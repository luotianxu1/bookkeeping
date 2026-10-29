package com.example.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 用户身份归一化过滤器（越权防线核心）。
 * <p>
 * JWT 过滤器解出可信 userId 后，本过滤器强制对请求做两件事：
 * <ol>
 *   <li>写请求（POST/PUT/DELETE/PATCH）：请求参数 userId/userIds 与 JSON body 中的 userId
 *       一律覆盖为 token 身份——家庭成员视角为只读，写操作永远只作用于本人；</li>
 *   <li>读请求（GET/HEAD）：请求参数 userId/userIds 若指向他人，必须属于本人家庭范围，
 *       否则直接拒绝（403）；未携带时注入本人 id，避免“无 userId 条件全表查询”。</li>
 * </ol>
 * 仅按路径变量定位、不含任何 userId 的接口，由各服务在 Service 层通过
 * SecurityUtils + FamilyScopeChecker 补充归属校验。
 */
public class UserIdentityScopeFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of(
        HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()
    );

    private final ObjectMapper objectMapper;
    private final FamilyScopeChecker scopeChecker;
    private final SecurityErrorWriter errorWriter;

    public UserIdentityScopeFilter(
        ObjectMapper objectMapper,
        FamilyScopeChecker scopeChecker,
        SecurityErrorWriter errorWriter
    ) {
        this.objectMapper = objectMapper;
        this.scopeChecker = scopeChecker;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long selfId = SecurityUtils.currentUserId();
        if (authentication == null || selfId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean read = READ_METHODS.contains(request.getMethod());
        Map<String, String[]> params = new LinkedHashMap<>(request.getParameterMap());
        boolean paramsChanged = false;

        // userId 参数
        if (read) {
            String[] raw = params.get("userId");
            if (raw == null || raw.length == 0) {
                params.put("userId", new String[]{String.valueOf(selfId)});
                paramsChanged = true;
            } else if (!allReadable(selfId, raw, response)) {
                return; // 已写出 403/400
            }
        } else {
            params.put("userId", new String[]{String.valueOf(selfId)});
            paramsChanged = true;
        }

        // userIds 参数（家庭联合视角）
        String[] rawUserIds = params.get("userIds");
        if (read) {
            if (rawUserIds != null && rawUserIds.length > 0 && !allReadable(selfId, rawUserIds, response)) {
                return;
            }
        } else {
            params.put("userIds", new String[]{String.valueOf(selfId)});
            paramsChanged = true;
        }

        HttpServletRequest effectiveRequest = request;

        // JSON body 中的 userId（仅写请求需要覆盖）
        if (!read && isJsonRequest(request)) {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length > 0) {
                byte[] rewritten = rewriteBodyUserId(body, selfId, response);
                if (rewritten == null) {
                    return; // body 非法，已写出 400
                }
                effectiveRequest = new OverridingRequestWrapper(request, params, rewritten);
                filterChain.doFilter(effectiveRequest, response);
                return;
            }
        }

        if (paramsChanged) {
            effectiveRequest = new OverridingRequestWrapper(request, params, null);
        }
        filterChain.doFilter(effectiveRequest, response);
    }

    private boolean allReadable(long selfId, String[] rawValues, HttpServletResponse response) throws IOException {
        for (String raw : rawValues) {
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                long target;
                try {
                    target = Long.parseLong(trimmed);
                } catch (NumberFormatException ignored) {
                    errorWriter.write(response, 400, "请求参数格式错误");
                    return false;
                }
                if (!scopeChecker.canRead(selfId, target)) {
                    errorWriter.write(response, 403, "无权查看该用户的数据");
                    return false;
                }
            }
        }
        return true;
    }

    /** 将 JSON body 顶层的 userId 覆盖为可信身份；body 非法时返回 null 并写出 400 */
    private byte[] rewriteBodyUserId(byte[] body, long selfId, HttpServletResponse response) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root instanceof ObjectNode objectNode && objectNode.has("userId")) {
                objectNode.put("userId", selfId);
                return objectMapper.writeValueAsBytes(objectNode);
            }
            return body;
        } catch (IOException ignored) {
            errorWriter.write(response, 400, "请求体格式错误");
            return null;
        }
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    /** 覆盖请求参数 / 重写请求体的包装器 */
    private static final class OverridingRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> params;
        private final byte[] body;

        private OverridingRequestWrapper(HttpServletRequest request, Map<String, String[]> params, byte[] body) {
            super(request);
            this.params = params;
            this.body = body;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return params;
        }

        @Override
        public String getParameter(String name) {
            String[] values = params.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public String[] getParameterValues(String name) {
            return params.get(name);
        }

        @Override
        public int getContentLength() {
            return body == null ? super.getContentLength() : body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body == null ? super.getContentLengthLong() : body.length;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            if (body == null) {
                return super.getInputStream();
            }
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            if (body == null) {
                return super.getReader();
            }
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private static final class CachedBodyServletInputStream extends jakarta.servlet.ServletInputStream {

        private final java.io.ByteArrayInputStream buffer;
        private final jakarta.servlet.ReadListener listener;

        private CachedBodyServletInputStream(byte[] body) {
            this.buffer = new java.io.ByteArrayInputStream(body);
            this.listener = null;
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }
}

package com.example.tool.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;

/**
 * 高德 Web Service 代理：Key 只保存在服务端，前端不再持有或直调 restapi.amap.com。
 * 仅转发白名单参数，防止被当成开放代理滥用。
 */
@RestController
@RequestMapping("/api/tool/amap")
@Tag(name = "高德地图代理", description = "服务端持有 Key 的路线规划与地点搜索代理")
public class AmapProxyController {

    private static final Set<String> DIRECTION_MODES = Set.of("driving", "walking", "bicycling");
    private static final Set<String> EXTENSIONS = Set.of("base", "all");
    private static final String COORDINATE_PATTERN = "^-?\\d{1,3}(\\.\\d+)?,-?\\d{1,3}(\\.\\d+)?$";

    @Value("${amap.key:}")
    private String amapKey;

    private final RestClient restClient;

    public AmapProxyController(@Value("${amap.key:}") String amapKey) {
        this.amapKey = amapKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.restClient = RestClient.builder().baseUrl("https://restapi.amap.com").requestFactory(factory).build();
    }

    @GetMapping("/direction")
    @Operation(summary = "路线规划代理", description = "mode=driving|walking|bicycling，origin/destination 为 lng,lat")
    public ResponseEntity<String> direction(
        @RequestParam("mode") String mode,
        @RequestParam("origin") String origin,
        @RequestParam("destination") String destination
    ) {
        if (isKeyMissing()) {
            return fail("地图服务未配置");
        }
        if (!DIRECTION_MODES.contains(mode)) {
            return fail("不支持的出行方式");
        }
        if (!origin.matches(COORDINATE_PATTERN) || !destination.matches(COORDINATE_PATTERN)) {
            return fail("坐标格式错误");
        }

        String path = "bicycling".equals(mode) ? "/v4/direction/bicycling" : "/v3/direction/" + mode;
        String body = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(path)
                .queryParam("key", amapKey)
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .build())
            .retrieve()
            .body(String.class);
        return json(body);
    }

    @GetMapping("/place/text")
    @Operation(summary = "地点搜索代理", description = "转发高德 v3/place/text，注入服务端 Key")
    public ResponseEntity<String> placeText(
        @RequestParam("keywords") String keywords,
        @RequestParam(value = "offset", defaultValue = "8") int offset,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "extensions", defaultValue = "base") String extensions
    ) {
        if (isKeyMissing()) {
            return fail("地图服务未配置");
        }
        String keyword = keywords == null ? "" : keywords.trim();
        if (keyword.isEmpty() || keyword.length() > 50) {
            return fail("关键词无效");
        }
        if (!EXTENSIONS.contains(extensions) || offset < 1 || offset > 25 || page < 1 || page > 10) {
            return fail("参数无效");
        }

        String body = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v3/place/text")
                .queryParam("key", amapKey)
                .queryParam("keywords", keyword)
                .queryParam("offset", offset)
                .queryParam("page", page)
                .queryParam("extensions", extensions)
                .build())
            .retrieve()
            .body(String.class);
        return json(body);
    }

    private boolean isKeyMissing() {
        return amapKey == null || amapKey.isBlank();
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private ResponseEntity<String> fail(String message) {
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"status\":\"0\",\"info\":\"" + message + "\"}");
    }
}

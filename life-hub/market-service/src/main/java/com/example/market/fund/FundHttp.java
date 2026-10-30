package com.example.market.fund;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 基金/报价数据源 HTTP 工具。
 * 新浪基金频道页为 GB18030（偶有非法字节，需宽松解码），hq.sinajs.cn 行情为 GBK，
 * 东财/howbuy 为 UTF-8。
 */
final class FundHttp {

    private FundHttp() {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final Map<String, String> SINA_HEADERS = Map.of(
        "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        "Referer", "https://finance.sina.com.cn"
    );

    static final Charset GB18030 = Charset.forName("GB18030");
    static final Charset GBK = Charset.forName("GBK");

    static String get(String url, Charset charset, int timeoutSeconds) {
        return get(url, charset, timeoutSeconds, SINA_HEADERS);
    }

    static String get(String url, Charset charset, int timeoutSeconds, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();
            headers.forEach(builder::header);
            HttpResponse<byte[]> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("上游返回 " + resp.statusCode() + ": " + url);
            }
            return decode(resp.body(), charset);
        } catch (IOException e) {
            throw new IllegalStateException("上游请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        }
    }

    /** 宽松解码：非法字节替换为 U+FFFD，不抛异常 */
    private static String decode(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            // REPLACE 模式下不会发生，兜底返回原字节的最宽松解释
            return new String(bytes, charset);
        }
    }

    /** 去掉 HTML 标签并整理空白，供静态页锚点解析 */
    static String stripTags(String html) {
        return html == null ? "" : html
            .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .trim();
    }
}

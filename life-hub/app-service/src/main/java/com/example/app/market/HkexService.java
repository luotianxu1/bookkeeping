package com.example.app.market;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 港交所全量证券列表（ListOfSecurities.xlsx）（对应 investment 项目 services/hkex.py）。
 *
 * <p>提供港股正股代码池：Category == "Equity"（排除窝轮/牛熊证/ETF 等），
 * 供市场概况统计港股全市场涨跌分布。列表变动缓慢，内存缓存 24 小时。
 * xlsx 即 zip 包，直接用内置 Zip + XML 解析，无需 POI 依赖。</p>
 */
public final class HkexService {

    private HkexService() {
    }

    private static final int MAX_RETRIES = 10;
    private static final MarketUtils.TtlCache<List<String>> CODES_CACHE = new MarketUtils.TtlCache<>(86_400_000L);

    /** 港股正股代码池（5 位字符串，如 '00700'），24 小时缓存 */
    public static List<String> equityCodes() {
        return CODES_CACHE.get(() -> {
            try {
                List<String> codes = parseEquityCodes(downloadXlsx());
                if (codes.size() < 1000) {
                    throw new IllegalStateException("港交所列表解析异常: 仅 " + codes.size() + " 只");
                }
                return codes;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("港交所列表解析失败: " + e.getMessage(), e);
            }
        });
    }

    /** 下载 xlsx；断点续传重试，返回字节流必须能通过 zip 完整性校验 */
    private static byte[] downloadXlsx() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        byte[] data = new byte[0];
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(MarketConfig.HKEX_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET();
                if (data.length > 0) {
                    builder.header("Range", "bytes=" + data.length + "-");
                }
                HttpResponse<InputStream> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() == 200 || resp.statusCode() == 206) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    buf.writeBytes(data);
                    resp.body().transferTo(buf);
                    data = buf.toByteArray();
                }
                if (isValidZip(data)) {
                    return data;
                }
            } catch (Exception ignored) {
                // 重试
            }
            sleepQuietly(1000);
        }
        throw new IllegalStateException("港交所证券列表下载失败");
    }

    private static boolean isValidZip(byte[] data) {
        if (data.length == 0) {
            return false;
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                zis.closeEntry();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析 xlsx，返回全部股本证券代码（保持文件顺序） */
    private static List<String> parseEquityCodes(byte[] xlsx) throws Exception {
        Map<Integer, String> sharedStrings = new HashMap<>();
        byte[] sheet1 = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("xl/sharedStrings.xml")) {
                    parseSharedStrings(zis.readAllBytes(), sharedStrings);
                } else if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    sheet1 = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        if (sheet1 == null) {
            return List.of();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(sheet1)).getDocumentElement();

        List<String> codes = new ArrayList<>();
        NodeList rows = root.getElementsByTagNameNS("*", "row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            NodeList cells = row.getElementsByTagNameNS("*", "c");
            String code = cells.getLength() > 0 ? cellValue((Element) cells.item(0), sharedStrings).trim() : "";
            String cat = cells.getLength() > 2 ? cellValue((Element) cells.item(2), sharedStrings).trim() : "";
            if (!code.isEmpty() && cat.equals("Equity")) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static void parseSharedStrings(byte[] xml, Map<Integer, String> out) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        NodeList siList = root.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < siList.getLength(); i++) {
            StringBuilder sb = new StringBuilder();
            NodeList ts = ((Element) siList.item(i)).getElementsByTagNameNS("*", "t");
            for (int j = 0; j < ts.getLength(); j++) {
                sb.append(ts.item(j).getTextContent());
            }
            out.put(i, sb.toString());
        }
    }

    private static String cellValue(Element cell, Map<Integer, String> sharedStrings) {
        NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) {
            return "";
        }
        String text = values.item(0).getTextContent();
        if ("s".equals(cell.getAttribute("t")) && !text.isEmpty()) {
            return sharedStrings.getOrDefault(Integer.parseInt(text.trim()), "");
        }
        return text;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

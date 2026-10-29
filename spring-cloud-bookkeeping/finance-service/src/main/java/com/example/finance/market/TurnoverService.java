package com.example.finance.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 市场实时成交额 + 较上一日同时点对比（对应 investment 项目 services/turnover.py）。
 *
 * <ul>
 *   <li>a / hk：腾讯 day/query 5 日分钟历史；A股 = 上证综指 + 深证综指，港股 = 恒生指数</li>
 *   <li>us：纳斯达克官方 screener Σ(成交量 × 最新价) 近似；
 *       昨日同时点由本服务按 5 分钟粒度采样留存（data/us-turnover.json，保留 7 个美东交易日）</li>
 * </ul>
 */
public final class TurnoverService {

    private TurnoverService() {
    }

    private static final long TTL = 60_000L;
    private static final Map<String, MarketUtils.TtlCache<Map<String, Object>>> CACHE = Map.of(
        "a", new MarketUtils.TtlCache<>(TTL),
        "hk", new MarketUtils.TtlCache<>(TTL),
        "us", new MarketUtils.TtlCache<>(TTL)
    );

    private static final String DAY_URL = "https://web.ifzq.gtimg.cn/appstock/app/day/query?code=";
    private static final List<String> A_CODES = List.of("sh000001", "sz399106");
    private static final String HK_CODE = "hkHSI";
    private static final int SNAPSHOT_KEEP_DAYS = 7;
    private static final ZoneId ET = ZoneId.of("America/New_York");

    /** 美股成交额采样留存文件（可用 us.turnover.snapshot-path 覆盖） */
    private static final Path SNAPSHOT_PATH = Path.of(
        System.getProperty("us.turnover.snapshot-path", "data/us-turnover.json"));

    /** 统一输出：today（今日实时累计成交额）、prev（上一交易日同时点，可能为 null）、currency */
    public static Map<String, Object> marketTurnover(String market) {
        MarketUtils.TtlCache<Map<String, Object>> cache = CACHE.get(market);
        if (cache == null) {
            throw new IllegalArgumentException("未知市场: " + market);
        }
        return cache.get(() -> switch (market) {
            case "a" -> tencentTurnover(A_CODES, "CNY");
            case "hk" -> tencentTurnover(List.of(HK_CODE), "HKD");
            case "us" -> usTurnover();
            default -> throw new IllegalArgumentException("未知市场: " + market);
        });
    }

    /** 取累计额：默认最后一根；cutoff（HHMM）时取同时点前的最后一根；按比例校验跳过脏行 */
    private static double lastCumAmount(List<String> rows, String cutoff) {
        double amt = 0;
        for (String r : rows) {
            String[] parts = r.trim().split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            if (cutoff != null && parts[0].compareTo(cutoff) > 0) {
                break;
            }
            double vol;
            double cand;
            try {
                vol = Double.parseDouble(parts[2]);
                cand = Double.parseDouble(parts[3]);
            } catch (NumberFormatException e) {
                continue;
            }
            // 正常行 成交额/累计量 ≈ 10000（A股≈1600+），脏行≈1
            if (vol > 0 && cand < vol * 100) {
                continue;
            }
            amt = cand;
        }
        return amt;
    }

    private static Map<String, Object> tencentTurnover(List<String> codes, String currency) {
        double todayTotal = 0;
        double prevTotal = 0;
        boolean gotToday = false;
        boolean gotPrev = false;
        String nowT = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (String code : codes) {
            JsonNode resp;
            try {
                resp = MarketUtils.MAPPER.readTree(TencentClient.httpGetJson(DAY_URL + code));
            } catch (Exception e) {
                continue;
            }
            JsonNode days = resp.path("data").path(code).path("data");
            if (!days.isArray() || days.size() < 2) {
                continue;
            }
            JsonNode first = days.get(0);
            if (todayStr.equals(first.path("date").asText())) {
                // 今日会话已开盘：今日至今 + 昨日同时点
                todayTotal += lastCumAmount(toStringList(first.path("data")), null);
                gotToday = true;
                prevTotal += lastCumAmount(toStringList(days.get(1).path("data")), nowT);
                gotPrev = true;
            }
            // 今日未开盘时 day/query 首日为上一交易日，不计入今日值
        }

        if (!gotToday) {
            throw new IllegalStateException("成交额数据不可用");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", round2(todayTotal));
        out.put("prev", gotPrev && prevTotal > 0 ? round2(prevTotal) : null);
        out.put("currency", currency);
        return out;
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    /** 全市场 Σ(成交量 × 最新价)，作为美股市场成交额近似 */
    private static double parseScreenerTotal() {
        JsonNode rows = ActivityService.fetchNasdaqScreener().path("data").path("rows");
        double total = 0;
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                long vol = MarketUtils.parseLongSafe(row.path("volume").asText("0"));
                Double px = MarketUtils.parseDouble(row.path("lastsale").asText("0").replace("$", ""));
                if (vol > 0 && px != null && px > 0) {
                    total += vol * px;
                }
            }
        }
        if (total <= 0) {
            throw new IllegalStateException("美股成交额数据不可用");
        }
        return total;
    }

    private static Map<String, Map<String, Double>> loadSnapshots() {
        try {
            JsonNode root = MarketUtils.MAPPER.readTree(Files.readString(SNAPSHOT_PATH));
            Map<String, Map<String, Double>> out = new HashMap<>();
            root.properties().forEach(entry -> {
                Map<String, Double> curve = new HashMap<>();
                entry.getValue().properties().forEach(c -> curve.put(c.getKey(), c.getValue().asDouble()));
                out.put(entry.getKey(), curve);
            });
            return out;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static void saveSnapshots(Map<String, Map<String, Double>> store) {
        List<String> keep = new ArrayList<>(new TreeMap<>(store).keySet());
        Map<String, Map<String, Double>> trimmed = new LinkedHashMap<>();
        for (String day : keep.subList(Math.max(0, keep.size() - SNAPSHOT_KEEP_DAYS), keep.size())) {
            trimmed.put(day, store.get(day));
        }
        try {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, MarketUtils.MAPPER.writeValueAsString(trimmed),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
            // 留存失败只影响次日对比，不阻塞今日值
        }
    }

    private static Map<String, Object> usTurnover() {
        double total = parseScreenerTotal();
        LocalDateTime etNow = LocalDateTime.now(ET);
        // 美东非交易时段（20:00-04:00）或周末：screener 停留在上一会话，无同时点可比
        if (etNow.getDayOfWeek().getValue() >= 6 || etNow.getHour() < 4 || etNow.getHour() >= 20) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("today", round2(total));
            out.put("prev", null);
            out.put("currency", "USD");
            return out;
        }

        String etDate = etNow.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String bucket = String.format("%04d", etNow.getHour() * 5 + etNow.getMinute() / 5);
        Map<String, Map<String, Double>> store = loadSnapshots();
        Map<String, Double> curve = store.get(etDate);
        if (curve == null || !curve.containsKey(bucket)) {
            store.computeIfAbsent(etDate, k -> new HashMap<>()).put(bucket, round2(total));
            saveSnapshots(store);
        }

        String prevDate = etNow.toLocalDate().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<String, Double> prevCurve = store.getOrDefault(prevDate, Map.of());
        Double prev = null;
        for (Map.Entry<String, Double> entry : new TreeMap<>(prevCurve).entrySet()) {
            if (entry.getKey().compareTo(bucket) <= 0) {
                prev = entry.getValue();
            } else {
                break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", round2(total));
        out.put("prev", prev);
        out.put("currency", "USD");
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

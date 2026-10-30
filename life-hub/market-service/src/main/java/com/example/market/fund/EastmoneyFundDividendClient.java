package com.example.market.fund;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东财基金分红历史（fundf10.eastmoney.com/fhsp_{code}.html）。
 * 这是基金分红数据目前唯一可用来源（蛋卷需登录、howbuy 为 AJAX、新浪无此数据），
 * 故保留东财源但收编进 market-service 统一出口。
 */
final class EastmoneyFundDividendClient {

    private EastmoneyFundDividendClient() {
    }

    private static final Pattern BATCH_DIVIDEND = Pattern.compile(
        "(?:每\\s*)?([0-9]+(?:\\.[0-9]+)?)\\s*份[^0-9]*([0-9]+(?:\\.[0-9]+)?)\\s*元");
    private static final Pattern SINGLE_UNIT_DIVIDEND = Pattern.compile(
        "每\\s*份[^0-9]*([0-9]+(?:\\.[0-9]+)?)\\s*元");
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");

    /**
     * 分红历史，item: dividendYear/recordDate/exDividendDate/dividendPerUnit/payDate。
     * matched=false 表示页面与请求的基金代码不符（东财偶发返回其他基金页面）。
     */
    static Map<String, Object> fetch(String code) {
        Map<String, Object> out = new LinkedHashMap<>();
        String url = "https://fundf10.eastmoney.com/fhsp_" + code + ".html";
        String body = FundHttp.get(url, java.nio.charset.StandardCharsets.UTF_8, 10,
            Map.of("User-Agent", "Mozilla/5.0"));
        Matcher title = Pattern.compile("(?is)<title>(.*?)</title>").matcher(body);
        boolean matched = title.find() && title.group(1).contains("(" + code + ")");
        out.put("matched", matched);
        out.put("items", matched ? parse(body) : List.of());
        return out;
    }

    private static List<Map<String, Object>> parse(String body) {
        List<Map<String, Object>> plans = new ArrayList<>();
        Matcher table = Pattern.compile("(?is)<table[^>]*class=['\"][^'\"]*comm\\s+cfxq[^'\"]*['\"][^>]*>(.*?)</table>")
            .matcher(body);
        if (!table.find()) {
            return plans;
        }
        String tableHtml = table.group(1);
        // 校验表头确为分红送配表，防止把拆分/公告等表格误解析成分红数据
        Matcher head = Pattern.compile("(?is)<thead>(.*?)</thead>").matcher(tableHtml);
        if (!head.find() || !head.group(1).contains("权益登记日") || !head.group(1).contains("分红发放日")) {
            return plans;
        }
        Matcher tbody = Pattern.compile("(?is)<tbody>(.*?)</tbody>").matcher(tableHtml);
        if (!tbody.find()) {
            return plans;
        }
        Matcher row = Pattern.compile("(?is)<tr>(.*?)</tr>").matcher(tbody.group(1));
        while (row.find()) {
            List<String> cells = tableCells(row.group(1));
            if (cells.size() < 5) {
                continue;
            }
            Integer year = parseYear(cells.get(0));
            String recordDate = date(cells.get(1));
            String exDividendDate = date(cells.get(2));
            String dividendPerUnit = perUnit(cells.get(3));
            String payDate = date(cells.get(4));
            if (year == null || dividendPerUnit == null || payDate == null) {
                continue;
            }
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("dividendYear", year);
            plan.put("recordDate", recordDate);
            plan.put("exDividendDate", exDividendDate);
            plan.put("dividendPerUnit", dividendPerUnit);
            plan.put("payDate", payDate);
            plans.add(plan);
        }
        return plans;
    }

    private static Integer parseYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{4})").matcher(value);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** 每份分红：支持 "每10份派X元"（批量）与 "每份X元"（单份），与 app-service 原口径一致 */
    private static String perUnit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace(",", "").replace("，", "").trim();
        Matcher batch = BATCH_DIVIDEND.matcher(normalized);
        if (batch.find()) {
            java.math.BigDecimal unitCount = dec(batch.group(1));
            java.math.BigDecimal amount = dec(batch.group(2));
            if (unitCount == null || unitCount.signum() <= 0 || amount == null || amount.signum() <= 0) {
                return null;
            }
            return amount.divide(unitCount, 12, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        Matcher single = SINGLE_UNIT_DIVIDEND.matcher(normalized);
        if (single.find()) {
            java.math.BigDecimal amount = dec(single.group(1));
            return amount != null && amount.signum() > 0 ? amount.toPlainString() : null;
        }
        if (normalized.contains("份")) {
            return null;
        }
        Matcher number = DECIMAL.matcher(normalized);
        if (!number.find()) {
            return null;
        }
        java.math.BigDecimal amount = dec(number.group(1));
        return amount != null && amount.signum() > 0 ? amount.toPlainString() : null;
    }

    private static List<String> tableCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cell = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>").matcher(rowHtml);
        while (cell.find()) {
            cells.add(FundHttp.stripTags(cell.group(1)));
        }
        return cells;
    }

    private static String date(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})").matcher(value.trim());
        if (!m.find()) {
            return null;
        }
        return String.format("%s-%02d-%02d", m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private static java.math.BigDecimal dec(String value) {
        try {
            return value == null ? null : new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

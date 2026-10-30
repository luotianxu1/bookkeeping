package com.example.market.fund;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪基金净值历史（官方开放接口 CaihuiFundInfoService.getNav）：
 * {fbrq: 净值日期, jjjz: 单位净值, ljjz: 累计净值}，按日期倒序返回。
 * num=1000 单页千行，4 年窗口 1-2 页即可覆盖。
 */
final class SinaNavHistoryClient {

    private SinaNavHistoryClient() {
    }

    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** 拉取 datefrom 以来的净值历史（倒序），最多 maxPages 页；items: date/nav/accNav */
    static List<Map<String, Object>> fetch(String code, LocalDate dateFrom, int maxPages) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            String url = "https://stock.finance.sina.com.cn/fundInfo/api/openapi.php/CaihuiFundInfoService.getNav"
                + "?symbol=" + code
                + "&datefrom=" + dateFrom + "&dateto=" + LocalDate.now()
                + "&page=" + page + "&num=1000";
            String body = FundHttp.get(url, java.nio.charset.StandardCharsets.UTF_8, 12);
            List<Map<String, Object>> rows = parse(body);
            items.addAll(rows);
            if (rows.size() < 1000) {
                break;
            }
        }
        return items;
    }

    private static List<Map<String, Object>> parse(String body) {
        List<Map<String, Object>> items = new ArrayList<>();
        Matcher row = Pattern.compile("\\{[^{}]*?\"fbrq\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"jjjz\"\\s*:\\s*\"?([\\d.]+)\"?[^{}]*?\"ljjz\"\\s*:\\s*\"?([\\d.]+)\"?[^{}]*?\\}")
            .matcher(body);
        while (row.find()) {
            Matcher d = DATE.matcher(row.group(1));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.find() ? d.group(1) : row.group(1));
            item.put("nav", row.group(2));
            item.put("accNav", row.group(3));
            items.add(item);
        }
        return items;
    }
}

package com.example.market.fund;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪财经基金数据源：
 * - hq.sinajs.cn/list=fu_{code} 盘中估值（GBK）
 * - stock.finance.sina.com.cn/fundInfo/view/FundInfo_JJGK.php 基金概况（GB18030）
 * - stock.finance.sina.com.cn/fundInfo/view/FundInfo_JJFL.php 费率结构（GB18030）
 * - vip.stock.finance.sina.com.cn/corp/go.php/vISSUE_ShareBonus 股票分红（GB18030）
 */
final class SinaFundClient {

    private static final String PAGE_BASE = "https://stock.finance.sina.com.cn/fundInfo/view";

    private SinaFundClient() {
    }

    /** 估值快照：[0]名称 [1]时间 [2]盘中估值 [3]昨日净值 [4]累计净值 [5]涨跌额 [6]涨跌幅% [7]估值日期 [8][9]LOF场内价 */
    static Map<String, Object> fuQuote(String code) {
        String text = FundHttp.get("https://hq.sinajs.cn/list=fu_" + code, FundHttp.GBK, 8);
        Matcher m = Pattern.compile("hq_str_fu_" + Pattern.quote(code) + "=\"([^\"]*)\"").matcher(text);
        if (!m.find()) {
            return null;
        }
        String[] f = m.group(1).split(",");
        if (f.length < 8 || f[0].isBlank()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", f[0].trim());
        out.put("time", f[1].trim());
        out.put("gsz", num(f.length > 2 ? f[2] : null));
        out.put("dwjz", num(f.length > 3 ? f[3] : null));
        out.put("ljjz", num(f.length > 4 ? f[4] : null));
        out.put("change", num(f.length > 5 ? f[5] : null));
        out.put("changePercent", num(f.length > 6 ? f[6] : null));
        out.put("date", f.length > 7 ? f[7].trim() : null);
        out.put("marketPrice", num(f.length > 8 ? f[8] : null));
        out.put("marketChangePercent", num(f.length > 9 ? f[9] : null));
        return out;
    }

    /** 基金概况（名称/类型/公司/申赎状态/成立日期），解析失败返回 null */
    static Map<String, Object> fundProfile(String code) {
        String html = FundHttp.get(PAGE_BASE + "/FundInfo_JJGK.php?symbol=" + code, FundHttp.GB18030, 10);
        if (!html.contains("(" + code + ")") && !html.contains(code)) {
            return null;
        }
        String text = FundHttp.stripTags(html);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fullName", anchor(text, "基金全称", "基金简称"));
        out.put("shortName", anchor(text, "基金简称", "基金类型|基金代码"));
        out.put("fundType", anchor(text, "基金类型", "基金公司|基金代码|发行日期"));
        out.put("company", firstCompany(text));
        out.put("purchaseStatus", mapStatus(anchorAfter(text, "申购状态")));
        out.put("redeemStatus", mapStatus(anchorAfter(text, "赎回状态")));
        out.put("foundDate", anchor(text, "成立日期", null));
        if (out.get("shortName") == null && out.get("purchaseStatus") == null) {
            return null;
        }
        return out;
    }

    /** 费率结构：管理费/托管费 + 赎回费分档（label 归一为东财措辞，供 parseFundRedeemFeeRule 复用） */
    static Map<String, Object> fundFees(String code) {
        String html = FundHttp.get(PAGE_BASE + "/FundInfo_JJFL.php?symbol=" + code, FundHttp.GB18030, 10);
        String text = FundHttp.stripTags(html);
        if (!text.contains(code)) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        // 运作费三个标签后跟三个值（管理费/托管费/销售服务费），值可能是百分比或 --
        Matcher op = Pattern.compile("基金运作费.*?基金管理费\\s*([\\d.]+%|--)\\s*基金托管费\\s*([\\d.]+%|--)\\s*销售服务费\\s*([\\d.]+%|--)").matcher(text);
        if (op.find()) {
            out.put("managementFeeRate", trimPct(op.group(1)));
            out.put("custodyFeeRate", trimPct(op.group(2)));
            out.put("serviceFeeRate", trimPct(op.group(3)));
        }
        // 赎回费分档：0<=X<7天 1.50% 7<=X<365天 0.50% 2<=X年 0.00% X>=730天 等形态
        Pattern pair = Pattern.compile(
            "((?:[\\d.]+\\s*<=\\s*X\\s*<=?[\\d.]+|[\\d.]+\\s*<=\\s*X|X\\s*>=\\s*[\\d.]+)\\s*[天年]?)\\s+([\\d.]+)%");
        java.util.List<Map<String, String>> rules = new java.util.ArrayList<>();
        int redeemStart = text.lastIndexOf("赎回费");
        String scope = redeemStart >= 0 ? text.substring(redeemStart) : text;
        Matcher sm = pair.matcher(scope);
        while (sm.find()) {
            String label = normalizeRedeemLabel(sm.group(1));
            if (label != null) {
                Map<String, String> rule = new LinkedHashMap<>();
                rule.put("label", label);
                rule.put("feeRateText", sm.group(2) + "%");
                rules.add(rule);
            }
        }
        out.put("redeemFeeRules", rules);
        return out;
    }

    /** 股票分红历史（每10股口径），新浪分红送配页 */
    static java.util.List<Map<String, Object>> stockDividends(String code) {
        String url = "https://vip.stock.finance.sina.com.cn/corp/go.php/vISSUE_ShareBonus/stockid/" + code + ".phtml";
        String html = FundHttp.get(url, FundHttp.GB18030, 10);
        if (!html.contains(code)) {
            return java.util.List.of();
        }
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        Matcher tr = Pattern.compile("(?is)<tr>(.*?)</tr>").matcher(html);
        while (tr.find() && rows.size() < 60) {
            java.util.List<String> cells = tableCells(tr.group(1));
            if (cells.size() < 8 || !cells.get(0).matches("\\d{4}-\\d{2}-\\d{2}")) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("announcementDate", date(cells.get(0)));
            row.put("bonusPer10", num(cells.get(1)));
            row.put("conversionPer10", num(cells.get(2)));
            row.put("pretaxPer10", num(cells.get(3)));
            row.put("progress", cells.get(4).trim());
            row.put("exDividendDate", date(cells.get(5)));
            row.put("recordDate", date(cells.get(6)));
            row.put("listingDate", date(cells.size() > 7 ? cells.get(7) : null));
            if (row.get("pretaxPer10") != null || row.get("bonusPer10") != null || row.get("conversionPer10") != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    // ---------- 内部工具 ----------

    /** 取 label 之后、next 锚点（正则可选）之前的文本 */
    private static String anchor(String text, String label, String nextPattern) {
        int start = text.indexOf(label);
        if (start < 0) {
            return null;
        }
        int from = start + label.length();
        int end = text.length();
        if (nextPattern != null) {
            Matcher m = Pattern.compile(nextPattern).matcher(text);
            if (m.find(from)) {
                end = m.start();
            }
        }
        String seg = text.substring(from, end).trim();
        return seg.isEmpty() ? null : seg.split("\\s+")[0];
    }

    /** "申购状态: 可申购" → 可申购 */
    private static String anchorAfter(String text, String label) {
        Matcher m = Pattern.compile(Pattern.quote(label) + "[:：]?\\s*(\\S+)").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String firstCompany(String text) {
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(?:基金管理|资产管理)有限公司)").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 新浪措辞（可申购/暂停申购…）→ 东财措辞（开放申购/暂停申购…），关键词校验在 app-service */
    private static String mapStatus(String raw) {
        if (raw == null || raw.isBlank() || "--".equals(raw)) {
            return null;
        }
        if (raw.contains("开放申购") || raw.contains("可申购")) {
            return "开放申购";
        }
        if (raw.contains("限大额") || raw.contains("限制大额")) {
            return "限制大额申购";
        }
        if (raw.contains("暂停大额")) {
            return "暂停大额申购";
        }
        if (raw.contains("暂停申购")) {
            return "暂停申购";
        }
        if (raw.contains("停止申购") || raw.contains("关闭申购")) {
            return "停止申购";
        }
        if (raw.contains("开放赎回") || raw.contains("可赎回")) {
            return "开放赎回";
        }
        if (raw.contains("暂停赎回")) {
            return "暂停赎回";
        }
        if (raw.contains("停止赎回") || raw.contains("关闭赎回")) {
            return "停止赎回";
        }
        if (raw.contains("封闭")) {
            return "封闭期";
        }
        return raw;
    }

    /**
     * 新浪分档措辞 → app-service parseFundRedeemFeeRule 认识的措辞：
     * 0<=X<7天 → 小于7天；7<=X<365天 → 大于等于7天,小于365天；X>=730天 / 2<=X年 → 大于等于730天（年换算为天）
     */
    private static String normalizeRedeemLabel(String raw) {
        String s = raw.replaceAll("\\s+", "");
        boolean year = s.endsWith("年");
        long unit = year ? 365L : 1L;
        if (s.endsWith("年") || s.endsWith("天")) {
            s = s.substring(0, s.length() - 1);
        }
        // 形态：[min<=]X[<max] 或 X>=max
        Matcher range = Pattern.compile("^(?:(\\d+(?:\\.\\d+)?)<=)?X(?:<(=)?(\\d+(?:\\.\\d+)?))?$").matcher(s);
        Matcher ge = Pattern.compile("^X>=(\\d+(?:\\.\\d+)?)$").matcher(s);
        if (range.matches()) {
            String lower = range.group(1);
            String upper = range.group(3);
            boolean upperInclusive = range.group(2) != null;
            Long min = lower != null ? Math.round(Double.parseDouble(lower) * unit) : null;
            Long max = upper != null ? Math.round(Double.parseDouble(upper) * unit) : null;
            if (min == null && max != null) {
                return "小于" + max + "天";
            }
            if (min != null && max == null) {
                return "大于等于" + min + "天";
            }
            if (min != null && max != null) {
                if (min == 0) {
                    return "小于" + max + "天";
                }
                // 上界为闭区间时（X<=max）多含 1 天，误差可忽略，保持开区间措辞
                return "大于等于" + min + "天,小于" + max + "天";
            }
            return null;
        }
        if (ge.matches()) {
            long min = Math.round(Double.parseDouble(ge.group(1)) * unit);
            return "大于等于" + min + "天";
        }
        return null;
    }

    private static java.util.List<String> tableCells(String rowHtml) {
        java.util.List<String> cells = new java.util.ArrayList<>();
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

    private static String num(String value) {
        if (value == null) {
            return null;
        }
        String s = value.replace(",", "").replace("--", "").trim();
        return s.matches("-?\\d+(?:\\.\\d+)?") ? s : null;
    }

    private static String trimPct(String value) {
        return value == null ? null : "--".equals(value) ? null : value.trim();
    }
}

package com.example.app.market;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 股票搜索（新浪 suggest 联想接口，GBK 编码）。（对应 investment 项目 services/search.py）
 *
 * <p>type 码：11 = A股/指数，2 = 港股/美股。行内字段顺序随 type 不同，
 * 统一用正则提取代码、取首个含中文的字段作名称。</p>
 */
public final class SearchService {

    private SearchService() {
    }

    private static final String SUGGEST_URL =
        "https://suggest3.sinajs.cn/suggest/type=2,11,12,13&key={kw}";
    private static final Pattern SYMBOL_RE =
        Pattern.compile("(?:sh|sz|bj|hk)\\d+|(?:us)[0-9a-z]+");
    private static final Pattern CJK_RE = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern SUGGEST_VALUE_RE = Pattern.compile("var suggestvalue=\"(.*)\"");
    private static final Charset GBK = Charset.forName("GBK");

    /** sh/sz/bj 代码 → index / stock */
    private static String aKind(String symbol) {
        String market = symbol.substring(0, 2);
        String code = symbol.substring(2);
        if ((market.equals("sh") && code.startsWith("000"))
            || (market.equals("sz") && code.startsWith("399"))) {
            return "index";
        }
        return "stock";
    }

    /** 过滤债券/回购/分时等非股票噪声：只留股票、指数、ETF、北交所 */
    private static boolean aStockOk(String code) {
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            return false;
        }
        char c0 = code.charAt(0);
        if (c0 == '6' || c0 == '5') { // 沪市股票/ETF
            return true;
        }
        if (c0 == '0' || c0 == '3') { // 深市股票/指数
            return true;
        }
        if (code.startsWith("15") || code.startsWith("16") || code.startsWith("18")) { // 深市 ETF/LOF
            return true;
        }
        return c0 == '8' || c0 == '4' || c0 == '9'; // 北交所
    }

    /** 关键词 → [{代码, 名称, 类型, 市场}]，类型与详情页 kind 一致 */
    public static List<Map<String, Object>> search(String keyword, int limit) {
        String url = SUGGEST_URL.replace("{kw}",
            URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        String text = SinaClient.getText(url, GBK, 10);
        Matcher m = SUGGEST_VALUE_RE.matcher(text);
        if (!m.find() || m.group(1).isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> indexCodes = new HashSet<>();
        for (String row : m.group(1).split(";")) {
            Matcher symM = SYMBOL_RE.matcher(row);
            if (!symM.find()) {
                continue;
            }
            String symbol = symM.group(0);
            String prefix = symbol.substring(0, 2);
            String code = symbol.substring(2);
            String kind;
            String market;
            if (prefix.equals("sh") || prefix.equals("sz") || prefix.equals("bj")) {
                if (!aStockOk(code)) {
                    continue;
                }
                kind = aKind(symbol);
                market = kind.equals("index") ? "A股指数" : "A股";
            } else if (prefix.equals("hk")) {
                kind = "hk";
                market = "港股";
            } else if (prefix.equals("us")) {
                kind = "us";
                market = "美股";
            } else {
                continue;
            }
            if (seen.contains(kind + code)) {
                continue;
            }
            // 同代码指数优先，避免"上证指数"重复出现在股票分类
            if (kind.equals("index")) {
                indexCodes.add(code);
            } else if (indexCodes.contains(code)) {
                continue;
            }
            // 名称：取首个含中文的字段
            String name = code;
            for (String field : row.split(",")) {
                if (CJK_RE.matcher(field).find()) {
                    name = field;
                    break;
                }
            }
            seen.add(kind + code);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("代码", code);
            item.put("名称", name);
            item.put("类型", kind);
            item.put("市场", market);
            out.add(item);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }
}

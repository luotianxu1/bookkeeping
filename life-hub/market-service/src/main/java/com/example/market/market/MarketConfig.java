package com.example.market.market;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 全球行情数据源常量（对应 investment 项目 backend/app/config.py）。
 *
 * <p>数据源：新浪财经（主）、腾讯财经（海外 K 线/快照）、纳斯达克官方（美股全市场）、
 * 港交所（港股代码池）、沪深交易所（融资融券）。</p>
 */
public final class MarketConfig {

    private MarketConfig() {
    }

    /** 新浪行情接口公共请求头（缺 Referer 会被拒绝） */
    public static final Map<String, String> SINA_HEADERS = Map.of(
        "Referer", "https://finance.sina.com.cn",
        "User-Agent", "Mozilla/5.0"
    );

    /** 腾讯行情接口公共请求头（海外市场 K 线专用主源） */
    public static final Map<String, String> TENCENT_HEADERS = Map.of(
        "Referer", "https://gu.qq.com",
        "User-Agent", "Mozilla/5.0"
    );

    /** 纳斯达克官方 screener 请求头 */
    public static final Map<String, String> NASDAQ_HEADERS = Map.of(
        "User-Agent", "Mozilla/5.0",
        "Accept", "application/json"
    );

    /** 港交所全量证券列表 xlsx（港股正股代码池，市场概况用） */
    public static final String HKEX_URL =
        "https://www.hkex.com.hk/eng/services/trading/securities/"
            + "securitieslists/ListOfSecurities.xlsx";

    /** 纳斯达克官方全市场 screener（含 NYSE/NASDAQ 全部上市股票，市场概况用） */
    public static final String NASDAQ_SCREENER_URL =
        "https://api.nasdaq.com/api/screener/stocks?tableonly=true&limit=99999&download=true";

    /** 全球指数：新浪代码(dji 等) → 腾讯 K 线代码（伦敦富时/日经腾讯无 K 线数据） */
    public static final Map<String, String> GLOBAL_TENCENT_CODES = Map.of(
        "dji", "usDJI",
        "nasdaq", "usIXIC",
        "sp500", "usINX",
        "hangseng", "hkHSI"
    );

    /** A 股核心指数（新浪代码 → 默认名称），顺序即前端行情条展示顺序 */
    public static final List<Map.Entry<String, String>> SINA_INDEX_CODES = List.of(
        Map.entry("sh000001", "上证指数"),
        Map.entry("sz399006", "创业板指"),
        Map.entry("bj899050", "北证50"),
        Map.entry("sh000688", "科创50"),
        Map.entry("sh000300", "沪深300"),
        Map.entry("sh000905", "中证500")
    );

    /** 各市场页的新浪行情代码 → 默认名称（全球/港股/美股/ETF/黄金） */
    public static final Map<String, List<Map.Entry<String, String>>> MARKET_GROUPS = buildMarketGroups();

    /** 全球页六大模块（A股指数/全球指数/股指期货/汇率指数/债券期货/全球商品） */
    public static final List<GlobalModuleDef> GLOBAL_MODULES = buildGlobalModules();

    /** 全球页模块定义 */
    public record GlobalModuleDef(String key, String title, boolean linkable,
                                  List<Map.Entry<String, String>> items) {
    }

    private static Map<String, List<Map.Entry<String, String>>> buildMarketGroups() {
        Map<String, List<Map.Entry<String, String>>> groups = new LinkedHashMap<>();
        groups.put("global", List.of(
            Map.entry("int_dji", "道琼斯"),
            Map.entry("int_nasdaq", "纳斯达克"),
            Map.entry("int_sp500", "标普500"),
            Map.entry("int_ftse", "伦敦富时100"),
            Map.entry("int_nikkei", "日经225"),
            Map.entry("int_hangseng", "恒生指数")
        ));
        groups.put("hk", List.of(
            Map.entry("rt_hkHSI", "恒生指数"),
            Map.entry("rt_hkHSCEI", "恒生国企指数"),
            Map.entry("rt_hkHSTECH", "恒生科技指数"),
            Map.entry("rt_hk00700", "腾讯控股"),
            Map.entry("rt_hk09988", "阿里巴巴-W"),
            Map.entry("rt_hk03690", "美团-W"),
            Map.entry("rt_hk01810", "小米集团-W"),
            Map.entry("rt_hk01211", "比亚迪股份"),
            Map.entry("rt_hk00941", "中国移动")
        ));
        groups.put("us", List.of(
            Map.entry("int_dji", "道琼斯"),
            Map.entry("int_nasdaq", "纳斯达克"),
            Map.entry("int_sp500", "标普500"),
            Map.entry("gb_aapl", "苹果"),
            Map.entry("gb_msft", "微软"),
            Map.entry("gb_nvda", "英伟达"),
            Map.entry("gb_tsla", "特斯拉"),
            Map.entry("gb_googl", "谷歌A"),
            Map.entry("gb_amzn", "亚马逊"),
            Map.entry("gb_meta", "Meta")
        ));
        groups.put("etf", List.of(
            Map.entry("sh510050", "上证50ETF"),
            Map.entry("sh510300", "沪深300ETF"),
            Map.entry("sz159915", "创业板ETF"),
            Map.entry("sh512500", "中证500ETF"),
            Map.entry("sh588000", "科创50ETF"),
            Map.entry("sh512100", "中证1000ETF"),
            Map.entry("sh513100", "纳指ETF"),
            Map.entry("sh513050", "中概互联ETF"),
            Map.entry("sh512880", "证券ETF"),
            Map.entry("sh512480", "半导体ETF"),
            Map.entry("sh515790", "光伏ETF"),
            Map.entry("sh516160", "新能源ETF")
        ));
        groups.put("gold", List.of(
            Map.entry("hf_XAU", "伦敦金现"),
            Map.entry("hf_GC", "纽约黄金"),
            Map.entry("nf_AU0", "沪金连续"),
            Map.entry("sh518880", "黄金ETF")
        ));
        return groups;
    }

    private static List<GlobalModuleDef> buildGlobalModules() {
        return List.of(
            new GlobalModuleDef("cn_index", "A股指数", true, List.of(
                Map.entry("sh000001", "上证指数"),
                Map.entry("sz399001", "深证指数"),
                Map.entry("sh000680", "科创综指"),
                Map.entry("sz399006", "创业板指"),
                Map.entry("sh000300", "沪深300"),
                Map.entry("bj899050", "北证50")
            )),
            new GlobalModuleDef("world_index", "全球指数", true, List.of(
                Map.entry("rt_hkHSI", "恒生指数"),
                Map.entry("rt_hkHSCEI", "国企指数"),
                Map.entry("znb_NKY", "日经指数"),
                Map.entry("znb_UKX", "英国富时"),
                Map.entry("znb_DAX", "德国DAX"),
                Map.entry("znb_CAC", "法国CAC"),
                Map.entry("int_dji", "道琼斯"),
                Map.entry("int_nasdaq", "纳斯达克"),
                Map.entry("int_sp500", "标普500")
            )),
            new GlobalModuleDef("index_future", "股指期货", true, List.of(
                Map.entry("hf_CHA50CFD", "富时A50期指连续"),
                Map.entry("hf_HSI", "恒生期货"),
                Map.entry("hf_YM", "迷你道琼斯连续"),
                Map.entry("hf_MCA", "MSCI中国A50互通主联"),
                Map.entry("nf_IH0", "上证50主连"),
                Map.entry("nf_IF0", "沪深300主连"),
                Map.entry("nf_IC0", "中证500主连")
            )),
            new GlobalModuleDef("fx", "汇率指数", true, List.of(
                Map.entry("DINI", "美元指数"),
                Map.entry("fx_seurusd", "欧元美元"),
                Map.entry("fx_susdcnh", "离岸人民币")
            )),
            new GlobalModuleDef("bond_future", "债券期货", true, List.of(
                Map.entry("nf_T0", "十年国债主连")
            )),
            new GlobalModuleDef("commodity", "全球商品", true, List.of(
                Map.entry("hf_XAU", "伦敦金现"),
                Map.entry("hf_XAG", "伦敦银现"),
                Map.entry("hf_CL", "轻质原油连续"),
                Map.entry("hf_GC", "纽约金主连"),
                Map.entry("hf_SI", "纽约银主连"),
                Map.entry("hf_QM", "美原油主连"),
                Map.entry("nf_AU0", "沪金主连"),
                Map.entry("nf_AG0", "沪银主连"),
                Map.entry("hf_OIL", "布伦特原油连续"),
                Map.entry("nf_CU0", "沪铜主连"),
                Map.entry("hf_CAD", "伦铜主连"),
                Map.entry("nf_SC0", "原油主连")
            ))
        );
    }
}

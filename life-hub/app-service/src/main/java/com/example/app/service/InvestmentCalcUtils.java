package com.example.app.service;

import com.example.app.entity.InvestmentPositionEntity;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 投资模块纯计算/格式化工具（自 InvestmentService 抽出，无实例状态）。
 * 各服务类通过 static import 直接使用原方法名。
 */
final class InvestmentCalcUtils {

    private InvestmentCalcUtils() {
    }

    public static Integer parseYearValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }


    public static YearMonth parseYearMonthValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }


    public static LocalDate parseDateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }


    public static int clampYear(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }


    public static YearMonth clampYearMonth(YearMonth value, YearMonth min, YearMonth max, YearMonth fallback) {
        YearMonth resolved = value == null ? fallback : value;
        if (resolved.isBefore(min)) {
            return min;
        }
        if (resolved.isAfter(max)) {
            return max;
        }
        return resolved;
    }


    public static LocalDate minDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }


    public static boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value)
            && StringUtils.hasText(keyword)
            && value.toUpperCase(Locale.ROOT).contains(keyword.toUpperCase(Locale.ROOT));
    }


    public static BigDecimal decimalText(String first, String second) {
        String value = StringUtils.hasText(first) && !"--".equals(first) ? first : second;
        if (!StringUtils.hasText(value) || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }


    public static BigDecimal safeDecimal(String value) {
        if (!StringUtils.hasText(value) || "--".equals(value) || "-".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }


    public static LocalDate safeDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim();
            if (normalized.length() >= 10) {
                normalized = normalized.substring(0, 10);
            }
            return LocalDate.parse(normalized);
        } catch (Exception ex) {
            return null;
        }
    }


    public static String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }


    public static String blankToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }


    public static String moneyText(BigDecimal value, int scale) {
        if (value == null) {
            return "-";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }


    public static String currencyText(BigDecimal value, int scale) {
        String text = moneyText(value == null ? null : value.abs(), scale);
        if ("-".equals(text)) {
            return "-";
        }
        return (value.compareTo(BigDecimal.ZERO) < 0 ? "-¥" : "¥") + text;
    }


    public static String priceText(BigDecimal value, String productType) {
        String text = moneyText(value, "stock".equals(productType) ? 2 : 4);
        if ("-".equals(text)) {
            return "-";
        }
        return "¥" + text;
    }


    public static String percentText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }


    public static String tone(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "neutral";
        }
        return value.compareTo(BigDecimal.ZERO) > 0 ? "positive" : "negative";
    }


    public static String productTypeName(String productType) {
        if ("stock".equals(productType)) return "股票";
        if ("fund".equals(productType)) return "基金";
        if ("bond".equals(productType)) return "债券";
        if ("gold".equals(productType)) return "黄金";
        return "其他";
    }


    public static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }


    public static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }


    public static BigDecimal scaleMoney(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }


    public static BigDecimal scaleRate(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }


    public static BigDecimal scaleQuantity(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }


    public static BigDecimal rate(BigDecimal profit, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }


    /** 已实现收益（卖出落袋收益+分红），不含浮动盈亏；空值按 0 处理。 */
    public static BigDecimal realizedProfitOf(InvestmentPositionEntity position) {
        return defaultZero(position.getRealizedProfit()).setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * 累计收益率分母：优先累计投入成本（买入累加、卖出不扣减，卖出越多不失真）。
     * 取 max(invested, cost)：新流程 invested ≥ cost 恒成立；未回填的历史数据（invested=0）
     * 或历史数据后又发生买入时，以当前持仓成本兜底，避免分母低估。
     */
    public static BigDecimal investedBaseOf(InvestmentPositionEntity position) {
        BigDecimal invested = defaultZero(position.getInvestedAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        return invested.max(cost);
    }


    /** 累计收益 = 已实现收益 + 持仓浮盈；展示口径与主流记账 App 一致。 */
    public static BigDecimal cumulativeProfitOf(InvestmentPositionEntity position) {
        return realizedProfitOf(position).add(defaultZero(position.getHoldingProfit())).setScale(2, RoundingMode.HALF_UP);
    }


    public static BigDecimal sum(List<InvestmentPositionEntity> positions, Function<InvestmentPositionEntity, BigDecimal> getter) {
        return positions.stream().map(getter).filter(item -> item != null).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }


    public static BigDecimal scaleFundQuantity(BigDecimal quantity) {
        return defaultZero(quantity).setScale(2, RoundingMode.HALF_UP);
    }


    public static LocalDate resolveStockQuoteDate(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() < 8) {
            return null;
        }
        String normalized = raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
        return safeDate(normalized);
    }


    public static BigDecimal scaleStockQuoteValue(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

}

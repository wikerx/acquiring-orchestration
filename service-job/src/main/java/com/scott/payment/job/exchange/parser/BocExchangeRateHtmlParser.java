package com.scott.payment.job.exchange.parser;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateHtmlParser
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Boc Exchange Rate Html Parser，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class BocExchangeRateHtmlParser {

    /**
     * 汇率管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String QUOTE_CURRENCY = "CNY";
    private static final List<DateTimeFormatter> BOC_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    private static final Map<String, String> CURRENCY_NAME_MAP = buildCurrencyNameMap();

    /**
     * 解析中国银行外汇牌价 HTML。
     *
     * @param html 页面 HTML
     * @return 原始汇率项目列表
     */
    /**
     * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
     * @param html 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<RawRateItem> parse(String html) {
        if (!StringUtils.hasText(html)) {
            return List.of();
        }
        List<RawRateItem> result = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = parseCells(rowMatcher.group(1));
            if (cells.size() < 7 || isHeaderRow(cells)) {
                continue;
            }
            RawRateItem item = toItem(cells);
            if (StringUtils.hasText(item.getSourceCurrencyName())) {
                result.add(item);
            }
        }
        return result;
    }

    private List<String> parseCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cleanText(cellMatcher.group(1)));
        }
        return cells;
    }

    private RawRateItem toItem(List<String> cells) {
        RawRateItem item = new RawRateItem();
        item.setSourceCurrencyName(cells.get(0));
        item.setBaseCurrency(CURRENCY_NAME_MAP.get(cells.get(0)));
        item.setQuoteCurrency(QUOTE_CURRENCY);
        item.setSpotBuyRate(parseBocRate(cells.get(1)));
        item.setCashBuyRate(parseBocRate(cells.get(2)));
        item.setSpotSellRate(parseBocRate(cells.get(3)));
        item.setCashSellRate(parseBocRate(cells.get(4)));
        item.setMiddleRate(parseBocRate(cells.get(5)));
        item.setPublishTime(parsePublishTime(cells.get(6)));
        return item;
    }

    private boolean isHeaderRow(List<String> cells) {
        return cells.stream().anyMatch(cell -> cell.contains("货币") || cell.contains("发布时间"));
    }

    private BigDecimal parseBocRate(String value) {
        if (!StringUtils.hasText(value) || "-".equals(value.trim())) {
            return null;
        }
        String normalized = value.replace(",", "").trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return new BigDecimal(normalized).divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP);
    }

    private LocalDateTime parsePublishTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        DateTimeParseException lastException = null;
        for (DateTimeFormatter formatter : BOC_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ex) {
                lastException = ex;
            }
        }
        throw lastException;
    }

    private String cleanText(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim();
    }

    private static Map<String, String> buildCurrencyNameMap() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("美元", "USD");
        mapping.put("欧元", "EUR");
        mapping.put("英镑", "GBP");
        mapping.put("港币", "HKD");
        mapping.put("日元", "JPY");
        mapping.put("澳门元", "MOP");
        mapping.put("新加坡元", "SGD");
        mapping.put("加拿大元", "CAD");
        mapping.put("澳大利亚元", "AUD");
        mapping.put("新西兰元", "NZD");
        mapping.put("瑞士法郎", "CHF");
        mapping.put("瑞典克朗", "SEK");
        mapping.put("丹麦克朗", "DKK");
        mapping.put("挪威克朗", "NOK");
        mapping.put("泰国铢", "THB");
        mapping.put("菲律宾比索", "PHP");
        mapping.put("韩国元", "KRW");
        mapping.put("卢布", "RUB");
        mapping.put("林吉特", "MYR");
        mapping.put("新台币", "TWD");
        mapping.put("南非兰特", "ZAR");
        return Map.copyOf(mapping);
    }
}

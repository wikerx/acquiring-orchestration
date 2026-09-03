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
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : boc汇率汇率htmlparser协作组件，位于 调度任务服务，封装该业务的本地校验、转换或运行时协作入口。
 * @status : create
 */
@Component
public class BocExchangeRateHtmlParser {

    /**
     * {@code QUOTE_CURRENCY}，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final String QUOTE_CURRENCY = "CNY";
    private static final List<DateTimeFormatter> BOC_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );
    /**
     * 行正则模式常量，统一 {@code BocExchangeRateHtmlParser} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    /**
     * {@code CELL_PATTERN}常量，统一 {@code BocExchangeRateHtmlParser} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    /**
     * {@code CURRENCY_NAME_MAP}，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final Map<String, String> CURRENCY_NAME_MAP = buildCurrencyNameMap();

    /**
     * 解析中国银行外汇牌价 HTML。
     *
     * @param html 页面 HTML
     * @return 原始汇率项目列表
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

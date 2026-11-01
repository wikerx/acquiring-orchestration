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
 * 解析中国银行外汇牌价 HTML，并将每百单位报价统一为一单位原始币种兑人民币的汇率。
 */
@Component
public class BocExchangeRateHtmlParser {


    private static final String QUOTE_CURRENCY = "CNY";
    private static final List<DateTimeFormatter> BOC_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );
    /**
     * 匹配外汇牌价表格行的受限表达式。
     */
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    /**
     * 匹配单元格内容的受限表达式，同时兼容表头和数据单元格。
     */
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");

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

    /**
     * 提取单行表格中的文本单元格并移除 HTML 标记。
     *
     * @param rowHtml 单行 HTML
     * @return 按页面顺序排列的单元格文本
     */
    private List<String> parseCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cleanText(cellMatcher.group(1)));
        }
        return cells;
    }

    /**
     * 将中行固定列顺序转换为统一原始汇率模型。
     *
     * @param cells 已清理的数据单元格
     * @return 一条原始汇率
     */
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

    /**
     * 判断当前行是否为包含列名的表头。
     *
     * @param cells 单元格文本
     * @return true 表示该行不包含报价数据
     */
    private boolean isHeaderRow(List<String> cells) {
        return cells.stream().anyMatch(cell -> cell.contains("货币") || cell.contains("发布时间"));
    }

    /**
     * 将中行每百单位外币报价转换为一单位外币兑人民币汇率。
     *
     * @param value 页面报价文本
     * @return 12 位小数汇率；页面缺失值返回 {@code null}
     */
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

    /**
     * 按中行现有两种发布时间格式解析报价时刻。
     *
     * @param value 页面发布时间
     * @return 发布时间；空值返回 {@code null}
     */
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

    /**
     * 删除脚本、样式和标签并还原当前解析需要的基础 HTML 实体。
     *
     * @param html 单元格 HTML
     * @return 可用于币种和数值解析的纯文本
     */
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

    /**
     * 构造中行中文币种名称到 ISO 4217 编码的不可变映射。
     *
     * @return 支持的页面币种映射
     */
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

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

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateHtmlParser
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Boc Exchange Rate Html Parser 协作组件，位于 调度任务服务，封装 bocexchange汇率htmlparser 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class BocExchangeRateHtmlParser {

    /**
     * QUOTE CURRENCY，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final String QUOTE_CURRENCY = "CNY";
    private static final List<DateTimeFormatter> BOC_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );
    /**
     * ROW PATTERN，用于保存 Boc Exchange Rate Html Parser 中与 rowpattern 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    /**
     * CELL PATTERN，用于保存 Boc Exchange Rate Html Parser 中与 cellpattern 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    /**
     * CURRENCY NAME MAP，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；可识别字段，日志输出必须脱敏或截断。
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

    /**
     * 解析parsecells，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 调度任务服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param rowHtml row Html 输入值，参与 行html 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 构造item对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param cells cells 输入值，参与 cells 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 判断 is header row 条件是否成立，用于控制 Boc Exchange Rate Html Parser 的后续分支。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param cells cells 输入值，参与 cells 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isHeaderRow(List<String> cells) {
        return cells.stream().anyMatch(cell -> cell.contains("货币") || cell.contains("发布时间"));
    }

    /**
     * 解析parseboc汇率，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 调度任务服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
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
     * 解析parsepublish时间，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 调度任务服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
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
     * 整理清理文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param html html 输入值，参与 html 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造currencynamemap对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @return 构造、转换或解析后的业务值
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

package com.scott.payment.component.db.sharding;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingKeyParser
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 内部异步链路缺少显式时间上下文时，从第一版平台交易号或生命周期操作号恢复业务时间；Admin/Merchant 在线查询不得依赖该组件。
 * @status : create
 */
@Component
public class TransactionShardingKeyParser {

    /**
     * 交易生命周期操作号前缀。
     */
    private static final String OPERATION_ID_PREFIX = "OP";

    /**
     * 交易号时间片格式，与 PaymentOrderNoGenerator 保持一致。
     */
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT);

    /**
     * 交易号时间片长度。
     */
    private static final int ORDER_TIME_PART_LENGTH = 17;

    /**
     * 从平台交易 ID 中解析分表时间。
     *
     * @param transactionId 第一版无前缀平台交易 ID
     * @return 交易业务时间，无法解析时返回 null
     */
    public LocalDateTime parseTransactionDateTime(String transactionId) {
        return parseBusinessDateTime(transactionId, "");
    }

    /**
     * 从生命周期操作号中解析原始交易分表时间。
     *
     * @param operationId 生命周期操作号
     * @return 交易业务时间，无法解析时返回 null
     */
    public LocalDateTime parseOperationDateTime(String operationId) {
        return parseBusinessDateTime(operationId, OPERATION_ID_PREFIX);
    }

    /**
     * 解析parsebusinessdate时间，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param prefix prefix 输入值，参与 prefix 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime parseBusinessDateTime(String value, String prefix) {
        if (!StringUtils.hasText(value) || value.length() < prefix.length() + ORDER_TIME_PART_LENGTH) {
            return null;
        }
        if (StringUtils.hasText(prefix) && !value.startsWith(prefix)) {
            return null;
        }
        String timePart = value.substring(prefix.length(), prefix.length() + ORDER_TIME_PART_LENGTH);
        try {
            return LocalDateTime.parse(timePart, ORDER_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}

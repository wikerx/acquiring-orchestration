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
 * @description : 内部异步恢复和动作列表补充查询缺少显式时间时，从第一版平台交易号或生命周期操作号恢复业务时间；详情查询和支付写链路不得依赖该组件。
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
     * 按第一版编号格式解析固定长度的毫秒时间片。
     *
     * @param value 平台交易号或生命周期操作号
     * @param prefix 编号前缀；平台交易号传空字符串
     * @return 解析出的业务时间，编号为空、前缀不符或时间片非法时返回 {@code null}
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

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
 * @description : 交易分表键时间片解析组件，位于 component-db 分表基础层，统一解析平台交易号和生命周期操作号中的业务时间。
 * @status : create
 */
@Component
public class TransactionShardingKeyParser {

    /**
     * 历史平台交易 ID 前缀。
     */
    private static final String LEGACY_TRANSACTION_ID_PREFIX = "TX";

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
     * @param transactionId 平台交易 ID，兼容无前缀和历史 TX 前缀
     * @return 交易业务时间，无法解析时返回 null
     */
    public LocalDateTime parseTransactionDateTime(String transactionId) {
        LocalDateTime dateTime = parseBusinessDateTime(transactionId, "");
        return dateTime == null ? parseBusinessDateTime(transactionId, LEGACY_TRANSACTION_ID_PREFIX) : dateTime;
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

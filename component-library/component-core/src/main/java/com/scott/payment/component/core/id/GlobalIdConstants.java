package com.scott.payment.component.core.id;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 全系统统一唯一标识生成常量，约束编号长度、时间格式、序列长度和默认时区。
 */
public final class GlobalIdConstants {

    /**
     * 编号时间片格式：yyMMddHHmmssSSS。
     */
    public static final String TIME_PATTERN = "yyMMddHHmmssSSS";

    /**
     * 编号时间片长度。
     */
    public static final int TIME_LENGTH = 15;

    /**
     * 毫秒内序列长度。
     */
    public static final int SEQUENCE_LENGTH = 6;

    /**
     * Luhn 校验位长度。
     */
    public static final int CHECK_DIGIT_LENGTH = 1;

    /**
     * 不含校验位的编号长度。
     */
    public static final int BODY_LENGTH = TIME_LENGTH + SEQUENCE_LENGTH;

    /**
     * 完整编号长度。
     */
    public static final int ID_LENGTH = BODY_LENGTH + CHECK_DIGIT_LENGTH;

    /**
     * 默认毫秒内最大序列。
     */
    public static final long DEFAULT_MAX_SEQUENCE = 999_999L;

    /**
     * 默认支付业务时区。
     */
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 编号时间格式化器。
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.ROOT);

    private GlobalIdConstants() {
    }
}

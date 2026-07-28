package com.scott.payment.component.core.id;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdConstants
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Global ID Constants 协作组件，位于 公共组件库，封装 globalIDconstants 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
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

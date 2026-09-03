package com.scott.payment.settlement.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchNumberFormatter
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 将数据库日序列格式化为稳定存储号和业务展示号；只负责格式，不生成或回收序号。
 * @status : create
 */
@Component
public final class SettlementBatchNumberFormatter {

    /**
     * {@code STORAGE_DATE}常量，统一 结算批次编号格式化器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    /**
     * 展示日期常量，统一 结算批次编号格式化器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 格式化数据库存储批次号。
     *
     * @param businessDate 独立业务日期
     * @param dailySequence 数据库分配的当日序号
     * @return SByyyyMMdd-NNNNNNNN
     */
    public String storageNumber(LocalDate businessDate, int dailySequence) {
        requireSequence(dailySequence);
        return "SB" + Objects.requireNonNull(businessDate, "business date is required").format(STORAGE_DATE)
                + "-" + String.format("%08d", dailySequence);
    }

    /**
     * 格式化运营和商户侧展示批次号。
     *
     * @param businessDate 独立业务日期
     * @param dailySequence 数据库分配的当日序号
     * @return yyyy-MM-dd NNNNNNNN
     */
    public String displayNumber(LocalDate businessDate, int dailySequence) {
        requireSequence(dailySequence);
        return Objects.requireNonNull(businessDate, "business date is required").format(DISPLAY_DATE)
                + " " + String.format("%08d", dailySequence);
    }

    private void requireSequence(int dailySequence) {
        if (dailySequence < 1 || dailySequence > 99_999_999) {
            throw new IllegalArgumentException("daily settlement sequence must be between 1 and 99999999");
        }
    }
}

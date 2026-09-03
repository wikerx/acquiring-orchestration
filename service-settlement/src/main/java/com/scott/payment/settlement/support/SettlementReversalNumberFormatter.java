package com.scott.payment.settlement.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalNumberFormatter
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 将 UTC 业务日期和数据库日序列格式化为固定长度冲正申请单号，并校验序列范围避免静默截断。
 * @status : create
 */
@Component
public final class SettlementReversalNumberFormatter {

    /**
     * {@code STORAGE_DATE}常量，统一 结算冲正编号格式化器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 格式化固定长度冲正申请单号。
     * @param businessDate UTC 业务日期
     * @param dailySequence 数据库分配的 1 至 99999999 当日序号
     * @return RByyyyMMdd-NNNNNNNN 存储单号
     * @throws IllegalArgumentException 日期为空或序号越界时抛出
     */
    public String storageNumber(LocalDate businessDate, int dailySequence) {
        if (dailySequence < 1 || dailySequence > 99_999_999) {
            throw new IllegalArgumentException("daily reversal sequence must be between 1 and 99999999");
        }
        return "SRO" + Objects.requireNonNull(businessDate, "business date is required")
                .format(STORAGE_DATE) + "-" + String.format("%08d", dailySequence);
    }
}

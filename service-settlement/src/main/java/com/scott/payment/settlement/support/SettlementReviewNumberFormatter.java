package com.scott.payment.settlement.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 将结算业务日期和数据库日序列格式化为固定长度预审单号，并校验序列范围避免静默截断。
 */
@Component
public final class SettlementReviewNumberFormatter {


    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 格式化固定长度结算预审单号。
     * @param businessDate 结算业务日期
     * @param dailySequence 数据库分配的 1 至 99999999 当日序号
     * @return SMyyyyMMdd-NNNNNNNN 存储单号
     * @throws IllegalArgumentException 日期为空或序号越界时抛出
     */
    public String storageNumber(LocalDate businessDate, int dailySequence) {
        if (dailySequence < 1 || dailySequence > 99_999_999) {
            throw new IllegalArgumentException("daily review sequence must be between 1 and 99999999");
        }
        return "SO" + Objects.requireNonNull(businessDate, "business date is required")
                .format(STORAGE_DATE) + "-" + String.format("%08d", dailySequence);
    }
}

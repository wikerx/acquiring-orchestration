package com.scott.payment.settlement.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchNumberFormatterTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证结算批次存储号和页面展示号使用同一业务日期及八位数据库序号。
 * @status : create
 */
class SettlementBatchNumberFormatterTest {

    private final SettlementBatchNumberFormatter formatter = new SettlementBatchNumberFormatter();

    @Test
    void shouldFormatStorageAndDisplayNumbers() {
        LocalDate businessDate = LocalDate.of(2026, 8, 26);

        assertThat(formatter.storageNumber(businessDate, 1)).isEqualTo("SB20260826-00000001");
        assertThat(formatter.displayNumber(businessDate, 1)).isEqualTo("2026-08-26 00000001");
    }

    @Test
    void shouldRejectOutOfRangeSequence() {
        assertThatThrownBy(() -> formatter.storageNumber(LocalDate.of(2026, 8, 26), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> formatter.storageNumber(LocalDate.of(2026, 8, 26), 100_000_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

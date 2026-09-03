package com.scott.payment.payment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionAbnormalEventMapperTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证勾兑异常重复建案时会刷新最新的金额与币种快照。
 * @status : create
 */
class TransactionAbnormalEventMapperTests {

    @Test
    void shouldRefreshMoneySnapshotWhenOccurrenceAlreadyExists() throws NoSuchMethodException {
        Method method = TransactionAbnormalEventMapper.class.getMethod(
                "upsertOccurrence", com.scott.payment.payment.entity.TransactionAbnormalEventDO.class);
        Insert insert = method.getAnnotation(Insert.class);

        assertThat(String.join("\n", insert.value()))
                .contains("platform_currency = VALUES(platform_currency)")
                .contains("platform_amount = VALUES(platform_amount)")
                .contains("channel_currency = VALUES(channel_currency)")
                .contains("channel_amount = VALUES(channel_amount)")
                .contains("amount_difference = VALUES(amount_difference)");
    }
}

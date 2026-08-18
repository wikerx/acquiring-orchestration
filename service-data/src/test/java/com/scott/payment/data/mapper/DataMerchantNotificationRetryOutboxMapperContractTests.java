package com.scott.payment.data.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 校验自动重试 Outbox 写入固定逻辑表并携带交易分片字段。 */
class DataMerchantNotificationRetryOutboxMapperContractTests {

    @Test
    void insertShouldUseTransactionOutboxWithShardTimeAndNoDynamicSql() throws NoSuchMethodException {
        Method method = DataMerchantNotificationRetryOutboxMapper.class.getMethod(
                "insert", com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO.class);
        Insert insert = method.getAnnotation(Insert.class);
        assertThat(insert).isNotNull();
        String sql = String.join("\n", insert.value());

        assertThat(sql)
                .contains("INSERT INTO transaction_event_outbox")
                .contains("event_no")
                .contains("transaction_date_time")
                .contains("#{event.transactionDateTime}")
                .contains("#{event.payloadJson}")
                .doesNotContain("${");
    }
}

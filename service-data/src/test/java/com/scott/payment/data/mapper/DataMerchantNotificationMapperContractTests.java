package com.scott.payment.data.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationMapperContractTests
 * @date : 2026-08-02 02:10
 * @email : scott_x@163.com
 * @description : 校验 service-data 通知逻辑表查询和状态 CAS 始终携带交易分片时间。
 * @status : create
 */
class DataMerchantNotificationMapperContractTests {

    @Test
    void dueScanAndRecoveryShouldUseOneQuarterHalfOpenRange() throws NoSuchMethodException {
        String dueSql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectDueForNotify",
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                int.class));
        String recoverySql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "recoverStaleProcessing",
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class));

        assertQuarterRange(dueSql);
        assertQuarterRange(recoverySql);
        assertThat(recoverySql)
                .contains("notify_status = 'PROCESSING'")
                .contains("last_attempt_no < max_retry_count");
    }

    @Test
    void notificationPointReadAndCasShouldCarryTransactionTimeVersionAndStatus() throws NoSuchMethodException {
        String readySql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectReadyByTransactionId",
                String.class,
                LocalDateTime.class,
                LocalDateTime.class));
        String processingSql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "markProcessing",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                LocalDateTime.class));
        String successSql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "markSuccess",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                LocalDateTime.class));
        String failedSql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "markFailed",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                String.class,
                LocalDateTime.class,
                String.class,
                LocalDateTime.class));

        assertThat(readySql)
                .contains("FROM transaction_merchant_notification")
                .contains("transaction_id = #{transactionId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .doesNotContain("${");
        assertCas(processingSql, "notify_status IN ('INIT', 'FAILED')");
        assertCas(successSql, "notify_status = 'PROCESSING'");
        assertCas(failedSql, "notify_status = 'PROCESSING'");
    }

    private static String selectSql(Method method) {
        Select annotation = method.getAnnotation(Select.class);
        assertThat(annotation).isNotNull();
        return String.join("\n", annotation.value());
    }

    private static String updateSql(Method method) {
        Update annotation = method.getAnnotation(Update.class);
        assertThat(annotation).isNotNull();
        return String.join("\n", annotation.value());
    }

    private static void assertQuarterRange(String sql) {
        assertThat(sql)
                .contains("transaction_date_time >= #{beginTime}")
                .contains("transaction_date_time < #{endTimeExclusive}")
                .doesNotContain("${");
    }

    private static void assertCas(String sql, String statusPredicate) {
        assertThat(sql)
                .contains("UPDATE transaction_merchant_notification")
                .contains("id = #{id}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains(statusPredicate)
                .contains("deleted = 0")
                .doesNotContain("${");
    }
}

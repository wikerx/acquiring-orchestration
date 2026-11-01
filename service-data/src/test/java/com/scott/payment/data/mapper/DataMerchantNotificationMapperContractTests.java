package com.scott.payment.data.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 service-data 通知逻辑表查询和状态 CAS 始终携带交易分片时间。
 */
class DataMerchantNotificationMapperContractTests {

    /**
     * 验证到期扫描和恢复查询只使用单季度左闭右开范围。
     */
    @Test
    void dueScanAndRecoveryShouldUseOneQuarterHalfOpenRange() throws NoSuchMethodException {
        String dueSql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectDueForNotify",
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                int.class));
        String staleSelectSql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectStaleProcessing",
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                int.class));
        String recoverySql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "recoverStaleProcessingCas",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                LocalDateTime.class,
                LocalDateTime.class));

        assertQuarterRange(dueSql);
        assertQuarterRange(staleSelectSql);
        assertThat(staleSelectSql).contains("LIMIT #{limit}");
        assertCas(recoverySql, "notify_status = 'PROCESSING'");
        assertThat(recoverySql)
                .contains("update_time < #{staleBefore}")
                .contains("last_attempt_no = GREATEST(last_attempt_no - 1, 0)")
                .doesNotContain("last_attempt_no < max_retry_count");
    }

    /**
     * 验证通知点查和状态更新同时携带分片时间、版本和当前状态条件。
     */
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
        String retryDueSql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectReadyByRetryEvent",
                String.class,
                String.class,
                LocalDateTime.class,
                Integer.class,
                Integer.class,
                LocalDateTime.class));

        assertThat(readySql)
                .contains("FROM transaction_merchant_notification")
                .contains("transaction_id = #{transactionId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .doesNotContain("${");
        assertCas(processingSql, "notify_status IN ('INIT', 'FAILED')");
        assertCas(successSql, "notify_status = 'PROCESSING'");
        assertCas(failedSql, "notify_status = 'PROCESSING'");
        assertThat(retryDueSql)
                .contains("transaction_id = #{transactionId}")
                .contains("notify_id = #{notifyId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("#{attemptNo} = 1 AND notify_status = 'INIT'")
                .contains("#{attemptNo} BETWEEN 2 AND 5")
                .contains("notify_status = 'FAILED'")
                .contains("last_attempt_no < LEAST(max_retry_count, 5)")
                .contains("next_retry_time <= #{now}")
                .doesNotContain("${");
    }

    /**
     * 人工重发必须显式排除仍在执行的自动五次计划，并保持精确分片和版本 CAS。
     */
    @Test
    void manualRetryShouldUseExactShardAndControlledStates() throws NoSuchMethodException {
        String retryableSql = selectSql(DataMerchantNotificationMapper.class.getMethod(
                "selectRetryableByTransactionId",
                String.class,
                LocalDateTime.class));
        String claimSql = updateSql(DataMerchantNotificationMapper.class.getMethod(
                "markProcessingForManualRetry",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                String.class,
                LocalDateTime.class));

        assertThat(retryableSql)
                .contains("transaction_id = #{transactionId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("notify_status IN ('SUCCESS', 'CLOSED')")
                .contains("notify_status = 'FAILED' AND next_retry_time IS NULL")
                .doesNotContain("notify_status = 'PROCESSING'")
                .doesNotContain("${");
        assertCas(claimSql, "notify_status IN ('SUCCESS', 'CLOSED')");
        assertThat(claimSql)
                .contains("notify_status = 'FAILED' AND next_retry_time IS NULL")
                .contains("processing_mode = 'MANUAL'")
                .contains("processing_event_id = #{callbackEventId}")
                .doesNotContain("max_retry_count = GREATEST")
                .doesNotContain("AND notify_status = 'PROCESSING'");
    }

    /**
     * 读取 Mapper 方法的 Select 注解 SQL 供契约断言。
     */
    private static String selectSql(Method method) {
        Select annotation = method.getAnnotation(Select.class);
        assertThat(annotation).isNotNull();
        return String.join("\n", annotation.value());
    }

    /**
     * 读取 Mapper 方法的 Update 注解 SQL 供契约断言。
     */
    private static String updateSql(Method method) {
        Update annotation = method.getAnnotation(Update.class);
        assertThat(annotation).isNotNull();
        return String.join("\n", annotation.value());
    }

    /**
     * 断言查询 SQL 使用标准季度半开区间。
     */
    private static void assertQuarterRange(String sql) {
        assertThat(sql)
                .contains("transaction_date_time >= #{beginTime}")
                .contains("transaction_date_time < #{endTimeExclusive}")
                .doesNotContain("${");
    }

    /**
     * 断言状态更新 SQL 同时校验分片键、版本和当前状态。
     */
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

package com.scott.payment.payment.mapper.provider;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementSqlProviderTests
 * @date : 2026-08-06
 * @email : scott_x@163.com
 * @description : 退款查询 SQL 契约测试，防止一对多通知记录放大退款数量和金额汇总。
 * @status : create
 */
class RefundManagementSqlProviderTests {

    /**
     * 所有退款查询必须以退款动作事实为统计粒度，不允许关联一对多通知表。
     */
    @Test
    void shouldNotJoinMerchantNotificationInRefundQueries() {
        assertRefundFactGrain(RefundManagementSqlProvider.countSql());
        assertRefundFactGrain(RefundManagementSqlProvider.pageSql());
        assertRefundFactGrain(RefundManagementSqlProvider.statusSummarySql());
        assertRefundFactGrain(RefundManagementSqlProvider.currencySummarySql());
        assertRefundFactGrain(RefundManagementSqlProvider.detailSql());
    }

    /**
     * 列表通知状态当前不参与查询聚合，避免用不稳定快照换取错误的资金统计。
     */
    @Test
    void shouldExposeNotificationStatusAsUnavailableWithoutAggregation() {
        assertThat(RefundManagementSqlProvider.pageSql())
                .contains("NULL AS merchant_notification_status")
                .doesNotContain("MAX(n.notify_status)")
                .doesNotContain("GROUP BY o.id");
    }

    /** 分币种统计必须同时覆盖总退款金额和待审批金额。 */
    @Test
    void shouldAggregateTotalAndPendingApprovalAmountsByCurrency() {
        assertThat(RefundManagementSqlProvider.currencySummarySql())
                .contains("AS total_amount")
                .contains("a.approval_status = 'PENDING'")
                .contains("AS pending_approval_amount");
    }

    /** 所有 Provider SQL 都必须能被 MyBatis 动态 SQL 语言驱动解析。 */
    @Test
    void shouldGenerateParseableMybatisDynamicSql() {
        assertParseable(RefundManagementSqlProvider.countSql());
        assertParseable(RefundManagementSqlProvider.pageSql());
        assertParseable(RefundManagementSqlProvider.statusSummarySql());
        assertParseable(RefundManagementSqlProvider.currencySummarySql());
        assertParseable(RefundManagementSqlProvider.detailSql());
    }

    private void assertRefundFactGrain(String sql) {
        assertThat(sql)
                .contains("FROM transaction_operation o")
                .doesNotContain("transaction_merchant_notification")
                .doesNotContain("JOIN transaction_merchant_notification");
    }

    private void assertParseable(String sql) {
        assertThatCode(() -> new XMLLanguageDriver()
                .createSqlSource(new Configuration(), sql, Object.class))
                .doesNotThrowAnyException();
    }
}

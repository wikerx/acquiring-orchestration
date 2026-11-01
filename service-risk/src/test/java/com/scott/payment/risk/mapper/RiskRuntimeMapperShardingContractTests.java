package com.scott.payment.risk.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 Risk 交易逻辑表 SQL 按商户、币种和时间范围路由，禁止主路径动态拼接物理表。
 */
class RiskRuntimeMapperShardingContractTests {

    /**
     * 验证金额聚合按商户和币种隔离，并使用左闭右开时间区间。
     */
    @Test
    void amountAggregationShouldIsolateMerchantCurrencyAndHalfOpenTimeRange() throws NoSuchMethodException {
        Method method = RiskRuntimeMapper.class.getMethod(
                "sumRiskApprovedTransactionAmount",
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                String.class);

        String sql = selectSql(method);

        assertThat(method.getReturnType()).isEqualTo(BigDecimal.class);
        assertThat(method.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
        assertThat(sql)
                .contains("FROM transaction_order")
                .contains("merchant_id = #{merchantId}")
                .contains("transaction_currency = #{currency}")
                .contains("transaction_date_time >= #{beginTime}")
                .contains("transaction_date_time < #{endTime}")
                .contains("COALESCE(root_transaction_id, '') <> #{excludeTransactionId}")
                .contains("COALESCE(latest_transaction_id, '') <> #{excludeTransactionId}")
                .doesNotContain("${", "UNION ALL", "&lt;&gt;");
    }

    /**
     * 验证交易状态查询只访问业务时间所属的单一逻辑季度范围。
     */
    @Test
    void statusLookupShouldUseOneLogicalQuarterRange() throws NoSuchMethodException {
        Method method = RiskRuntimeMapper.class.getMethod(
                "selectPaymentTransactionStatus",
                String.class,
                LocalDateTime.class,
                LocalDateTime.class);

        String sql = selectSql(method);

        assertThat(method.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
        assertThat(sql)
                .contains("FROM transaction_operation")
                .contains("transaction_id = #{transactionId}")
                .contains("transaction_date_time >= #{beginTime}")
                .contains("transaction_date_time < #{endTimeExclusive}")
                .contains("deleted = 0")
                .doesNotContain("${", "UNION ALL");
    }

    /**
     * 读取 Mapper 方法上的 Select 注解 SQL 供分片契约断言。
     */
    private static String selectSql(Method method) {
        Select annotation = method.getAnnotation(Select.class);
        assertThat(annotation).isNotNull();
        return String.join("\n", annotation.value());
    }
}

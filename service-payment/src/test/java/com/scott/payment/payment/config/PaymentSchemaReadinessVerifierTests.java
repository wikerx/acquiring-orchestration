package com.scott.payment.payment.config;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.mapper.PaymentSchemaReadinessMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentSchemaReadinessVerifierTests
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 支付数据库启动门禁测试，覆盖完整结构、缺失分表字段、查询故障和主库路由约束。
 * @status : create
 */
class PaymentSchemaReadinessVerifierTests {

    @Test
    void shouldAcceptCompatibleTransactionOperationTables() {
        PaymentSchemaReadinessVerifier verifier = new PaymentSchemaReadinessVerifier(List::of);

        assertThatCode(verifier::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void shouldFailStartupWithEveryIncompatibleShardAndMigrationHint() {
        PaymentSchemaReadinessVerifier verifier = new PaymentSchemaReadinessVerifier(
                () -> List.of("transaction_operation", "transaction_operation_202604"));

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction_operation")
                .hasMessageContaining("transaction_operation_202604")
                .hasMessageContaining(PaymentSchemaReadinessVerifier.MIGRATION_FILE);
    }

    @Test
    void shouldFailStartupWithoutLeakingSqlDetailsWhenReadinessQueryFails() {
        PaymentSchemaReadinessVerifier verifier = new PaymentSchemaReadinessVerifier(() -> {
            throw new IllegalStateException("select credential from hidden_table failed");
        });

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment schema readiness check failed; review and apply "
                        + PaymentSchemaReadinessVerifier.MIGRATION_FILE)
                .hasMessageNotContaining("hidden_table");
    }

    @Test
    void shouldAlwaysReadPaymentSchemaContractFromMasterDataSource() throws NoSuchMethodException {
        Method method = PaymentSchemaReadinessMapper.class
                .getMethod("selectIncompatibleTransactionOperationTables");

        assertThat(method.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
    }
}

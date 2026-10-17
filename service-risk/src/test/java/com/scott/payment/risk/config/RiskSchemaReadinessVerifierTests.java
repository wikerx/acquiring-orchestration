package com.scott.payment.risk.config;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.risk.mapper.RiskSchemaReadinessMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskSchemaReadinessVerifierTests
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 风控数据库启动门禁测试，覆盖完整契约、缺表、缺列、查询故障和主库路由约束。
 * @status : create
 */
class RiskSchemaReadinessVerifierTests {

    @Test
    void shouldAcceptCompleteRiskSchemaContract() {
        RiskSchemaReadinessVerifier verifier = new RiskSchemaReadinessVerifier(() -> List.of(
                "table:risk_merchant_function_config",
                "table:risk_evaluation_record",
                "table:risk_evaluation_hit_detail",
                "column:risk_merchant_function_config.merchant_id",
                "column:risk_merchant_function_config.module_type",
                "column:risk_merchant_function_config.function_code",
                "column:risk_merchant_function_config.enabled",
                "column:risk_merchant_function_config.deleted",
                "column:risk_evaluation_record.decision_reason_code",
                "column:risk_evaluation_hit_detail.reason_code"));

        assertThatCode(verifier::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void shouldFailStartupWithMigrationHintWhenRequiredObjectsAreMissing() {
        RiskSchemaReadinessVerifier verifier = new RiskSchemaReadinessVerifier(() -> List.of(
                "table:risk_evaluation_record",
                "column:risk_evaluation_record.decision_reason_code"));

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("table risk_merchant_function_config")
                .hasMessageContaining("table risk_evaluation_hit_detail")
                .hasMessageContaining(RiskSchemaReadinessVerifier.MIGRATION_FILE);
    }

    @Test
    void shouldFailStartupWhenRequiredTableExistsButColumnIsMissing() {
        RiskSchemaReadinessVerifier verifier = new RiskSchemaReadinessVerifier(() -> List.of(
                "table:risk_merchant_function_config",
                "column:risk_merchant_function_config.merchant_id",
                "column:risk_merchant_function_config.module_type",
                "column:risk_merchant_function_config.function_code",
                "column:risk_merchant_function_config.enabled",
                "column:risk_merchant_function_config.deleted",
                "table:risk_evaluation_record",
                "column:risk_evaluation_record.decision_reason_code",
                "table:risk_evaluation_hit_detail"));

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column risk_evaluation_hit_detail.reason_code")
                .hasMessageNotContaining("table risk_evaluation_hit_detail")
                .hasMessageContaining(RiskSchemaReadinessVerifier.MIGRATION_FILE);
    }

    @Test
    void shouldFailStartupWithoutLeakingSqlDetailsWhenReadinessQueryFails() {
        RiskSchemaReadinessVerifier verifier = new RiskSchemaReadinessVerifier(() -> {
            throw new IllegalStateException("select secret from internal_table failed");
        });

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Risk schema readiness check failed; review and apply "
                        + RiskSchemaReadinessVerifier.MIGRATION_FILE)
                .hasMessageNotContaining("internal_table");
    }

    @Test
    void shouldAlwaysReadSchemaContractFromMasterDataSource() throws NoSuchMethodException {
        Method method = RiskSchemaReadinessMapper.class.getMethod("selectPresentRequiredColumns");

        assertThat(method.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
    }
}

package com.scott.payment.clearing.config;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证自动清分启动门禁同时保护正式交易拓扑和内部接口 HMAC 边界。 */
class ClearingActivationGuardTest {

    /** 自动清分启动时，内部管理接口不允许关闭 HMAC 后退化为 caller Header 鉴权。 */
    @Test
    void shouldRejectDisabledInternalAuthentication() {
        InternalServiceAuthProperties authProperties = new InternalServiceAuthProperties();
        authProperties.setEnabled(false);
        ClearingActivationGuard guard = new ClearingActivationGuard(
                new ClearingProperties(), new TransactionShardingProperties(), authProperties);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication must be enabled");
    }

    /** 自动清分服务启动时必须替换仓库内的开发共享密钥。 */
    @Test
    void shouldRejectDefaultDevelopmentSecret() {
        InternalServiceAuthProperties authProperties = new InternalServiceAuthProperties();
        ClearingActivationGuard guard = new ClearingActivationGuard(
                new ClearingProperties(), new TransactionShardingProperties(), authProperties);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development secret");
    }

    /** 内部接口路径不得被通用 HMAC 白名单覆盖。 */
    @Test
    void shouldRejectWhitelistThatBypassesClearingInternalEndpoints() {
        InternalServiceAuthProperties authProperties = new InternalServiceAuthProperties();
        authProperties.setWhitelist(List.of("/internal/**", "/actuator/health/**"));
        ClearingActivationGuard guard = new ClearingActivationGuard(
                new ClearingProperties(), new TransactionShardingProperties(), authProperties);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whitelist");
    }

    /** HMAC 有效但交易拓扑仍为25表时必须拒绝启动自动清分。 */
    @Test
    void shouldRejectAutomaticRuntimeBeforeTwentyEightTableTopology() {
        TransactionShardingProperties shardingProperties = new TransactionShardingProperties();
        shardingProperties.setLogicTables(shardingProperties.getLogicTables().subList(0, 25));
        InternalServiceAuthProperties authProperties = new InternalServiceAuthProperties();
        authProperties.setSecret("unit-test-clearing-internal-secret-32-bytes");
        ClearingActivationGuard guard = new ClearingActivationGuard(
                new ClearingProperties(), shardingProperties, authProperties);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("28-table");
    }

    /** 28表拓扑、HMAC 开启且注入非默认密钥时允许完成自动启动校验。 */
    @Test
    void shouldAllowAutomaticRuntimeWithInjectedSecret() {
        InternalServiceAuthProperties authProperties = new InternalServiceAuthProperties();
        authProperties.setSecret("unit-test-clearing-internal-secret-32-bytes");
        ClearingActivationGuard guard = new ClearingActivationGuard(
                new ClearingProperties(), new TransactionShardingProperties(), authProperties);

        guard.afterSingletonsInstantiated();
    }
}

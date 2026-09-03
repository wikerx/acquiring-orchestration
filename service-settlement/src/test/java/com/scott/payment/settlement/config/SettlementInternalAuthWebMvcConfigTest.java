package com.scott.payment.settlement.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalAuthWebMvcConfigTest
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : 验证结算内部接口在 HMAC 密钥缺失或占位符未解析时启动失败，避免错误实例注册后持续拒绝管理命令。
 * @status : create
 */
class SettlementInternalAuthWebMvcConfigTest {

    @Test
    void shouldFailClosedUntilCallerScopedCredentialsAreProvided() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        InternalRequestReplayGuard replayGuard = mock(InternalRequestReplayGuard.class);

        assertThatThrownBy(() -> new SettlementInternalAuthWebMvcConfig(properties, replayGuard))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caller credentials");

        assertThatCode(() -> new SettlementInternalAuthWebMvcConfig(validProperties(), replayGuard))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnresolvedInternalAuthSecretDuringConfiguration() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        InternalServiceAuthProperties.CallerCredential credential = callerCredential();
        credential.setActiveSecret("${acquiring.internal-auth.edges.admin-settlement.active-secret}");
        properties.setCallers(Map.of("service-admin", credential));

        assertThatThrownBy(() -> new SettlementInternalAuthWebMvcConfig(
                properties, mock(InternalRequestReplayGuard.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    /** 构造结算服务允许的 Admin 调用凭据。 */
    private InternalServiceAuthProperties validProperties() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setCallers(Map.of("service-admin", callerCredential()));
        return properties;
    }

    /** 构造只允许访问结算内部路径的测试凭据。 */
    private InternalServiceAuthProperties.CallerCredential callerCredential() {
        InternalServiceAuthProperties.CallerCredential credential =
                new InternalServiceAuthProperties.CallerCredential();
        credential.setActiveSecret("unit-test-settlement-internal-secret-32-bytes");
        credential.setAllowedPaths(List.of("/internal/settlement/**"));
        return credential;
    }
}

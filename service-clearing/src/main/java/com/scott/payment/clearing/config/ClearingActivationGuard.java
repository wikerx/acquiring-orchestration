package com.scott.payment.clearing.config;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * 清分启动期失败关闭门禁；服务自动运行前强制校验内部 HMAC、正式交易拓扑和非默认密钥。
 */
@Component
public class ClearingActivationGuard implements SmartInitializingSingleton {

    private static final String CLEARING_INTERNAL_PROBE_PATH =
            "/internal/clearing/v1/transactions/search";
    /**
     * 用于验证内部认证白名单不会绕过清分探测路径。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ClearingProperties clearingProperties;
    private final TransactionShardingProperties shardingProperties;
    private final InternalServiceAuthProperties authProperties;

    /**
     * 创建清分激活门禁。
     *
     * @param clearingProperties 清分运行参数
     * @param shardingProperties 已发布交易分片规则
     * @param authProperties 内部服务 HMAC、防重放和白名单参数
     */
    public ClearingActivationGuard(ClearingProperties clearingProperties,
                                   TransactionShardingProperties shardingProperties,
                                   InternalServiceAuthProperties authProperties) {
        this.clearingProperties = clearingProperties;
        this.shardingProperties = shardingProperties;
        this.authProperties = authProperties;
    }

    /**
     * 启动完成前校验内部认证和正式拓扑，失败时直接阻止应用提供内部接口或消费消息。
     */
    @Override
    public void afterSingletonsInstantiated() {
        validateInternalSecurity();
        clearingProperties.validateRuntime(shardingProperties.getLogicTables().size());
    }

    /**
     * 禁止清分内部接口关闭签名、命中认证白名单或继续使用开发共享密钥。
     */
    private void validateInternalSecurity() {
        if (!authProperties.isEnabled()) {
            throw new IllegalStateException("clearing internal service authentication must be enabled");
        }
        authProperties.validate();
        if (authProperties.getWhitelist() == null
                || authProperties.getWhitelist().stream().anyMatch(pattern ->
                pattern != null && !pattern.trim().isEmpty()
                        && PATH_MATCHER.match(pattern.trim(), CLEARING_INTERNAL_PROBE_PATH))) {
            throw new IllegalStateException("clearing internal endpoints must not match the authentication whitelist");
        }
    }
}

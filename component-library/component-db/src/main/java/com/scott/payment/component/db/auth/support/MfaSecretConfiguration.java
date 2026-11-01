package com.scott.payment.component.db.auth.support;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Set;

/**
 * MFA 主密钥启动门禁，位于认证数据支撑层；只对持有 MFA 数据的 Admin/Merchant 服务强制校验环境密钥并初始化加密组件。
 */
@Configuration(proxyBeanMethods = false)
public class MfaSecretConfiguration implements InitializingBean {

    /**
     * 需要在启动阶段完成 MFA 主密钥初始化的固定服务身份；非敏感且不可为空。
     */
    private static final Set<String> MFA_SERVICE_NAMES = Set.of("service-admin", "service-merchant");

    /**
     * Spring 运行环境，只读取服务身份、活动 profile 和 MFA 密钥配置；对象不可为空。
     */
    private final Environment environment;

    /**
     * 创建 MFA 主密钥启动门禁。
     *
     * @param environment 当前 Spring 运行环境
     */
    public MfaSecretConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * 在启用 MFA 的服务启动时校验密钥配置，阻止缺少或使用弱默认密钥的实例接收流量。
     * 非 MFA 服务不执行该校验，避免公共数据库组件要求无关服务配置认证密钥。
     */
    @Override
    public void afterPropertiesSet() {
        if (!MFA_SERVICE_NAMES.contains(environment.getProperty("spring.application.name", ""))) {
            return;
        }
        String masterSecret = MfaSecretKeyPolicy.resolve(
                environment.getProperty("payment.mfa.secret"),
                environment.getProperty("payment.mfa.allow-ephemeral-test-secret", Boolean.class, false),
                environment.getActiveProfiles());
        List<String> previousSecrets = MfaSecretKeyPolicy.resolvePreviousSecrets(
                environment.getProperty("payment.mfa.previous-secrets", String[].class, new String[0]),
                masterSecret);
        MfaSecretCrypto.configure(masterSecret, previousSecrets);
    }
}

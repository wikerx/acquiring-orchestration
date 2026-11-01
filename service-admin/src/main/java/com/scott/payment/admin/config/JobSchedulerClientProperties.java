package com.scott.payment.admin.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理后台任务调度客户端配置属性，仅维护远程调用开关。
 */
@Data
@ConfigurationProperties(prefix = "admin.job-client")
public class JobSchedulerClientProperties {

    /**
     * 是否启用远程调度中心调用。
     */
    private boolean remoteEnabled = true;

    /**
     * service-job 内部接口固定调用方。
     */
    private String internalCaller = "service-admin";

    /**
     * Admin → Job 调用链 active HMAC 密钥。
     */
    private String internalSecret;

    /**
     * 远程调度开启时校验服务身份和 Nacos 注入密钥。
     */
    public void validate() {
        if (remoteEnabled) {
            InternalServiceClientCredentialValidator.validate(
                    "admin job-client", "service-admin", internalCaller, internalSecret);
        }
    }

}

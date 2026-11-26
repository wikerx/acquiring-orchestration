package com.scott.payment.admin.support.risk;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.crypto.SensitiveFieldCipher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 使用 Admin 已配置的持久化主密钥加密预警凭据。 */
@Component
public class DisputeAlertCredentialCrypto {

    private static final String PURPOSE = "dispute-alert-credential-v1";
    private final Environment environment;

    public DisputeAlertCredentialCrypto(Environment environment) {
        this.environment = environment;
    }

    public String encrypt(long accountId, String reference, String field, String value) {
        try {
            String masterKey = RiskSensitiveValueKeyPolicy.resolve(
                    environment.getProperty("payment.risk.secret"), false, environment.getActiveProfiles());
            String aad = PURPOSE + ":" + accountId + ":" + reference + ":" + field;
            return SensitiveFieldCipher.encrypt(value, PURPOSE + ":" + masterKey, aad);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "预警凭据加密失败，请联系管理员检查 Admin 的敏感字段加密配置");
        }
    }
}

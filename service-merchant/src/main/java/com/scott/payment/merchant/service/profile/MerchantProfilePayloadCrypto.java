package com.scott.payment.merchant.service.profile;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.crypto.SensitiveFieldCipher;
import com.scott.payment.merchant.config.MerchantProfileProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfilePayloadCrypto
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料变更快照加密边界，使用申请编号作为 AAD 防止跨申请替换密文
 * @status : create
 */
@Component
public class MerchantProfilePayloadCrypto {

    /** 商户资料字段级加密配置。 */
    private final MerchantProfileProperties properties;

    /** 创建资料变更快照加密组件。 */
    public MerchantProfilePayloadCrypto(MerchantProfileProperties properties) {
        this.properties = properties;
    }

    /** 加密资料快照 JSON。 */
    public String encrypt(String requestNo, String json) {
        requireConfigured();
        return SensitiveFieldCipher.encrypt(json, properties.getEncryptionSecret(), requestNo);
    }

    /** 解密资料快照 JSON。 */
    public String decrypt(String requestNo, String cipher) {
        requireConfigured();
        return SensitiveFieldCipher.decrypt(cipher, properties.getEncryptionSecret(), requestNo);
    }

    /** 脱敏证件号，仅保留有限首尾字符供资料核对。 */
    public String maskIdNumber(String idNumber) {
        if (!StringUtils.hasText(idNumber)) {
            return null;
        }
        String value = idNumber.trim();
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        if (value.length() <= 8) {
            return value.substring(0, 2) + "*".repeat(value.length() - 4)
                    + value.substring(value.length() - 2);
        }
        return value.substring(0, 3) + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    /** 缺少密钥时拒绝保存敏感资料，避免降级为明文。 */
    private void requireConfigured() {
        if (!StringUtils.hasText(properties.getEncryptionSecret())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile encryption secret is not configured");
        }
    }
}

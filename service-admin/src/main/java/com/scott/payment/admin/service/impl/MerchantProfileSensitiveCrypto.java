package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.config.MerchantProfileProperties;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.crypto.SensitiveFieldCipher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileSensitiveCrypto
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户相关人员敏感字段加密边界，负责证件号 AES-GCM 加密和管理端脱敏展示
 * @status : create
 */
@Component
public class MerchantProfileSensitiveCrypto {

    /** 商户资料加密配置；包含敏感主密钥，不允许为空或写入日志。 */
    private final MerchantProfileProperties properties;

    /**
     * 创建商户敏感字段加密组件。
     *
     * @param properties 商户资料安全配置，不允许为空
     */
    public MerchantProfileSensitiveCrypto(MerchantProfileProperties properties) {
        this.properties = properties;
    }

    /**
     * 加密相关人员证件号，使用商户号作为 AAD，避免密文被复制到其他商户后解密。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param displayOrder 人员展示顺序，仅用于保持调用契约，不进入密文或日志
     * @param idNumber 明文证件号，敏感，可为空
     * @return AES-GCM 密文；输入为空时返回 null
     */
    public String encryptIdNumber(String merchantId, int displayOrder, String idNumber) {
        if (!StringUtils.hasText(idNumber)) {
            return null;
        }
        if (!StringUtils.hasText(properties.getEncryptionSecret())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "商户敏感资料加密密钥未配置");
        }
        return SensitiveFieldCipher.encrypt(idNumber.trim(), properties.getEncryptionSecret(), merchantId);
    }

    /**
     * 脱敏证件号，仅保留有限首尾字符供管理员核对。
     *
     * @param idNumber 明文证件号，敏感，可为空
     * @return 脱敏值；输入为空时返回 null
     */
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

    /** 使用申请编号作为 AAD 解密资料变更快照，解密失败时不返回部分正文。 */
    public String decryptChangeSnapshot(String requestNo, String cipher) {
        if (!StringUtils.hasText(properties.getEncryptionSecret())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "商户敏感资料加密密钥未配置");
        }
        try {
            return SensitiveFieldCipher.decrypt(cipher, properties.getEncryptionSecret(), requestNo);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "商户资料变更快照校验失败");
        }
    }
}

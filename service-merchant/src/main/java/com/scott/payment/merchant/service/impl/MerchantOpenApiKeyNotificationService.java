package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户门户密钥生命周期通知服务，只提取密钥指纹尾号，不传输或记录完整密钥材料。
 */
@Slf4j
@Service
public class MerchantOpenApiKeyNotificationService {

    /**
     * 密钥重置通知模板编码。
     */
    public static final String TEMPLATE_RESET = "API_KEY_RESET";
    /**
     * 密钥启用通知模板编码。
     */
    public static final String TEMPLATE_ENABLED = "API_KEY_ENABLED";
    /**
     * 密钥停用通知模板编码。
     */
    public static final String TEMPLATE_DISABLED = "API_KEY_DISABLED";
    private static final String SCENE = "API_KEY_CHANGED";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MerchantTemplateEmailService templateEmailService;
    private final MerchantRuntimeProfileCacheService merchantProfileService;

    /**
     * 创建商户开放接口密钥生命周期通知服务。
     */
    public MerchantOpenApiKeyNotificationService(MerchantTemplateEmailService templateEmailService,
                                                 MerchantRuntimeProfileCacheService merchantProfileService) {
        this.templateEmailService = templateEmailService;
        this.merchantProfileService = merchantProfileService;
    }

    /**
     * 在密钥事务提交后向当前商户联系人发送生命周期通知。
     *
     * @param merchantId 当前认证商户号
     * @param templateCode 密钥轮换、启用或停用模板代码
     * @param keyType 本次操作对应的密钥类型
     * @param material 只读取指纹字段的密钥概要
     */
    public void sendAfterCommit(String merchantId,
                                String templateCode,
                                OpenApiKeyType keyType,
                                OpenApiMerchantKeyMaterialVO material) {
        MerchantRuntimeProfile merchant = merchantProfileService.findRuntimeProfile(merchantId);
        if (merchant == null || !StringUtils.hasText(merchant.getContactEmail())) {
            return;
        }
        Runnable task = () -> send(merchant, templateCode, keyType, material);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 在当前事务成功提交后执行延迟动作。
             */
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /**
     * 发送商户 OpenAPI 密钥安全通知，不在模板变量中放入密钥正文。
     */
    private void send(MerchantRuntimeProfile merchant,
                      String templateCode,
                      OpenApiKeyType keyType,
                      OpenApiMerchantKeyMaterialVO material) {
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("merchantName", merchant.getMerchantName());
            variables.put("merchantNo", merchant.getMerchantId());
            variables.put("keyName", keyName(keyType));
            variables.put("keyLast4", fingerprintLast4(keyType, material));
            variables.put("operatorName", operatorName());
            variables.put("operationTime", TIME_FORMATTER.format(LocalDateTime.now()));
            templateEmailService.sendByTemplate(new MerchantEmailSendCommand(
                    AuthConstants.APP_MERCHANT,
                    merchant.getMerchantId(),
                    merchant.getMerchantId(),
                    merchant.getMerchantName(),
                    templateCode,
                    SCENE,
                    MerchantLocaleSupport.normalize(merchant.getDefaultLocale()),
                    List.of(merchant.getContactEmail()),
                    variables,
                    SCENE,
                    merchant.getMerchantId()
            ));
        } catch (RuntimeException exception) {
            log.warn("商户OpenAPI密钥通知发送失败, event: MERCHANT_OPENAPI_KEY_NOTICE_FAILED, merchantId: {}, templateCode: {}, exceptionType: {}",
                    merchant.getMerchantId(), templateCode, exception.getClass().getSimpleName());
        }
    }

    /**
     * 返回密钥类型对应的商户可见名称。
     */
    private String keyName(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            return "JWT 签名密钥";
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            return "平台请求体密钥";
        }
        return "商户响应密钥";
    }

    /**
     * 提取密钥指纹末四位用于安全通知。
     */
    private String fingerprintLast4(OpenApiKeyType keyType, OpenApiMerchantKeyMaterialVO material) {
        if (material == null) {
            return "----";
        }
        String fingerprint;
        if (keyType == OpenApiKeyType.JWT_KEY) {
            fingerprint = material.getJwtKeyFingerprint();
        } else if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            fingerprint = material.getPlatformPayloadPublicKeyFingerprint();
        } else {
            fingerprint = material.getMerchantResponsePublicKeyFingerprint();
        }
        if (!StringUtils.hasText(fingerprint)) {
            return "----";
        }
        String compact = fingerprint.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return compact.length() <= 4 ? compact : compact.substring(compact.length() - 4);
    }

    /**
     * 读取当前操作人名称，缺失时使用系统操作人标识。
     */
    private String operatorName() {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null) {
            return "Merchant Administrator";
        }
        if (StringUtils.hasText(operator.getRealName())) {
            return operator.getRealName();
        }
        return StringUtils.hasText(operator.getLoginAccount()) ? operator.getLoginAccount() : "Merchant Administrator";
    }
}

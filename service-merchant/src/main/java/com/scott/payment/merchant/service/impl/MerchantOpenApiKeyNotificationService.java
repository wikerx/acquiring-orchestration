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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiKeyNotificationService
 * @date : 2026-08-05 00:00
 * @email : scott_x@163.com
 * @description : 商户门户密钥生命周期通知服务，只从密钥概要提取指纹尾号，不传输或记录完整密钥材料
 * @status : create
 */
@Slf4j
@Service
public class MerchantOpenApiKeyNotificationService {

    /**
     * 模板重置，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TEMPLATE_RESET = "API_KEY_RESET";
    /**
     * 模板启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TEMPLATE_ENABLED = "API_KEY_ENABLED";
    /**
     * {@code TEMPLATE_DISABLED}，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TEMPLATE_DISABLED = "API_KEY_DISABLED";
    /**
     * 场景常量，统一 {@code MerchantOpenApiKeyNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SCENE = "API_KEY_CHANGED";
    /**
     * 时间格式化器常量，统一 {@code MerchantOpenApiKeyNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MerchantTemplateEmailService templateEmailService;
    private final MerchantRuntimeProfileCacheService merchantProfileService;

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
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

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
            log.warn("merchant portal key notice send failed, merchantId: {}, templateCode: {}, exceptionType: {}",
                    merchant.getMerchantId(), templateCode, exception.getClass().getSimpleName());
        }
    }

    private String keyName(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            return "JWT 签名密钥";
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            return "平台请求体密钥";
        }
        return "商户响应密钥";
    }

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

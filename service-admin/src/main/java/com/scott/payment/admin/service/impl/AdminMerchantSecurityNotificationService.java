package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantSecurityNotificationService
 * @date : 2026-08-05 00:00
 * @email : scott_x@163.com
 * @description : 管理端商户密钥生命周期通知服务，仅向邮件模板传递密钥类型、操作信息和不可逆指纹尾号
 * @status : create
 */
@Slf4j
@Service
public class AdminMerchantSecurityNotificationService {

    /**
     * 模板创建，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TEMPLATE_CREATED = "API_KEY_CREATED";
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
     * 场景常量，统一 {@code AdminMerchantSecurityNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SCENE = "API_KEY_CHANGED";
    /**
     * 时间格式化器常量，统一 {@code AdminMerchantSecurityNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminEmailService emailService;

    public AdminMerchantSecurityNotificationService(AdminEmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 在当前事务提交后发送密钥变更通知，避免密钥事务回滚时产生错误通知。
     *
     * @param merchant 商户资料及通知邮箱
     * @param templateCode 密钥生命周期模板代码
     * @param keyName 可展示的密钥类型名称
     * @param fingerprintSource 仅用于本地计算不可逆指纹尾号的密钥材料
     */
    public void sendAfterCommit(BaseMerchantInfoDO merchant,
                                String templateCode,
                                String keyName,
                                String fingerprintSource) {
        String fingerprintLast4 = fingerprintLast4(fingerprintSource);
        Runnable task = () -> send(merchant, templateCode, keyName, fingerprintLast4);
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

    private void send(BaseMerchantInfoDO merchant,
                      String templateCode,
                      String keyName,
                      String keyLast4) {
        if (merchant == null || !StringUtils.hasText(merchant.getContactEmail())) {
            return;
        }
        try {
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_MERCHANT);
            request.setMerchantId(merchant.getMerchantId());
            request.setMerchantNo(merchant.getMerchantId());
            request.setMerchantName(merchant.getMerchantName());
            request.setTemplateCode(templateCode);
            request.setSceneCode(SCENE);
            request.setLocale(MerchantLocaleSupport.normalize(merchant.getDefaultLocale()));
            request.setToEmails(List.of(merchant.getContactEmail()));
            request.setBizType(SCENE);
            request.setBizNo(merchant.getMerchantId());
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("merchantName", merchant.getMerchantName());
            variables.put("merchantNo", merchant.getMerchantId());
            variables.put("keyName", keyName);
            variables.put("keyLast4", keyLast4);
            variables.put("operatorName", operatorName());
            variables.put("operationTime", TIME_FORMATTER.format(LocalDateTime.now()));
            request.setVariables(variables);
            emailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("merchant key lifecycle notice send failed, merchantId: {}, templateCode: {}, exceptionType: {}",
                    merchant.getMerchantId(), templateCode, exception.getClass().getSimpleName());
        }
    }

    private String fingerprintLast4(String source) {
        if (!StringUtils.hasText(source)) {
            return "----";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            String fingerprint = HexFormat.of().formatHex(digest).toUpperCase();
            return fingerprint.substring(fingerprint.length() - 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String operatorName() {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null) {
            return "System Administrator";
        }
        if (StringUtils.hasText(operator.getRealName())) {
            return operator.getRealName();
        }
        return StringUtils.hasText(operator.getLoginAccount()) ? operator.getLoginAccount() : "System Administrator";
    }
}

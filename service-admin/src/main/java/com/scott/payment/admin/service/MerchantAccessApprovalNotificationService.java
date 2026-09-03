package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.support.approval.MerchantAccessApprovalStatus;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
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
 * @classname : MerchantAccessApprovalNotificationService
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 商户来源网址和 IP 白名单审批邮件服务，在审批事务提交后通知商户联系人，邮件异常不影响审批结果。
 * @status : create
 */
@Slf4j
@Service
public class MerchantAccessApprovalNotificationService {

    /**
     * 类型来源URL，用于区分 {@code MerchantAccessApprovalNotificationService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TYPE_SOURCE_URL = "SOURCE_URL";
    /**
     * {@code TYPE_IP_WHITELIST}，用于区分 {@code MerchantAccessApprovalNotificationService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TYPE_IP_WHITELIST = "IP_WHITELIST";
    /**
     * 场景常量，统一 {@code MerchantAccessApprovalNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SCENE = "MERCHANT_ACCESS_CONFIG_APPROVAL";
    /**
     * 时间格式化器常量，统一 {@code MerchantAccessApprovalNotificationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminEmailService emailService;

    /**
     * 创建审批邮件服务。
     *
     * @param emailService 邮件领域服务
     */
    public MerchantAccessApprovalNotificationService(AdminEmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 注册事务提交后的审批结果邮件；商户无联系邮箱时跳过。
     *
     * @param merchant          商户资料
     * @param configType       SOURCE_URL 或 IP_WHITELIST
     * @param configValue      审批配置值
     * @param approvalStatus   审批终态
     * @param transactionStatus 交易状态，1 允许、0 禁止
     * @param rejectReason     拒绝原因
     * @param reviewTime       审核时间
     */
    public void sendAfterCommit(BaseMerchantInfoDO merchant,
                                String configType,
                                String configValue,
                                MerchantAccessApprovalStatus approvalStatus,
                                int transactionStatus,
                                String rejectReason,
                                LocalDateTime reviewTime) {
        if (merchant == null || !StringUtils.hasText(merchant.getContactEmail())) {
            return;
        }
        Runnable task = () -> send(merchant, configType, configValue, approvalStatus,
                transactionStatus, rejectReason, reviewTime);
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
                      String configType,
                      String configValue,
                      MerchantAccessApprovalStatus approvalStatus,
                      int transactionStatus,
                      String rejectReason,
                      LocalDateTime reviewTime) {
        String templateCode = templateCode(configType, approvalStatus);
        try {
            String locale = MerchantLocaleSupport.normalize(merchant.getDefaultLocale());
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_MERCHANT);
            request.setMerchantId(merchant.getMerchantId());
            request.setMerchantNo(merchant.getMerchantId());
            request.setMerchantName(merchant.getMerchantName());
            request.setTemplateCode(templateCode);
            request.setSceneCode(SCENE);
            request.setLocale(locale);
            request.setToEmails(List.of(merchant.getContactEmail().trim()));
            request.setBizType(SCENE);
            request.setBizNo(configType + ":" + merchant.getMerchantId());
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("merchantName", merchant.getMerchantName());
            variables.put("merchantId", merchant.getMerchantId());
            variables.put("configValue", configValue);
            boolean chinese = locale != null && locale.toLowerCase().startsWith("zh");
            variables.put("transactionStatusText", transactionStatus == 1
                    ? (chinese ? "允许交易" : "Allowed")
                    : (chinese ? "禁止交易" : "Prohibited"));
            variables.put("reviewTime", TIME_FORMATTER.format(reviewTime));
            variables.put("rejectReason", rejectReason == null ? "" : rejectReason);
            request.setVariables(variables);
            emailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("merchant access approval email failed, merchantId: {}, configType: {}, templateCode: {}, exceptionType: {}",
                    merchant.getMerchantId(), configType, templateCode, exception.getClass().getSimpleName());
        }
    }

    private String templateCode(String configType, MerchantAccessApprovalStatus approvalStatus) {
        String prefix = TYPE_SOURCE_URL.equals(configType)
                ? "MERCHANT_SOURCE_URL" : "MERCHANT_IP_WHITELIST";
        return prefix + (approvalStatus == MerchantAccessApprovalStatus.APPROVED ? "_APPROVED" : "_REJECTED");
    }
}

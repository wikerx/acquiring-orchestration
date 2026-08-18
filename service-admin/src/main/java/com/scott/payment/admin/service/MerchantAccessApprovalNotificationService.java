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
 * @description : 商户来源网址和 IP 白名单审批邮件服务，在审批事务提交后通知商户联系人，邮件异常不影响审批结果。
 * @status : create
 */
@Slf4j
@Service
public class MerchantAccessApprovalNotificationService {

    public static final String TYPE_SOURCE_URL = "SOURCE_URL";
    public static final String TYPE_IP_WHITELIST = "IP_WHITELIST";
    private static final String SCENE = "MERCHANT_ACCESS_CONFIG_APPROVAL";
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

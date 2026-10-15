package com.scott.payment.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailPreparationFailureRecorder
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 邮件准备失败记录器；在独立事务中保存脱敏的 CLOSED 记录，避免原准备事务回滚后管理端无迹可查。
 * @status : create
 */
@Service
public class AdminEmailPreparationFailureRecorder {

    private static final long NOT_DELETED = 0L;
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String DEFAULT_APP = "COMMON";
    private static final String DEFAULT_SCENE = "COMMON";

    private final EmailSendRecordMapper recordMapper;

    /** 创建邮件准备失败记录器。 */
    public AdminEmailPreparationFailureRecorder(EmailSendRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 使用独立事务保存最小失败快照；不保存模板变量、验证码、密码或原始异常消息。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public EmailSendResult record(EmailSendRequest request, String errorCode) {
        EmailSendRequest safeRequest = request == null ? new EmailSendRequest() : request;
        LocalDateTime now = LocalDateTime.now();
        EmailSendRecordDO row = new EmailSendRecordDO();
        row.setEmailNo(generateEmailNo(now));
        row.setAppCode(upperOrDefault(safeRequest.getAppCode(), DEFAULT_APP));
        row.setMerchantId(trim(safeRequest.getMerchantId()));
        row.setMerchantNo(trim(safeRequest.getMerchantNo()));
        row.setMerchantName(trim(safeRequest.getMerchantName()));
        row.setSceneCode(upperOrDefault(safeRequest.getSceneCode(), DEFAULT_SCENE));
        row.setTemplateCode(trimUpper(safeRequest.getTemplateCode()));
        row.setLocale(defaultIfBlank(safeRequest.getLocale(), DEFAULT_LOCALE));
        row.setToEmails(JSON.toJSONString(defaultList(safeRequest.getToEmails())));
        row.setCcEmails(JSON.toJSONString(defaultList(safeRequest.getCcEmails())));
        row.setBccEmails(JSON.toJSONString(defaultList(safeRequest.getBccEmails())));
        row.setSubject("Email preparation failed" + (StringUtils.hasText(row.getTemplateCode())
                ? ": " + row.getTemplateCode() : ""));
        row.setBizType(trimUpper(safeRequest.getBizType()));
        row.setBizNo(trim(safeRequest.getBizNo()));
        row.setSendStatus(EmailDeliveryStatus.CLOSED.getCode());
        row.setRetryCount(0);
        row.setMaxRetryCount(0);
        row.setErrorCode(defaultIfBlank(errorCode, "EMAIL_PREPARATION_FAILED"));
        row.setErrorMessage("Email preparation failed. Check template and sender account configuration.");
        fillOperator(row);
        row.setCreateBy(row.getOperatorName());
        row.setUpdateBy(row.getOperatorName());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(NOT_DELETED);
        recordMapper.insert(row);

        EmailSendResult result = new EmailSendResult();
        result.setRecordId(row.getId());
        result.setEmailNo(row.getEmailNo());
        result.setSendStatus(row.getSendStatus());
        result.setErrorCode(row.getErrorCode());
        result.setErrorMessage(row.getErrorMessage());
        return result;
    }

    private void fillOperator(EmailSendRecordDO row) {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null) {
            row.setOperatorName("system");
            return;
        }
        row.setOperatorId(operator.getAccountId());
        row.setOperatorName(StringUtils.hasText(operator.getLoginAccount())
                ? operator.getLoginAccount() : "system");
    }

    private String generateEmailNo(LocalDateTime now) {
        return "EMAIL_FAIL_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private String upperOrDefault(String value, String defaultValue) {
        String normalized = trimUpper(value);
        return StringUtils.hasText(normalized) ? normalized : defaultValue;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized) ? normalized : defaultValue;
    }

    private String trimUpper(String value) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> defaultList(List<String> value) {
        return value == null ? List.of() : value;
    }
}

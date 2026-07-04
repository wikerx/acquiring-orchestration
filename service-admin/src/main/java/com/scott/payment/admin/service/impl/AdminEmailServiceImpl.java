package com.scott.payment.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountSaveRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountTestRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.admin.entity.email.EmailEntities.EmailAccountDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailTemplateDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理后台邮件管理服务实现。
 *
 * <p>负责发件账户、邮件模板、发送记录和 SMTP 同步发送。SMTP 密码仅以 AES-GCM 密文保存，编辑时为空表示沿用原密文。</p>
 */
@Service
public class AdminEmailServiceImpl implements AdminEmailService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int YES = 1;
    private static final int NO = 0;
    private static final int VERIFY_UNVERIFIED = 0;
    private static final int VERIFY_SUCCESS = 1;
    private static final int VERIFY_FAILED = 2;
    private static final int SEND_SENDING = 1;
    private static final int SEND_SUCCESS = 2;
    private static final int SEND_FAILED = 3;
    private static final String COMMON_SCENE = "COMMON";
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SCOPE_MERCHANT = "MERCHANT";
    private static final String SMTP_PROVIDER = "SMTP";
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String CONTENT_HTML = "HTML";
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailAccountMapper accountMapper;
    private final EmailTemplateMapper templateMapper;
    private final EmailSendRecordMapper recordMapper;

    public AdminEmailServiceImpl(EmailAccountMapper accountMapper,
                                 EmailTemplateMapper templateMapper,
                                 EmailSendRecordMapper recordMapper) {
        this.accountMapper = accountMapper;
        this.templateMapper = templateMapper;
        this.recordMapper = recordMapper;
    }

    @Override
    public PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query) {
        EmailAccountQuery safeQuery = query == null ? new EmailAccountQuery() : query;
        Page<EmailAccountDO> page = accountMapper.selectPage(
                new Page<>(safeQuery.safePageNo(), safeQuery.safePageSize()),
                accountQueryWrapper(safeQuery)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toAccountResponse).toList());
    }

    @Override
    public EmailAccountResponse getAccount(Long id) {
        return toAccountResponse(requireAccount(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailAccountResponse createAccount(EmailAccountSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        EmailAccountDO row = new EmailAccountDO();
        row.setAccountCode(generateCode("EMAIL_ACC"));
        fillAccount(row, request, true, now);
        row.setCreateBy(currentOperatorName());
        row.setCreateTime(now);
        row.setDeleted(NOT_DELETED);
        ensureAccountCodeUnique(row.getAccountCode(), null);
        if (row.getDefaultFlag() == YES) {
            clearDefaultAccount(row, null);
        }
        accountMapper.insert(row);
        return toAccountResponse(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request) {
        EmailAccountDO row = requireAccount(id);
        fillAccount(row, request, false, LocalDateTime.now());
        if (row.getDefaultFlag() == YES) {
            clearDefaultAccount(row, id);
        }
        accountMapper.updateById(row);
        return toAccountResponse(row);
    }

    @Override
    public EmailAccountResponse updateAccountStatus(Long id, Integer status) {
        EmailAccountDO row = requireAccount(id);
        row.setStatus(normalizeStatus(status));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(row);
        return toAccountResponse(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailAccountResponse setDefaultAccount(Long id) {
        EmailAccountDO row = requireAccount(id);
        row.setDefaultFlag(YES);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        clearDefaultAccount(row, id);
        accountMapper.updateById(row);
        return toAccountResponse(row);
    }

    @Override
    public void deleteAccount(Long id) {
        EmailAccountDO row = requireAccount(id);
        row.setDeleted(row.getId());
        row.setDefaultFlag(NO);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(row);
    }

    @Override
    public EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request) {
        EmailAccountDO account = requireAccount(accountId);
        LocalDateTime now = LocalDateTime.now();
        EmailSendRecordDO record = new EmailSendRecordDO();
        record.setEmailNo(generateCode("EMAIL"));
        record.setAppCode(account.getAppCode());
        record.setMerchantId(account.getMerchantId());
        record.setMerchantNo(account.getMerchantNo());
        record.setMerchantName(account.getMerchantName());
        record.setSceneCode(COMMON_SCENE);
        record.setLocale(DEFAULT_LOCALE);
        fillAccountSnapshot(record, account);
        record.setToEmails(JSON.toJSONString(List.of(trim(request.getToEmail()))));
        record.setSubject(defaultIfBlank(trim(request.getSubject()), "Vexra 邮件服务测试"));
        record.setContentSnapshot(defaultIfBlank(trim(request.getContent()), "这是一封邮件服务测试邮件。"));
        record.setBizType("EMAIL_TEST");
        record.setBizNo(account.getAccountCode());
        record.setSendStatus(SEND_SENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(0);
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        recordMapper.insert(record);
        EmailSendResult result = doSend(record, account, record.getContentSnapshot(), true);
        account.setVerifyStatus(result.getSendStatus() == SEND_SUCCESS ? VERIFY_SUCCESS : VERIFY_FAILED);
        account.setLastTestTime(LocalDateTime.now());
        account.setLastErrorMessage(result.getSendStatus() == SEND_SUCCESS ? null : result.getErrorMessage());
        account.setUpdateBy(currentOperatorName());
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return result;
    }

    @Override
    public PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query) {
        EmailTemplateQuery safeQuery = query == null ? new EmailTemplateQuery() : query;
        Page<EmailTemplateDO> page = templateMapper.selectPage(
                new Page<>(safeQuery.safePageNo(), safeQuery.safePageSize()),
                templateQueryWrapper(safeQuery)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toTemplateResponse).toList());
    }

    @Override
    public EmailTemplateResponse getTemplate(Long id) {
        return toTemplateResponse(requireTemplate(id));
    }

    @Override
    public EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        EmailTemplateDO row = new EmailTemplateDO();
        fillTemplate(row, request, now);
        row.setSystemBuiltin(NO);
        row.setVersionNo(1);
        row.setCreateBy(currentOperatorName());
        row.setCreateTime(now);
        row.setDeleted(NOT_DELETED);
        ensureTemplateUnique(row.getTemplateCode(), row.getLocale(), null);
        templateMapper.insert(row);
        return toTemplateResponse(row);
    }

    @Override
    public EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request) {
        EmailTemplateDO row = requireTemplate(id);
        String oldCode = row.getTemplateCode();
        String oldLocale = row.getLocale();
        fillTemplate(row, request, LocalDateTime.now());
        if (!oldCode.equals(row.getTemplateCode()) || !oldLocale.equals(row.getLocale())) {
            ensureTemplateUnique(row.getTemplateCode(), row.getLocale(), id);
        }
        row.setVersionNo(defaultIfNull(row.getVersionNo(), 1) + 1);
        templateMapper.updateById(row);
        return toTemplateResponse(row);
    }

    @Override
    public EmailTemplateResponse copyTemplate(Long id) {
        EmailTemplateDO source = requireTemplate(id);
        EmailTemplateDO row = new EmailTemplateDO();
        row.setTemplateCode(source.getTemplateCode() + "_COPY_" + System.currentTimeMillis());
        row.setTemplateName(source.getTemplateName() + " Copy");
        row.setAppCode(source.getAppCode());
        row.setSceneCode(source.getSceneCode());
        row.setLocale(source.getLocale());
        row.setSubjectTemplate(source.getSubjectTemplate());
        row.setContentType(source.getContentType());
        row.setContentTemplate(source.getContentTemplate());
        row.setVariableSchema(source.getVariableSchema());
        row.setSensitiveVariableNames(source.getSensitiveVariableNames());
        row.setStatus(DISABLED);
        row.setSystemBuiltin(NO);
        row.setVersionNo(1);
        row.setRemark(source.getRemark());
        row.setCreateBy(currentOperatorName());
        row.setUpdateBy(currentOperatorName());
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleted(NOT_DELETED);
        templateMapper.insert(row);
        return toTemplateResponse(row);
    }

    @Override
    public EmailTemplateResponse updateTemplateStatus(Long id, Integer status) {
        EmailTemplateDO row = requireTemplate(id);
        row.setStatus(normalizeStatus(status));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(row);
        return toTemplateResponse(row);
    }

    @Override
    public void deleteTemplate(Long id) {
        EmailTemplateDO row = requireTemplate(id);
        row.setDeleted(row.getId());
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(row);
    }

    @Override
    public EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request) {
        Set<String> missing = missingVariables(request.getSubjectTemplate() + request.getContentTemplate(), request.getVariables());
        EmailTemplatePreviewResponse response = new EmailTemplatePreviewResponse();
        response.getMissingVariables().addAll(missing);
        if (missing.isEmpty()) {
            response.setSubject(render(request.getSubjectTemplate(), request.getVariables()));
            response.setContent(render(request.getContentTemplate(), request.getVariables()));
            response.setMaskedContent(maskSensitiveContent(request.getContentTemplate(), request.getVariables(), parseStringList(request.getSensitiveVariableNames())));
        }
        return response;
    }

    @Override
    public PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query) {
        EmailRecordQuery safeQuery = query == null ? new EmailRecordQuery() : query;
        Page<EmailSendRecordDO> page = recordMapper.selectPage(
                new Page<>(safeQuery.safePageNo(), safeQuery.safePageSize()),
                recordQueryWrapper(safeQuery)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRecordResponse).toList());
    }

    @Override
    public EmailRecordResponse getRecord(Long id) {
        return toRecordResponse(requireRecord(id));
    }

    @Override
    public EmailSendResult sendByTemplate(EmailSendRequest request) {
        EmailTemplateDO template = requireEnabledTemplate(request.getTemplateCode(), defaultIfBlank(request.getLocale(), DEFAULT_LOCALE));
        Map<String, Object> variables = request.getVariables() == null ? new LinkedHashMap<>() : request.getVariables();
        Set<String> missing = missingVariables(template.getSubjectTemplate() + template.getContentTemplate(), variables);
        EmailAccountDO account = selectAccount(request.getAppCode(), request.getMerchantId(), defaultIfBlank(request.getSceneCode(), template.getSceneCode()));
        EmailSendRecordDO record = buildRecord(request, template, account);
        if (!missing.isEmpty()) {
            record.setSubject(template.getSubjectTemplate());
            record.setContentSnapshot("模板变量缺失：" + String.join(",", missing));
            record.setSendStatus(SEND_FAILED);
            record.setErrorCode("EMAIL_VARIABLE_MISSING");
            record.setErrorMessage("模板变量缺失：" + String.join(",", missing));
            recordMapper.insert(record);
            return toSendResult(record);
        }
        String content = render(template.getContentTemplate(), variables);
        record.setSubject(render(template.getSubjectTemplate(), variables));
        record.setContentSnapshot(maskSensitiveContent(template.getContentTemplate(), variables, parseStringList(template.getSensitiveVariableNames())));
        record.setVariablesSnapshot(JSON.toJSONString(maskVariables(variables, parseStringList(template.getSensitiveVariableNames()))));
        recordMapper.insert(record);
        return doSend(record, account, content, CONTENT_HTML.equalsIgnoreCase(template.getContentType()));
    }

    @Override
    public EmailSendResult resend(Long id) {
        EmailSendRecordDO source = requireRecord(id);
        if (source.getSendStatus() == SEND_SUCCESS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "发送成功的邮件不允许重新发送");
        }
        if ("LOGIN_OTP".equals(source.getSceneCode()) || "PASSWORD_RESET".equals(source.getSceneCode())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "验证码和找回密码邮件请通过原业务流程重新发送");
        }
        EmailAccountDO account = requireAccount(source.getAccountId());
        EmailSendRecordDO record = copyRetryRecord(source);
        recordMapper.insert(record);
        return doSend(record, account, source.getContentSnapshot(), true);
    }

    private LambdaQueryWrapper<EmailAccountDO> accountQueryWrapper(EmailAccountQuery query) {
        return Wrappers.<EmailAccountDO>lambdaQuery()
                .eq(EmailAccountDO::getDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getAccountName()), EmailAccountDO::getAccountName, trim(query.getAccountName()))
                .eq(StringUtils.hasText(query.getAppCode()), EmailAccountDO::getAppCode, trimUpper(query.getAppCode()))
                .eq(StringUtils.hasText(query.getScopeType()), EmailAccountDO::getScopeType, trimUpper(query.getScopeType()))
                .like(StringUtils.hasText(query.getMerchantId()), EmailAccountDO::getMerchantId, trim(query.getMerchantId()))
                .like(StringUtils.hasText(query.getMerchantName()), EmailAccountDO::getMerchantName, trim(query.getMerchantName()))
                .like(StringUtils.hasText(query.getFromEmail()), EmailAccountDO::getFromEmail, trim(query.getFromEmail()))
                .eq(StringUtils.hasText(query.getSceneCode()), EmailAccountDO::getSceneCode, trimUpper(query.getSceneCode()))
                .eq(query.getStatus() != null, EmailAccountDO::getStatus, query.getStatus())
                .eq(query.getVerifyStatus() != null, EmailAccountDO::getVerifyStatus, query.getVerifyStatus())
                .ge(query.getCreateStartTime() != null, EmailAccountDO::getCreateTime, query.getCreateStartTime())
                .le(query.getCreateEndTime() != null, EmailAccountDO::getCreateTime, query.getCreateEndTime())
                .orderByAsc(EmailAccountDO::getSortOrder)
                .orderByDesc(EmailAccountDO::getUpdateTime);
    }

    private LambdaQueryWrapper<EmailTemplateDO> templateQueryWrapper(EmailTemplateQuery query) {
        return Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getTemplateName()), EmailTemplateDO::getTemplateName, trim(query.getTemplateName()))
                .like(StringUtils.hasText(query.getTemplateCode()), EmailTemplateDO::getTemplateCode, trimUpper(query.getTemplateCode()))
                .eq(StringUtils.hasText(query.getAppCode()), EmailTemplateDO::getAppCode, trimUpper(query.getAppCode()))
                .eq(StringUtils.hasText(query.getSceneCode()), EmailTemplateDO::getSceneCode, trimUpper(query.getSceneCode()))
                .eq(StringUtils.hasText(query.getLocale()), EmailTemplateDO::getLocale, trim(query.getLocale()))
                .eq(query.getStatus() != null, EmailTemplateDO::getStatus, query.getStatus())
                .eq(query.getSystemBuiltin() != null, EmailTemplateDO::getSystemBuiltin, query.getSystemBuiltin())
                .orderByDesc(EmailTemplateDO::getUpdateTime);
    }

    private LambdaQueryWrapper<EmailSendRecordDO> recordQueryWrapper(EmailRecordQuery query) {
        return Wrappers.<EmailSendRecordDO>lambdaQuery()
                .eq(EmailSendRecordDO::getDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getEmailNo()), EmailSendRecordDO::getEmailNo, trim(query.getEmailNo()))
                .eq(StringUtils.hasText(query.getAppCode()), EmailSendRecordDO::getAppCode, trimUpper(query.getAppCode()))
                .like(StringUtils.hasText(query.getMerchantId()), EmailSendRecordDO::getMerchantId, trim(query.getMerchantId()))
                .like(StringUtils.hasText(query.getMerchantName()), EmailSendRecordDO::getMerchantName, trim(query.getMerchantName()))
                .eq(StringUtils.hasText(query.getSceneCode()), EmailSendRecordDO::getSceneCode, trimUpper(query.getSceneCode()))
                .like(StringUtils.hasText(query.getTemplateCode()), EmailSendRecordDO::getTemplateCode, trimUpper(query.getTemplateCode()))
                .like(StringUtils.hasText(query.getToEmail()), EmailSendRecordDO::getToEmails, trim(query.getToEmail()))
                .eq(query.getSendStatus() != null, EmailSendRecordDO::getSendStatus, query.getSendStatus())
                .like(StringUtils.hasText(query.getBizNo()), EmailSendRecordDO::getBizNo, trim(query.getBizNo()))
                .ge(query.getCreateStartTime() != null, EmailSendRecordDO::getCreateTime, query.getCreateStartTime())
                .le(query.getCreateEndTime() != null, EmailSendRecordDO::getCreateTime, query.getCreateEndTime())
                .ge(query.getSendStartTime() != null, EmailSendRecordDO::getSendSuccessTime, query.getSendStartTime())
                .le(query.getSendEndTime() != null, EmailSendRecordDO::getSendSuccessTime, query.getSendEndTime())
                .orderByDesc(EmailSendRecordDO::getCreateTime);
    }

    private void fillAccount(EmailAccountDO row, EmailAccountSaveRequest request, boolean create, LocalDateTime now) {
        row.setAccountName(trim(request.getAccountName()));
        row.setAppCode(trimUpper(request.getAppCode()));
        row.setScopeType(trimUpper(request.getScopeType()));
        row.setMerchantId(trim(request.getMerchantId()));
        row.setMerchantNo(defaultIfBlank(trim(request.getMerchantNo()), row.getMerchantId()));
        row.setMerchantName(trim(request.getMerchantName()));
        row.setSceneCode(defaultIfBlank(trimUpper(request.getSceneCode()), COMMON_SCENE));
        row.setProviderType(defaultIfBlank(trimUpper(request.getProviderType()), SMTP_PROVIDER));
        row.setFromName(trim(request.getFromName()));
        row.setFromEmail(trim(request.getFromEmail()));
        row.setReplyToEmail(trim(request.getReplyToEmail()));
        row.setSmtpHost(trim(request.getSmtpHost()));
        row.setSmtpPort(request.getSmtpPort());
        row.setEncryptionType(defaultIfBlank(trimUpper(request.getEncryptionType()), "SSL"));
        row.setSmtpAuthRequired(defaultIfNull(request.getSmtpAuthRequired(), YES));
        row.setSmtpUsername(trim(request.getSmtpUsername()));
        if (StringUtils.hasText(request.getSmtpPassword())) {
            row.setSmtpPasswordCipher(encryptSecret(request.getSmtpPassword()));
            row.setPasswordUpdatedTime(now);
        } else if (create) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "smtpPassword is required");
        }
        row.setConnectTimeoutMs(defaultIfNull(request.getConnectTimeoutMs(), 10000));
        row.setReadTimeoutMs(defaultIfNull(request.getReadTimeoutMs(), 30000));
        row.setDefaultFlag(defaultIfNull(request.getDefaultFlag(), NO));
        row.setStatus(defaultIfNull(request.getStatus(), ENABLED));
        row.setVerifyStatus(defaultIfNull(row.getVerifyStatus(), VERIFY_UNVERIFIED));
        row.setMinuteLimit(defaultIfNull(request.getMinuteLimit(), 60));
        row.setDailyLimit(defaultIfNull(request.getDailyLimit(), 10000));
        row.setRemark(trim(request.getRemark()));
        row.setSortOrder(defaultIfNull(request.getSortOrder(), 0));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(now);
        validateAccountScope(row);
    }

    private void validateAccountScope(EmailAccountDO row) {
        if (SCOPE_MERCHANT.equals(row.getScopeType()) && !StringUtils.hasText(row.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchantId is required");
        }
        if (SCOPE_SYSTEM.equals(row.getScopeType())) {
            row.setMerchantId(null);
            row.setMerchantNo(null);
            row.setMerchantName(null);
        }
    }

    private void fillTemplate(EmailTemplateDO row, EmailTemplateSaveRequest request, LocalDateTime now) {
        row.setTemplateCode(trimUpper(request.getTemplateCode()));
        row.setTemplateName(trim(request.getTemplateName()));
        row.setAppCode(trimUpper(request.getAppCode()));
        row.setSceneCode(trimUpper(request.getSceneCode()));
        row.setLocale(defaultIfBlank(trim(request.getLocale()), DEFAULT_LOCALE));
        row.setSubjectTemplate(trim(request.getSubjectTemplate()));
        row.setContentType(defaultIfBlank(trimUpper(request.getContentType()), CONTENT_HTML));
        row.setContentTemplate(request.getContentTemplate());
        row.setVariableSchema(trim(request.getVariableSchema()));
        row.setSensitiveVariableNames(normalizeJsonArray(request.getSensitiveVariableNames()));
        row.setStatus(defaultIfNull(request.getStatus(), ENABLED));
        row.setRemark(trim(request.getRemark()));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(now);
    }

    private EmailSendRecordDO buildRecord(EmailSendRequest request, EmailTemplateDO template, EmailAccountDO account) {
        LocalDateTime now = LocalDateTime.now();
        EmailSendRecordDO record = new EmailSendRecordDO();
        record.setEmailNo(generateCode("EMAIL"));
        record.setAppCode(trimUpper(request.getAppCode()));
        record.setMerchantId(trim(request.getMerchantId()));
        record.setMerchantNo(defaultIfBlank(trim(request.getMerchantNo()), record.getMerchantId()));
        record.setMerchantName(trim(request.getMerchantName()));
        record.setSceneCode(defaultIfBlank(trimUpper(request.getSceneCode()), template.getSceneCode()));
        record.setTemplateCode(template.getTemplateCode());
        record.setTemplateName(template.getTemplateName());
        record.setLocale(template.getLocale());
        fillAccountSnapshot(record, account);
        record.setToEmails(JSON.toJSONString(request.getToEmails()));
        record.setCcEmails(JSON.toJSONString(defaultList(request.getCcEmails())));
        record.setBccEmails(JSON.toJSONString(defaultList(request.getBccEmails())));
        record.setBizType(trimUpper(request.getBizType()));
        record.setBizNo(trim(request.getBizNo()));
        record.setSendStatus(SEND_SENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(defaultIfNull(request.getMaxRetryCount(), 0));
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        return record;
    }

    private void fillAccountSnapshot(EmailSendRecordDO record, EmailAccountDO account) {
        record.setAccountId(account.getId());
        record.setAccountCode(account.getAccountCode());
        record.setProviderType(account.getProviderType());
        record.setFromName(account.getFromName());
        record.setFromEmail(account.getFromEmail());
        record.setReplyToEmail(account.getReplyToEmail());
    }

    private EmailSendResult doSend(EmailSendRecordDO record, EmailAccountDO account, String content, boolean html) {
        LocalDateTime start = LocalDateTime.now();
        record.setSendStartTime(start);
        record.setSendStatus(SEND_SENDING);
        recordMapper.updateById(record);
        try {
            JavaMailSenderImpl sender = buildMailSender(account);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(account.getFromEmail(), account.getFromName());
            if (StringUtils.hasText(account.getReplyToEmail())) {
                helper.setReplyTo(account.getReplyToEmail());
            }
            helper.setTo(parseEmailArray(record.getToEmails()));
            String[] cc = parseEmailArray(record.getCcEmails());
            if (cc.length > 0) {
                helper.setCc(cc);
            }
            String[] bcc = parseEmailArray(record.getBccEmails());
            if (bcc.length > 0) {
                helper.setBcc(bcc);
            }
            helper.setSubject(record.getSubject());
            helper.setText(content, html);
            sender.send(message);
            record.setSendStatus(SEND_SUCCESS);
            record.setSendEndTime(LocalDateTime.now());
            record.setSendSuccessTime(record.getSendEndTime());
            record.setCostMs(Duration.between(start, record.getSendEndTime()).toMillis());
            record.setErrorCode(null);
            record.setErrorMessage(null);
        } catch (Exception ex) {
            record.setSendStatus(SEND_FAILED);
            record.setSendEndTime(LocalDateTime.now());
            record.setCostMs(Duration.between(start, record.getSendEndTime()).toMillis());
            record.setErrorCode("EMAIL_SEND_FAILED");
            record.setErrorMessage(truncate(ex.getMessage(), 1800));
        }
        record.setUpdateBy(currentOperatorName());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return toSendResult(record);
    }

    private JavaMailSenderImpl buildMailSender(EmailAccountDO account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getSmtpHost());
        sender.setPort(account.getSmtpPort());
        sender.setUsername(account.getSmtpUsername());
        if (account.getSmtpAuthRequired() == YES) {
            sender.setPassword(decryptSecret(account.getSmtpPasswordCipher()));
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(account.getSmtpAuthRequired() == YES));
        props.put("mail.smtp.connectiontimeout", String.valueOf(account.getConnectTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(account.getReadTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(account.getReadTimeoutMs()));
        if ("SSL".equals(account.getEncryptionType()) || "TLS".equals(account.getEncryptionType())) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(account.getEncryptionType())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    private EmailAccountDO selectAccount(String appCode, String merchantId, String sceneCode) {
        List<LambdaQueryWrapper<EmailAccountDO>> candidates = new ArrayList<>();
        if (StringUtils.hasText(merchantId)) {
            candidates.add(accountRouteWrapper(appCode, SCOPE_MERCHANT, merchantId, sceneCode));
            candidates.add(accountRouteWrapper(appCode, SCOPE_MERCHANT, merchantId, COMMON_SCENE));
        }
        candidates.add(accountRouteWrapper(appCode, SCOPE_SYSTEM, null, sceneCode));
        candidates.add(accountRouteWrapper(appCode, SCOPE_SYSTEM, null, COMMON_SCENE));
        for (LambdaQueryWrapper<EmailAccountDO> wrapper : candidates) {
            EmailAccountDO account = accountMapper.selectOne(wrapper.last("LIMIT 1"));
            if (account != null) {
                return account;
            }
        }
        throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "未找到可用发件账户");
    }

    private LambdaQueryWrapper<EmailAccountDO> accountRouteWrapper(String appCode, String scopeType, String merchantId, String sceneCode) {
        return Wrappers.<EmailAccountDO>lambdaQuery()
                .eq(EmailAccountDO::getDeleted, NOT_DELETED)
                .eq(EmailAccountDO::getStatus, ENABLED)
                .eq(EmailAccountDO::getDefaultFlag, YES)
                .eq(EmailAccountDO::getAppCode, trimUpper(appCode))
                .eq(EmailAccountDO::getScopeType, scopeType)
                .eq(StringUtils.hasText(merchantId), EmailAccountDO::getMerchantId, trim(merchantId))
                .eq(EmailAccountDO::getSceneCode, defaultIfBlank(trimUpper(sceneCode), COMMON_SCENE))
                .orderByDesc(EmailAccountDO::getUpdateTime);
    }

    private void clearDefaultAccount(EmailAccountDO row, Long excludeId) {
        accountMapper.update(null, Wrappers.<EmailAccountDO>lambdaUpdate()
                .eq(EmailAccountDO::getDeleted, NOT_DELETED)
                .eq(EmailAccountDO::getAppCode, row.getAppCode())
                .eq(EmailAccountDO::getScopeType, row.getScopeType())
                .eq(EmailAccountDO::getSceneCode, row.getSceneCode())
                .eq(StringUtils.hasText(row.getMerchantId()), EmailAccountDO::getMerchantId, row.getMerchantId())
                .ne(excludeId != null, EmailAccountDO::getId, excludeId)
                .set(EmailAccountDO::getDefaultFlag, NO)
                .set(EmailAccountDO::getUpdateBy, currentOperatorName())
                .set(EmailAccountDO::getUpdateTime, LocalDateTime.now()));
    }

    private Set<String> missingVariables(String template, Map<String, Object> variables) {
        Set<String> required = extractVariables(template);
        required.removeIf(key -> variables != null && variables.containsKey(key) && variables.get(key) != null);
        return required;
    }

    private Set<String> extractVariables(String template) {
        Set<String> variables = new LinkedHashSet<>();
        if (!StringUtils.hasText(template)) {
            return variables;
        }
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private String render(String template, Map<String, Object> variables) {
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String maskSensitiveContent(String template, Map<String, Object> variables, List<String> sensitiveNames) {
        if (CollectionUtils.isEmpty(sensitiveNames)) {
            return render(template, variables);
        }
        Map<String, Object> masked = new LinkedHashMap<>(variables);
        for (String name : sensitiveNames) {
            if (masked.containsKey(name)) {
                masked.put(name, "******");
            }
        }
        return render(template, masked);
    }

    private Map<String, Object> maskVariables(Map<String, Object> variables, List<String> sensitiveNames) {
        Map<String, Object> masked = new LinkedHashMap<>(variables);
        for (String name : sensitiveNames) {
            if (masked.containsKey(name)) {
                masked.put(name, "******");
            }
        }
        return masked;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeJsonArray(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            JSONArray array = JSON.parseArray(value);
            return array.toJSONString();
        } catch (Exception ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "JSON array format is invalid");
        }
    }

    private EmailSendRecordDO copyRetryRecord(EmailSendRecordDO source) {
        LocalDateTime now = LocalDateTime.now();
        EmailSendRecordDO record = new EmailSendRecordDO();
        record.setEmailNo(generateCode("EMAIL"));
        record.setAppCode(source.getAppCode());
        record.setMerchantId(source.getMerchantId());
        record.setMerchantNo(source.getMerchantNo());
        record.setMerchantName(source.getMerchantName());
        record.setSceneCode(source.getSceneCode());
        record.setTemplateCode(source.getTemplateCode());
        record.setTemplateName(source.getTemplateName());
        record.setLocale(source.getLocale());
        record.setAccountId(source.getAccountId());
        record.setAccountCode(source.getAccountCode());
        record.setProviderType(source.getProviderType());
        record.setFromName(source.getFromName());
        record.setFromEmail(source.getFromEmail());
        record.setReplyToEmail(source.getReplyToEmail());
        record.setToEmails(source.getToEmails());
        record.setCcEmails(source.getCcEmails());
        record.setBccEmails(source.getBccEmails());
        record.setSubject(source.getSubject());
        record.setContentSnapshot(source.getContentSnapshot());
        record.setVariablesSnapshot(source.getVariablesSnapshot());
        record.setBizType(source.getBizType());
        record.setBizNo(source.getBizNo());
        record.setSendStatus(SEND_SENDING);
        record.setRetryCount(defaultIfNull(source.getRetryCount(), 0) + 1);
        record.setMaxRetryCount(source.getMaxRetryCount());
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        return record;
    }

    private EmailAccountDO requireAccount(Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "id is required");
        }
        EmailAccountDO row = accountMapper.selectOne(Wrappers.<EmailAccountDO>lambdaQuery()
                .eq(EmailAccountDO::getId, id)
                .eq(EmailAccountDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "邮件发件账户不存在");
        }
        return row;
    }

    private EmailTemplateDO requireTemplate(Long id) {
        EmailTemplateDO row = templateMapper.selectOne(Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getId, id)
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "邮件模板不存在");
        }
        return row;
    }

    private EmailTemplateDO requireEnabledTemplate(String templateCode, String locale) {
        EmailTemplateDO row = templateMapper.selectOne(Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getTemplateCode, trimUpper(templateCode))
                .eq(EmailTemplateDO::getLocale, locale)
                .eq(EmailTemplateDO::getStatus, ENABLED)
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "启用邮件模板不存在");
        }
        return row;
    }

    private EmailSendRecordDO requireRecord(Long id) {
        EmailSendRecordDO row = recordMapper.selectOne(Wrappers.<EmailSendRecordDO>lambdaQuery()
                .eq(EmailSendRecordDO::getId, id)
                .eq(EmailSendRecordDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "邮件发送记录不存在");
        }
        return row;
    }

    private void ensureAccountCodeUnique(String code, Long excludeId) {
        Long count = accountMapper.selectCount(Wrappers.<EmailAccountDO>lambdaQuery()
                .eq(EmailAccountDO::getAccountCode, code)
                .eq(EmailAccountDO::getDeleted, NOT_DELETED)
                .ne(excludeId != null, EmailAccountDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "发件账户编码不能重复");
        }
    }

    private void ensureTemplateUnique(String code, String locale, Long excludeId) {
        Long count = templateMapper.selectCount(Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getTemplateCode, code)
                .eq(EmailTemplateDO::getLocale, locale)
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED)
                .ne(excludeId != null, EmailTemplateDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一语言下模板编码不能重复");
        }
    }

    private EmailAccountResponse toAccountResponse(EmailAccountDO row) {
        EmailAccountResponse response = new EmailAccountResponse();
        response.setId(row.getId());
        response.setAccountCode(row.getAccountCode());
        response.setAccountName(row.getAccountName());
        response.setAppCode(row.getAppCode());
        response.setScopeType(row.getScopeType());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantNo(row.getMerchantNo());
        response.setMerchantName(row.getMerchantName());
        response.setSceneCode(row.getSceneCode());
        response.setProviderType(row.getProviderType());
        response.setFromName(row.getFromName());
        response.setFromEmail(row.getFromEmail());
        response.setReplyToEmail(row.getReplyToEmail());
        response.setSmtpHost(row.getSmtpHost());
        response.setSmtpPort(row.getSmtpPort());
        response.setEncryptionType(row.getEncryptionType());
        response.setSmtpAuthRequired(row.getSmtpAuthRequired());
        response.setSmtpUsername(row.getSmtpUsername());
        response.setPasswordConfigured(StringUtils.hasText(row.getSmtpPasswordCipher()) ? YES : NO);
        response.setPasswordUpdatedTime(row.getPasswordUpdatedTime());
        response.setConnectTimeoutMs(row.getConnectTimeoutMs());
        response.setReadTimeoutMs(row.getReadTimeoutMs());
        response.setDefaultFlag(row.getDefaultFlag());
        response.setStatus(row.getStatus());
        response.setVerifyStatus(row.getVerifyStatus());
        response.setLastTestTime(row.getLastTestTime());
        response.setLastErrorMessage(row.getLastErrorMessage());
        response.setMinuteLimit(row.getMinuteLimit());
        response.setDailyLimit(row.getDailyLimit());
        response.setRemark(row.getRemark());
        response.setSortOrder(row.getSortOrder());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private EmailTemplateResponse toTemplateResponse(EmailTemplateDO row) {
        EmailTemplateResponse response = new EmailTemplateResponse();
        response.setId(row.getId());
        response.setTemplateCode(row.getTemplateCode());
        response.setTemplateName(row.getTemplateName());
        response.setAppCode(row.getAppCode());
        response.setSceneCode(row.getSceneCode());
        response.setLocale(row.getLocale());
        response.setSubjectTemplate(row.getSubjectTemplate());
        response.setContentType(row.getContentType());
        response.setContentTemplate(row.getContentTemplate());
        response.setVariableSchema(row.getVariableSchema());
        response.setSensitiveVariableNames(row.getSensitiveVariableNames());
        response.setStatus(row.getStatus());
        response.setSystemBuiltin(row.getSystemBuiltin());
        response.setVersionNo(row.getVersionNo());
        response.setRemark(row.getRemark());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private EmailRecordResponse toRecordResponse(EmailSendRecordDO row) {
        EmailRecordResponse response = new EmailRecordResponse();
        response.setId(row.getId());
        response.setEmailNo(row.getEmailNo());
        response.setAppCode(row.getAppCode());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantNo(row.getMerchantNo());
        response.setMerchantName(row.getMerchantName());
        response.setSceneCode(row.getSceneCode());
        response.setTemplateCode(row.getTemplateCode());
        response.setTemplateName(row.getTemplateName());
        response.setLocale(row.getLocale());
        response.setAccountId(row.getAccountId());
        response.setAccountCode(row.getAccountCode());
        response.setProviderType(row.getProviderType());
        response.setFromName(row.getFromName());
        response.setFromEmail(row.getFromEmail());
        response.setReplyToEmail(row.getReplyToEmail());
        response.setToEmails(row.getToEmails());
        response.setCcEmails(row.getCcEmails());
        response.setBccEmails(row.getBccEmails());
        response.setSubject(row.getSubject());
        response.setContentSnapshot(row.getContentSnapshot());
        response.setVariablesSnapshot(row.getVariablesSnapshot());
        response.setBizType(row.getBizType());
        response.setBizNo(row.getBizNo());
        response.setSendStatus(row.getSendStatus());
        response.setRetryCount(row.getRetryCount());
        response.setMaxRetryCount(row.getMaxRetryCount());
        response.setNextRetryTime(row.getNextRetryTime());
        response.setSendStartTime(row.getSendStartTime());
        response.setSendEndTime(row.getSendEndTime());
        response.setSendSuccessTime(row.getSendSuccessTime());
        response.setCostMs(row.getCostMs());
        response.setErrorCode(row.getErrorCode());
        response.setErrorMessage(row.getErrorMessage());
        response.setOperatorId(row.getOperatorId());
        response.setOperatorName(row.getOperatorName());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private EmailSendResult toSendResult(EmailSendRecordDO record) {
        EmailSendResult result = new EmailSendResult();
        result.setRecordId(record.getId());
        result.setEmailNo(record.getEmailNo());
        result.setSendStatus(record.getSendStatus());
        result.setErrorCode(record.getErrorCode());
        result.setErrorMessage(record.getErrorMessage());
        return result;
    }

    private void fillOperator(EmailSendRecordDO record) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            record.setOperatorName("system");
            return;
        }
        record.setOperatorId(account.getAccountId());
        record.setOperatorName(currentOperatorName());
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "system";
    }

    private Integer normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    private String[] parseEmailArray(String json) {
        if (!StringUtils.hasText(json)) {
            return new String[0];
        }
        try {
            return JSON.parseArray(json, String.class).stream().filter(StringUtils::hasText).toArray(String[]::new);
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimUpper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    private String encryptSecret(String plainText) {
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "SMTP 密码加密失败");
        }
    }

    private String decryptSecret(String cipherText) {
        try {
            String[] parts = cipherText.split("\\.", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "SMTP 密码解密失败");
        }
    }

    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.email.secret", System.getenv().getOrDefault("PAYMENT_EMAIL_SECRET", "local-email-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }
}

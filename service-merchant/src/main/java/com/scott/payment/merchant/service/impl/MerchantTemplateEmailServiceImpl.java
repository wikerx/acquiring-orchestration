package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.email.model.EnabledEmailTemplateSnapshot;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheReader;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailTemplateDO;
import com.scott.payment.merchant.mapper.MerchantEmailAccountMapper;
import com.scott.payment.merchant.mapper.MerchantEmailSendRecordMapper;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTemplateEmailServiceImpl
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户模板邮件服务实现，位于 商户后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class MerchantTemplateEmailServiceImpl implements MerchantTemplateEmailService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 是。
     */
    private static final int YES = 1;

    /**
     * 发送中。
     */
    private static final int SEND_PENDING = EmailDeliveryStatus.PENDING.getCode();

    /**
     * 发送失败。
     */
    private static final int SEND_FAILED = EmailDeliveryStatus.CLOSED.getCode();

    /**
     * 系统默认发件账户范围。
     */
    private static final String SCOPE_SYSTEM = "SYSTEM";

    /**
     * 商户专用发件账户范围。
     */
    private static final String SCOPE_MERCHANT = "MERCHANT";

    /**
     * 通用邮件所属系统。
     */
    private static final String APP_COMMON = "COMMON";
    /**
     * 管理系统所属系统，承载管理系统邮件配置页面维护的通用默认发件账户。
     */
    private static final String APP_ADMIN = "ADMIN";

    /**
     * 通用邮件场景。
     */
    private static final String COMMON_SCENE = "COMMON";

    /**
     * 默认语言。
     */
    private static final String DEFAULT_LOCALE = "zh-CN";

    /**
     * HTML 邮件类型。
     */
    private static final String CONTENT_HTML = "HTML";

    /**
     * 模板变量占位符。
     */
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");

    private final MerchantEmailAccountMapper emailAccountMapper;
    private final MerchantEmailSendRecordMapper emailSendRecordMapper;
    private final ObjectMapper objectMapper;
    /** 邮件真实正文加密组件。 */
    private final EmailPayloadCrypto payloadCrypto;
    /** Merchant 邮件异步投递编排。 */
    private final MerchantEmailDeliveryService deliveryService;
    /** 邮件默认重试配置。 */
    private final EmailDeliveryProperties deliveryProperties;
    /** 跨系统已启用邮件模板快照读取器。 */
    private final EnabledEmailTemplateCacheReader enabledTemplateCacheReader;

    /**
     * 创建商户模板邮件服务。
     *
     * @param emailAccountMapper    发件账户 Mapper
     * @param emailSendRecordMapper 邮件发送记录 Mapper
     * @param objectMapper          JSON 序列化工具
     * @param enabledTemplateCacheReader 已启用邮件模板快照读取器
     */
    public MerchantTemplateEmailServiceImpl(MerchantEmailAccountMapper emailAccountMapper,
                                            MerchantEmailSendRecordMapper emailSendRecordMapper,
                                            ObjectMapper objectMapper,
                                            EmailPayloadCrypto payloadCrypto,
                                            MerchantEmailDeliveryService deliveryService,
                                            EmailDeliveryProperties deliveryProperties,
                                            EnabledEmailTemplateCacheReader enabledTemplateCacheReader) {
        this.emailAccountMapper = emailAccountMapper;
        this.emailSendRecordMapper = emailSendRecordMapper;
        this.objectMapper = objectMapper;
        this.payloadCrypto = payloadCrypto;
        this.deliveryService = deliveryService;
        this.deliveryProperties = deliveryProperties;
        this.enabledTemplateCacheReader = enabledTemplateCacheReader;
    }

    /**
     * 按模板发送商户邮件，发送结果写入管理系统邮件发送记录表。
     *
     * @param request 邮件发送请求
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void sendByTemplate(MerchantEmailSendCommand request) {
        MerchantEmailTemplateDO template = requireEnabledTemplate(request.templateCode(), defaultIfBlank(request.locale(), DEFAULT_LOCALE));
        MerchantEmailAccountDO account = selectAccount(request.appCode(), request.merchantId(), defaultIfBlank(request.sceneCode(), template.getSceneCode()));
        Map<String, Object> variables = request.variables() == null ? Map.of() : request.variables();
        MerchantEmailSendRecordDO record = buildRecord(request, template, account);
        Set<String> missingVariables = missingVariables(template.getSubjectTemplate() + template.getContentTemplate(), variables);
        if (!missingVariables.isEmpty()) {
            record.setSubject(template.getSubjectTemplate());
            record.setContentSnapshot("模板变量缺失：" + String.join(",", missingVariables));
            record.setVariablesSnapshot(toJson(maskVariables(variables, parseStringList(template.getSensitiveVariableNames()))));
            record.setSendStatus(SEND_FAILED);
            record.setErrorCode("EMAIL_VARIABLE_MISSING");
            record.setErrorMessage("模板变量缺失：" + String.join(",", missingVariables));
            emailSendRecordMapper.insert(record);
            return;
        }
        String content = render(template.getContentTemplate(), variables);
        record.setSubject(render(template.getSubjectTemplate(), variables));
        record.setContentSnapshot(maskSensitiveContent(template.getContentTemplate(), variables, parseStringList(template.getSensitiveVariableNames())));
        record.setVariablesSnapshot(toJson(maskVariables(variables, parseStringList(template.getSensitiveVariableNames()))));
        record.setDeliveryContentCipher(payloadCrypto.encrypt(content));
        record.setContentType(defaultIfBlank(trimUpper(template.getContentType()), CONTENT_HTML));
        emailSendRecordMapper.insert(record);
        deliveryService.enqueue(record);
    }

    /**
     * 按模板编码和语言查询启用的商户邮件模板。
     *
     * @param templateCode 模板编码
     * @param locale       语言地区编码
     * @return 唯一启用且未删除的模板
     * @throws IllegalStateException 没有可用模板时抛出
     */
    private MerchantEmailTemplateDO requireEnabledTemplate(String templateCode, String locale) {
        EnabledEmailTemplateSnapshot snapshot = enabledTemplateCacheReader.findEnabled(
                trimUpper(templateCode),
                locale
        );
        if (snapshot == null) {
            throw new IllegalStateException("enabled merchant email template not found: " + templateCode);
        }
        MerchantEmailTemplateDO row = new MerchantEmailTemplateDO();
        row.setId(snapshot.getId());
        row.setTemplateCode(snapshot.getTemplateCode());
        row.setTemplateName(snapshot.getTemplateName());
        row.setAppCode(snapshot.getAppCode());
        row.setSceneCode(snapshot.getSceneCode());
        row.setLocale(snapshot.getLocale());
        row.setSubjectTemplate(snapshot.getSubjectTemplate());
        row.setContentType(snapshot.getContentType());
        row.setContentTemplate(snapshot.getContentTemplate());
        row.setSensitiveVariableNames(snapshot.getSensitiveVariableNames());
        row.setStatus(ENABLED);
        return row;
    }

    /**
     * 按商户、应用和场景优先级选择发件账号。
     * <p>
     * 优先商户专属场景账号，再回退系统 ADMIN/COMMON 账号和当前应用系统账号；账号凭据只在
     * 发送组件内使用，不写入邮件记录或日志。
     * </p>
     *
     * @param appCode    调用应用编码
     * @param merchantId 商户号；系统邮件可为空
     * @param sceneCode  邮件场景编码
     * @return 首个启用且匹配的发件账号
     * @throws IllegalStateException 没有可用发件账号时抛出
     */
    private MerchantEmailAccountDO selectAccount(String appCode, String merchantId, String sceneCode) {
        String normalizedAppCode = defaultIfBlank(trimUpper(appCode), AuthConstants.APP_MERCHANT);
        String normalizedSceneCode = defaultIfBlank(trimUpper(sceneCode), COMMON_SCENE);
        List<LambdaQueryWrapper<MerchantEmailAccountDO>> candidates = StringUtils.hasText(merchantId)
                ? List.of(
                accountRouteWrapper(normalizedAppCode, SCOPE_MERCHANT, merchantId, normalizedSceneCode),
                accountRouteWrapper(normalizedAppCode, SCOPE_MERCHANT, merchantId, COMMON_SCENE),
                accountRouteWrapper(APP_ADMIN, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(APP_ADMIN, SCOPE_SYSTEM, null, COMMON_SCENE),
                accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, COMMON_SCENE),
                accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, COMMON_SCENE)
        )
                : List.of(
                accountRouteWrapper(APP_ADMIN, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(APP_ADMIN, SCOPE_SYSTEM, null, COMMON_SCENE),
                accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, COMMON_SCENE),
                accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, normalizedSceneCode),
                accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, COMMON_SCENE)
        );
        for (LambdaQueryWrapper<MerchantEmailAccountDO> wrapper : candidates) {
            MerchantEmailAccountDO account = emailAccountMapper.selectOne(wrapper.last("LIMIT 1"));
            if (account != null) {
                return account;
            }
        }
        throw new IllegalStateException("available email account not found");
    }

    private LambdaQueryWrapper<MerchantEmailAccountDO> accountRouteWrapper(String appCode,
                                                                          String scopeType,
                                                                          String merchantId,
                                                                          String sceneCode) {
        return Wrappers.<MerchantEmailAccountDO>lambdaQuery()
                .eq(MerchantEmailAccountDO::getDeleted, NOT_DELETED)
                .eq(MerchantEmailAccountDO::getStatus, ENABLED)
                .eq(MerchantEmailAccountDO::getDefaultFlag, YES)
                .eq(MerchantEmailAccountDO::getAppCode, trimUpper(appCode))
                .eq(MerchantEmailAccountDO::getScopeType, scopeType)
                .eq(StringUtils.hasText(merchantId), MerchantEmailAccountDO::getMerchantId, trim(merchantId))
                .eq(MerchantEmailAccountDO::getSceneCode, defaultIfBlank(trimUpper(sceneCode), COMMON_SCENE))
                .orderByDesc(MerchantEmailAccountDO::getId);
    }

    private MerchantEmailSendRecordDO buildRecord(MerchantEmailSendCommand request,
                                                  MerchantEmailTemplateDO template,
                                                  MerchantEmailAccountDO account) {
        LocalDateTime now = LocalDateTime.now();
        MerchantEmailSendRecordDO record = new MerchantEmailSendRecordDO();
        record.setEmailNo(generateCode("EMAIL"));
        record.setAppCode(defaultIfBlank(trimUpper(request.appCode()), AuthConstants.APP_MERCHANT));
        record.setMerchantId(trim(request.merchantId()));
        record.setMerchantNo(defaultIfBlank(trim(request.merchantNo()), record.getMerchantId()));
        record.setMerchantName(trim(request.merchantName()));
        record.setSceneCode(defaultIfBlank(trimUpper(request.sceneCode()), template.getSceneCode()));
        record.setTemplateCode(template.getTemplateCode());
        record.setTemplateName(template.getTemplateName());
        record.setLocale(template.getLocale());
        record.setAccountId(account.getId());
        record.setAccountCode(account.getAccountCode());
        record.setProviderType(account.getProviderType());
        record.setFromName(account.getFromName());
        record.setFromEmail(account.getFromEmail());
        record.setReplyToEmail(account.getReplyToEmail());
        record.setToEmails(toJson(defaultList(request.toEmails())));
        record.setCcEmails(toJson(List.of()));
        record.setBccEmails(toJson(List.of()));
        record.setBizType(trimUpper(request.bizType()));
        record.setBizNo(trim(request.bizNo()));
        record.setSendStatus(SEND_PENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(Math.max(deliveryProperties.getDefaultMaxRetryCount(), 0));
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        return record;
    }

    private Set<String> missingVariables(String template, Map<String, Object> variables) {
        Set<String> required = extractVariables(template);
        required.removeIf(key -> variables.containsKey(key) && variables.get(key) != null);
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
        return render(template, maskVariables(variables, sensitiveNames));
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
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String[] parseEmailArray(String json) {
        if (!StringUtils.hasText(json)) {
            return new String[0];
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            }).stream().filter(StringUtils::hasText).toArray(String[]::new);
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private void fillOperator(MerchantEmailSendRecordDO record) {
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

    private List<String> defaultList(List<String> source) {
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }
}

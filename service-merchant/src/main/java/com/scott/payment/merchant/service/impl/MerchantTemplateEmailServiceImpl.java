package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商户模板邮件服务，校验模板变量并持久化待投递邮件。
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
     * 已完成准备但明确禁止投递。
     */
    private static final int SEND_CANCELLED = EmailDeliveryStatus.CANCELLED.getCode();

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
    /**
     * 邮件真实正文加密组件。
     */
    private final EmailPayloadCrypto payloadCrypto;
    /**
     * Merchant 邮件异步投递编排。
     */
    private final MerchantEmailDeliveryService deliveryService;
    /**
     * 邮件默认重试配置。
     */
    private final EmailDeliveryProperties deliveryProperties;
    /**
     * 跨系统已启用邮件模板快照读取器。
     */
    private final EnabledEmailTemplateCacheReader enabledTemplateCacheReader;

    /**
     * 创建商户模板邮件服务。
     *
     * @param emailAccountMapper    发件账户 Mapper
     * @param emailSendRecordMapper 邮件发送记录 Mapper
     * @param objectMapper          JSON 序列化工具
     * @param payloadCrypto 邮件正文加密组件
     * @param deliveryService 邮件异步投递服务
     * @param deliveryProperties 邮件投递重试配置
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
    public MerchantEmailSendResult sendByTemplate(MerchantEmailSendCommand request) {
        return prepareByTemplate(request, false);
    }

    /**
     * 完整执行邮件准备链路，但把记录直接置为取消终态并移除收件人。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MerchantEmailSendResult prepareSuppressedByTemplate(MerchantEmailSendCommand request) {
        return prepareByTemplate(request, true);
    }

    /**
     * 校验模板和变量后创建待投递邮件记录。
     */
    private MerchantEmailSendResult prepareByTemplate(MerchantEmailSendCommand request, boolean suppressDelivery) {
        MerchantEmailTemplateDO template;
        MerchantEmailAccountDO account;
        List<String> sensitiveVariableNames;
        try {
            template = requireEnabledTemplate(request.templateCode(), defaultIfBlank(request.locale(), DEFAULT_LOCALE));
            account = selectAccount(request.appCode(), request.merchantId(),
                    defaultIfBlank(request.sceneCode(), template.getSceneCode()));
            sensitiveVariableNames = parseStringList(template.getSensitiveVariableNames());
        } catch (RuntimeException exception) {
            return recordPreparationFailure(request, exception);
        }
        Map<String, Object> variables = request.variables() == null ? Map.of() : request.variables();
        MerchantEmailSendRecordDO record = buildRecord(request, template, account);
        Set<String> missingVariables = missingVariables(template.getSubjectTemplate() + template.getContentTemplate(), variables);
        if (!missingVariables.isEmpty()) {
            record.setSubject(template.getSubjectTemplate());
            record.setContentSnapshot("模板变量缺失：" + String.join(",", missingVariables));
            record.setVariablesSnapshot(toJson(maskVariables(variables, sensitiveVariableNames)));
            record.setSendStatus(SEND_FAILED);
            record.setErrorCode("EMAIL_VARIABLE_MISSING");
            record.setErrorMessage("模板变量缺失：" + String.join(",", missingVariables));
            emailSendRecordMapper.insert(record);
            return new MerchantEmailSendResult(record.getId(), record.getSendStatus());
        }
        String contentType = defaultIfBlank(trimUpper(template.getContentType()), CONTENT_HTML);
        boolean htmlContent = CONTENT_HTML.equals(contentType);
        String content = render(template.getContentTemplate(), variables, htmlContent);
        record.setSubject(render(template.getSubjectTemplate(), variables, false));
        record.setContentSnapshot(maskSensitiveContent(template.getContentTemplate(), variables,
                sensitiveVariableNames, htmlContent));
        record.setVariablesSnapshot(toJson(maskVariables(variables, sensitiveVariableNames)));
        record.setDeliveryContentCipher(payloadCrypto.encrypt(content));
        record.setContentType(contentType);
        if (suppressDelivery) {
            record.setToEmails(toJson(List.of()));
            record.setSendStatus(SEND_CANCELLED);
            record.setMaxRetryCount(0);
            record.setErrorCode("DELIVERY_SUPPRESSED");
            record.setErrorMessage("Delivery suppressed for a virtual security request");
        }
        emailSendRecordMapper.insert(record);
        deliveryService.enqueue(record);
        return new MerchantEmailSendResult(record.getId(), record.getSendStatus());
    }

    /**
     * 模板或发件账户缺失时也写入关闭记录，避免安全邮件准备失败却没有审计轨迹。
     */
    private MerchantEmailSendResult recordPreparationFailure(MerchantEmailSendCommand request,
                                                             RuntimeException exception) {
        LocalDateTime now = LocalDateTime.now();
        MerchantEmailSendRecordDO record = new MerchantEmailSendRecordDO();
        record.setEmailNo(generateCode("EMAIL"));
        record.setAppCode(defaultIfBlank(trimUpper(request.appCode()), AuthConstants.APP_MERCHANT));
        record.setMerchantId(trim(request.merchantId()));
        record.setMerchantNo(defaultIfBlank(trim(request.merchantNo()), record.getMerchantId()));
        record.setMerchantName(trim(request.merchantName()));
        record.setSceneCode(defaultIfBlank(trimUpper(request.sceneCode()), COMMON_SCENE));
        record.setTemplateCode(trimUpper(request.templateCode()));
        record.setTemplateName(trim(request.templateCode()));
        record.setLocale(defaultIfBlank(request.locale(), DEFAULT_LOCALE));
        record.setToEmails(toJson(defaultList(request.toEmails())));
        record.setCcEmails(toJson(List.of()));
        record.setBccEmails(toJson(List.of()));
        record.setSubject("邮件准备失败");
        record.setContentSnapshot("邮件模板或发件账户不可用");
        record.setVariablesSnapshot(toJson(maskAllVariables(request.variables())));
        record.setContentType(CONTENT_HTML);
        record.setBizType(trimUpper(request.bizType()));
        record.setBizNo(trim(request.bizNo()));
        record.setSendStatus(SEND_FAILED);
        record.setRetryCount(0);
        record.setMaxRetryCount(0);
        record.setErrorCode("EMAIL_PREPARATION_FAILED");
        record.setErrorMessage(truncate(exception.getClass().getSimpleName() + ": " + exception.getMessage(), 2000));
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        emailSendRecordMapper.insert(record);
        return new MerchantEmailSendResult(record.getId(), record.getSendStatus());
    }

    /**
     * 对全部模板变量执行默认脱敏。
     */
    private Map<String, Object> maskAllVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        variables.keySet().forEach(key -> masked.put(key, "******"));
        return masked;
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

    /**
     * 构建限定邮件账号与应用范围的数据库查询条件。
     */
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

    /**
     * 构建不含明文敏感变量的邮件投递记录。
     */
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

    /**
     * 返回模板引用但请求未提供的变量名集合。
     */
    private Set<String> missingVariables(String template, Map<String, Object> variables) {
        Set<String> required = extractVariables(template);
        required.removeIf(key -> variables.containsKey(key) && variables.get(key) != null);
        return required;
    }

    /**
     * 从模板正文中提取变量占位符。
     */
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

    /**
     * 使用已校验变量渲染模板，并按内容类型执行转义。
     */
    private String render(String template, Map<String, Object> variables, boolean htmlEscape) {
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            String replacement = value == null ? "" : String.valueOf(value);
            if (htmlEscape) {
                replacement = HtmlUtils.htmlEscape(replacement);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * 按模板敏感变量配置脱敏邮件内容。
     */
    private String maskSensitiveContent(String template,
                                        Map<String, Object> variables,
                                        List<String> sensitiveNames,
                                        boolean htmlEscape) {
        if (CollectionUtils.isEmpty(sensitiveNames)) {
            return render(template, variables, htmlEscape);
        }
        return render(template, maskVariables(variables, sensitiveNames), htmlEscape);
    }

    /**
     * 复制模板变量并脱敏指定字段。
     */
    private Map<String, Object> maskVariables(Map<String, Object> variables, List<String> sensitiveNames) {
        Map<String, Object> masked = new LinkedHashMap<>(variables);
        for (String name : sensitiveNames) {
            if (masked.containsKey(name)) {
                masked.put(name, "******");
            }
        }
        return masked;
    }

    /**
     * 解析模板敏感变量名称；非法配置必须进入邮件准备失败审计，不能按空集合继续发送。
     *
     * @param json 模板敏感变量名称 JSON 数组
     * @return 敏感变量名称列表
     * @throws IllegalStateException JSON 不是字符串数组时抛出
     */
    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("email template sensitive variable configuration is invalid", exception);
        }
    }

    /**
     * 将邮件地址或模板变量序列化为 JSON 文本；失败时中止当前事务，避免持久化伪造的空数组。
     *
     * @param value 待序列化值
     * @return JSON 文本
     * @throws IllegalStateException JSON 序列化失败时抛出
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("email audit payload serialization failed", exception);
        }
    }

    /**
     * 补充邮件操作人信息，防止信任调用方伪造审计字段。
     */
    private void fillOperator(MerchantEmailSendRecordDO record) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            record.setOperatorName("system");
            return;
        }
        record.setOperatorId(account.getAccountId());
        record.setOperatorName(currentOperatorName());
    }

    /**
     * 读取当前操作人展示名称。
     */
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

    /**
     * 将空集合归一化为不可变空集合。
     */
    private List<String> defaultList(List<String> source) {
        return source == null ? List.of() : source;
    }

    /**
     * 去除字符串首尾空白，空值保持为空。
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 去除字符串空白并转换为大写编码。
     */
    private String trimUpper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /**
     * 字符串为空时返回指定默认值。
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 按持久化字段长度安全截断文本。
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 生成满足模板要求的一次性验证码。
     */
    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }
}

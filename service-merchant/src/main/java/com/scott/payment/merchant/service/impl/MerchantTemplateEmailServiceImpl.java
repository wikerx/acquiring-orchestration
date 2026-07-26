package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailTemplateDO;
import com.scott.payment.merchant.mapper.MerchantEmailAccountMapper;
import com.scott.payment.merchant.mapper.MerchantEmailSendRecordMapper;
import com.scott.payment.merchant.mapper.MerchantEmailTemplateMapper;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTemplateEmailServiceImpl
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户模板邮件服务实现，位于 service-merchant 服务实现层；读取管理系统维护的邮件模板与发件账户发送商户 MFA 安全通知。
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
    private static final int SEND_SENDING = 1;

    /**
     * 发送成功。
     */
    private static final int SEND_SUCCESS = 2;

    /**
     * 发送失败。
     */
    private static final int SEND_FAILED = 3;

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

    /**
     * email Account Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantEmailAccountMapper emailAccountMapper;
    /**
     * email Template Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantEmailTemplateMapper emailTemplateMapper;
    /**
     * email Send Record Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantEmailSendRecordMapper emailSendRecordMapper;
    /**
     * object Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建商户模板邮件服务。
     *
     * @param emailAccountMapper    发件账户 Mapper
     * @param emailTemplateMapper   邮件模板 Mapper
     * @param emailSendRecordMapper 邮件发送记录 Mapper
     * @param objectMapper          JSON 序列化工具
     */
    public MerchantTemplateEmailServiceImpl(MerchantEmailAccountMapper emailAccountMapper,
                                            MerchantEmailTemplateMapper emailTemplateMapper,
                                            MerchantEmailSendRecordMapper emailSendRecordMapper,
                                            ObjectMapper objectMapper) {
        this.emailAccountMapper = emailAccountMapper;
        this.emailTemplateMapper = emailTemplateMapper;
        this.emailSendRecordMapper = emailSendRecordMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按模板发送商户邮件，发送结果写入管理系统邮件发送记录表。
     *
     * @param request 邮件发送请求
     */
    @Override
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
        emailSendRecordMapper.insert(record);
        doSend(record, account, content, CONTENT_HTML.equalsIgnoreCase(template.getContentType()));
    }

    private MerchantEmailTemplateDO requireEnabledTemplate(String templateCode, String locale) {
        MerchantEmailTemplateDO row = emailTemplateMapper.selectOne(Wrappers.<MerchantEmailTemplateDO>lambdaQuery()
                .eq(MerchantEmailTemplateDO::getTemplateCode, trimUpper(templateCode))
                .eq(MerchantEmailTemplateDO::getLocale, locale)
                .eq(MerchantEmailTemplateDO::getStatus, ENABLED)
                .eq(MerchantEmailTemplateDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new IllegalStateException("enabled merchant email template not found: " + templateCode);
        }
        return row;
    }

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
 * 执行 account Route Wrapper 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param appCode app Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param scopeType scope Type 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param sceneCode scene Code 输入值，含义由调用方法名称和所属业务对象限定
 * @return 方法签名声明的返回值，具体结构由返回类型定义
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
 * 执行 build Record 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param template template 输入值，含义由调用方法名称和所属业务对象限定
 * @param account account 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
        record.setSendStatus(SEND_SENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(0);
        fillOperator(record);
        record.setCreateBy(currentOperatorName());
        record.setUpdateBy(currentOperatorName());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(NOT_DELETED);
        return record;
    }

/**
 * 执行 do Send 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param record record 输入值，含义由调用方法名称和所属业务对象限定
 * @param account account 输入值，含义由调用方法名称和所属业务对象限定
 * @param content content 输入值，含义由调用方法名称和所属业务对象限定
 * @param html html 输入值，含义由调用方法名称和所属业务对象限定
 */
    private void doSend(MerchantEmailSendRecordDO record,
                        MerchantEmailAccountDO account,
                        String content,
                        boolean html) {
        LocalDateTime start = LocalDateTime.now();
        record.setSendStartTime(start);
        record.setSendStatus(SEND_SENDING);
        emailSendRecordMapper.updateById(record);
        try {
            JavaMailSenderImpl sender = buildMailSender(account);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(account.getFromEmail(), account.getFromName());
            if (StringUtils.hasText(account.getReplyToEmail())) {
                helper.setReplyTo(account.getReplyToEmail());
            }
            helper.setTo(parseEmailArray(record.getToEmails()));
            helper.setSubject(record.getSubject());
            helper.setText(content, html);
            sender.send(message);
            record.setSendStatus(SEND_SUCCESS);
            record.setSendEndTime(LocalDateTime.now());
            record.setSendSuccessTime(record.getSendEndTime());
            record.setCostMs(Duration.between(start, record.getSendEndTime()).toMillis());
            record.setErrorCode(null);
            record.setErrorMessage(null);
        } catch (Exception exception) {
            record.setSendStatus(SEND_FAILED);
            record.setSendEndTime(LocalDateTime.now());
            record.setCostMs(Duration.between(start, record.getSendEndTime()).toMillis());
            record.setErrorCode("EMAIL_SEND_FAILED");
            record.setErrorMessage(truncate(exception.getMessage(), 1800));
        }
        record.setUpdateBy(currentOperatorName());
        record.setUpdateTime(LocalDateTime.now());
        emailSendRecordMapper.updateById(record);
    }

    /**
     * 执行 build Mail Sender 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param account account 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private JavaMailSenderImpl buildMailSender(MerchantEmailAccountDO account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getSmtpHost());
        sender.setPort(account.getSmtpPort());
        sender.setUsername(account.getSmtpUsername());
        if (account.getSmtpAuthRequired() == YES) {
            sender.setPassword(decryptSecret(account.getSmtpPasswordCipher()));
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(account.getSmtpAuthRequired() == YES));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(account.getConnectTimeoutMs()));
        properties.put("mail.smtp.timeout", String.valueOf(account.getReadTimeoutMs()));
        properties.put("mail.smtp.writetimeout", String.valueOf(account.getReadTimeoutMs()));
        if ("SSL".equals(account.getEncryptionType()) || "TLS".equals(account.getEncryptionType())) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(account.getEncryptionType())) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    /**
     * 执行 missing Variables 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param template template 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param variables variables 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Set<String> missingVariables(String template, Map<String, Object> variables) {
        Set<String> required = extractVariables(template);
        required.removeIf(key -> variables.containsKey(key) && variables.get(key) != null);
        return required;
    }

    /**
     * 执行 extract Variables 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param template template 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 render 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param template template 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param variables variables 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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

    /**
     * 执行 mask Sensitive Content 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param template template 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param variables variables 输入值，含义由调用方法名称和所属业务对象限定
     * @param sensitiveNames sensitive Names 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String maskSensitiveContent(String template, Map<String, Object> variables, List<String> sensitiveNames) {
        if (CollectionUtils.isEmpty(sensitiveNames)) {
            return render(template, variables);
        }
        return render(template, maskVariables(variables, sensitiveNames));
    }

    /**
     * 执行 mask Variables 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param variables variables 输入值，含义由调用方法名称和所属业务对象限定
     * @param sensitiveNames sensitive Names 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 parse String List 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param json json 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
     */
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

    /**
     * 执行 parse Email Array 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param json json 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
     */
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

    /**
     * 执行 to Json 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 转换或构建后的目标对象
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    /**
     * 执行 fill Operator 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param record record 输入值，含义由调用方法名称和所属业务对象限定
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
     * 执行 current Operator Name 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 decrypt Secret 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param cipherText cipher Text 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String decryptSecret(String cipherText) {
        try {
            String[] parts = cipherText.split("\\.", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("smtp password decrypt failed", exception);
        }
    }

    /**
     * 执行 secret Key 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.email.secret", System.getenv().getOrDefault("PAYMENT_EMAIL_SECRET", "local-email-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 执行 default List 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private List<String> defaultList(List<String> source) {
        return source == null ? List.of() : source;
    }

    /**
     * 执行 trim 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 执行 trim Upper 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trimUpper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /**
     * 执行 default If Blank 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param defaultValue default Value 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 执行 truncate 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param maxLength max Length 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 执行 generate Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantTemplateEmailServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param prefix prefix 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }
}

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
     * email Account Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantEmailAccountMapper emailAccountMapper;
    /**
     * email Template Mapper，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和格式由接口校验约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantEmailTemplateMapper emailTemplateMapper;
    /**
     * email Send Record Mapper 依赖，用于 Merchant Template Email Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和格式由接口校验约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantEmailSendRecordMapper emailSendRecordMapper;
    /**
     * object Mapper 依赖，用于 Merchant Template Email Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
 * 整理账号routewrapper，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param appCode app Code 输入值，参与 app编码 的查询、校验、转换、写入或日志摘要
 * @param scopeType scope Type 输入值，参与 scopetype 的查询、校验、转换、写入或日志摘要
 * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
 * @param sceneCode scene Code 输入值，参与 scene编码 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 构造记录对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
 * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
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
 * 整理邮件发送动作，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
 * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
 * @param content content 输入值，参与 content 的查询、校验、转换、写入或日志摘要
 * @param html html 输入值，参与 html 的查询、校验、转换、写入或日志摘要
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
     * 构造mailsender对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 整理缺失模板变量，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param variables variables 输入值，参与 变量 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<String> missingVariables(String template, Map<String, Object> variables) {
        Set<String> required = extractVariables(template);
        required.removeIf(key -> variables.containsKey(key) && variables.get(key) != null);
        return required;
    }

    /**
     * 整理模板变量提取结果，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化render，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param variables variables 输入值，参与 变量 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 脱敏sensitivecontent，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param variables variables 输入值，参与 variables 的查询、校验、转换、写入或日志摘要
     * @param sensitiveNames sensitive Names 输入值，参与 sensitivenames 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskSensitiveContent(String template, Map<String, Object> variables, List<String> sensitiveNames) {
        if (CollectionUtils.isEmpty(sensitiveNames)) {
            return render(template, variables);
        }
        return render(template, maskVariables(variables, sensitiveNames));
    }

    /**
     * 脱敏variables，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param variables variables 输入值，参与 variables 的查询、校验、转换、写入或日志摘要
     * @param sensitiveNames sensitive Names 输入值，参与 sensitivenames 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 解析parsestringlist，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param json json 输入值，参与 json 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 解析parse邮件array，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param json json 输入值，参与 json 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 构造json对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    /**
     * 构造operator对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
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
     * 整理当前操作人名称，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化secret，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param cipherText cipher Text 输入值，参与 密文文本 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理密钥材料，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.email.secret", System.getenv().getOrDefault("PAYMENT_EMAIL_SECRET", "local-email-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 整理默认list，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> defaultList(List<String> source) {
        return source == null ? List.of() : source;
    }

    /**
     * 规范化trim，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 规范化trimupper，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimUpper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /**
     * 整理默认ifblank，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 规范化truncate，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param maxLength max Length 输入值，参与 maxlength 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 创建编码，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param prefix prefix 输入值，参与 prefix 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }
}

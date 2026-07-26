package com.scott.payment.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.constant.SystemConfigKeys;
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
import com.scott.payment.admin.service.AdminConfigService;
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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailServiceImpl
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : Admin Email Service Impl 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class AdminEmailServiceImpl implements AdminEmailService {

    /**
     * NOT DELETED，用于保存 Admin Email Service Impl 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ENABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * DISABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int DISABLED = 0;
    /**
     * YES，用于保存 Admin Email Service Impl 中与 yes 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int YES = 1;
    /**
     * NO，用于保存 Admin Email Service Impl 中与 no 相关的业务属性。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int NO = 0;
    /**
     * VERIFY UNVERIFIED，用于保存 Admin Email Service Impl 中与 verifyunverified 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int VERIFY_UNVERIFIED = 0;
    /**
     * VERIFY SUCCESS，用于保存 Admin Email Service Impl 中与 verifysuccess 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int VERIFY_SUCCESS = 1;
    /**
     * VERIFY FAILED，用于保存 Admin Email Service Impl 中与 verifyfailed 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int VERIFY_FAILED = 2;
    /**
     * SEND SENDING，用于保存 Admin Email Service Impl 中与 sendsending 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int SEND_SENDING = 1;
    /**
     * SEND SUCCESS，用于保存 Admin Email Service Impl 中与 sendsuccess 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int SEND_SUCCESS = 2;
    /**
     * SEND FAILED，用于保存 Admin Email Service Impl 中与 sendfailed 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int SEND_FAILED = 3;
    /**
     * COMMON SCENE，用于保存 Admin Email Service Impl 中与 commonscene 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String COMMON_SCENE = "COMMON";
    /**
     * 全局通用发件账户应用编码，用于没有应用专属发件账户时兜底。
     */
    private static final String APP_COMMON = "COMMON";
    /**
     * SCOPE SYSTEM，用于保存 Admin Email Service Impl 中与 scopesystem 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SCOPE_SYSTEM = "SYSTEM";
    /**
     * SCOPE MERCHANT，用于保存 Admin Email Service Impl 中与 scope商户 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SCOPE_MERCHANT = "MERCHANT";
    /**
     * SMTP PROVIDER，用于保存 Admin Email Service Impl 中与 smtpprovider 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SMTP_PROVIDER = "SMTP";
    /**
     * DEFAULT LOCALE，用于保存 Admin Email Service Impl 中与 defaultlocale 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_LOCALE = "zh-CN";
    /**
     * CONTENT HTML，用于保存 Admin Email Service Impl 中与 contenthtml 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String CONTENT_HTML = "HTML";
    /**
     * TEMPLATE VARIABLE PATTERN，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");
    /**
     * SECURE RANDOM，用于保存 Admin Email Service Impl 中与 securerandom 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * account Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final EmailAccountMapper accountMapper;
    /**
     * template Mapper，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final EmailTemplateMapper templateMapper;
    /**
     * record Mapper 依赖，用于 Admin Email Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final EmailSendRecordMapper recordMapper;
    /**
     * 系统参数配置服务，用于邮件模板注入平台访问地址等公共变量。
     */
    private final AdminConfigService adminConfigService;

/**
 * 整理admin邮件serviceimpl，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param accountMapper account Mapper 输入值，参与 账号映射器 的查询、校验、转换、写入或日志摘要
 * @param templateMapper template Mapper 输入值，参与 template映射器 的查询、校验、转换、写入或日志摘要
 * @param recordMapper record Mapper 输入值，参与 记录映射器 的查询、校验、转换、写入或日志摘要
 * @param adminConfigService admin Config Service 输入值，参与 admin配置service 的查询、校验、转换、写入或日志摘要
 */
    public AdminEmailServiceImpl(EmailAccountMapper accountMapper,
                                 EmailTemplateMapper templateMapper,
                                 EmailSendRecordMapper recordMapper,
                                 AdminConfigService adminConfigService) {
        this.accountMapper = accountMapper;
        this.templateMapper = templateMapper;
        this.recordMapper = recordMapper;
        this.adminConfigService = adminConfigService;
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

    /**
     * 查询模板，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
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
        Map<String, Object> variables = enrichSystemVariables(request.getVariables());
        Set<String> missing = missingVariables(request.getSubjectTemplate() + request.getContentTemplate(), variables);
        EmailTemplatePreviewResponse response = new EmailTemplatePreviewResponse();
        response.getMissingVariables().addAll(missing);
        if (missing.isEmpty()) {
            response.setSubject(render(request.getSubjectTemplate(), variables));
            response.setContent(render(request.getContentTemplate(), variables));
            response.setMaskedContent(maskSensitiveContent(request.getContentTemplate(), variables, parseStringList(request.getSensitiveVariableNames())));
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
    /**
     * 发送bytemplate消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已确定 运营后台服务 的目标地址、消息主题、业务编号和重试策略。
     * 该方法可能调用外部系统、内部服务或 MQ；traceId 必须沿调用链透传，重试应保留原业务标识。
     * 异常边界：网络异常、超时或投递失败需转换为当前模块可识别的失败结果并记录脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    public EmailSendResult sendByTemplate(EmailSendRequest request) {
        EmailTemplateDO template = requireEnabledTemplate(request.getTemplateCode(), defaultIfBlank(request.getLocale(), DEFAULT_LOCALE));
        Map<String, Object> variables = enrichSystemVariables(request.getVariables());
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

    /**
     * 发送resend消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理账号查询wrapper，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理template查询wrapper，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 记录querywrapper，写入安全、审计或链路排障所需的脱敏上下文。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 构造账号对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param create create 输入值，参与 create 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 校验账号scope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     */
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

    /**
     * 构造模板对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造记录对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param template template 输入值，参与 模板 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造账号snapshot对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     */
    private void fillAccountSnapshot(EmailSendRecordDO record, EmailAccountDO account) {
        record.setAccountId(account.getId());
        record.setAccountCode(account.getAccountCode());
        record.setProviderType(account.getProviderType());
        record.setFromName(account.getFromName());
        record.setFromEmail(account.getFromEmail());
        record.setReplyToEmail(account.getReplyToEmail());
    }

    /**
     * 整理邮件发送动作，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param content content 输入值，参与 content 的查询、校验、转换、写入或日志摘要
     * @param html html 输入值，参与 html 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 构造mailsender对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 查询账号，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appCode app Code 输入值，参与 app编码 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param sceneCode scene Code 输入值，参与 scene编码 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private EmailAccountDO selectAccount(String appCode, String merchantId, String sceneCode) {
        String normalizedAppCode = defaultIfBlank(trimUpper(appCode), APP_COMMON);
        String normalizedSceneCode = defaultIfBlank(trimUpper(sceneCode), COMMON_SCENE);
        List<LambdaQueryWrapper<EmailAccountDO>> candidates = new ArrayList<>();
        if (StringUtils.hasText(merchantId)) {
            candidates.add(accountRouteWrapper(normalizedAppCode, SCOPE_MERCHANT, merchantId, normalizedSceneCode));
            candidates.add(accountRouteWrapper(normalizedAppCode, SCOPE_MERCHANT, merchantId, COMMON_SCENE));
        }
        candidates.add(accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, normalizedSceneCode));
        candidates.add(accountRouteWrapper(normalizedAppCode, SCOPE_SYSTEM, null, COMMON_SCENE));
        if (!APP_COMMON.equals(normalizedAppCode)) {
            candidates.add(accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, normalizedSceneCode));
            candidates.add(accountRouteWrapper(APP_COMMON, SCOPE_SYSTEM, null, COMMON_SCENE));
        }
        for (LambdaQueryWrapper<EmailAccountDO> wrapper : candidates) {
            EmailAccountDO account = accountMapper.selectOne(wrapper.last("LIMIT 1"));
            if (account != null) {
                return account;
            }
        }
        throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "未找到可用发件账户");
    }

    /**
     * 整理账号routewrapper，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appCode app Code 输入值，参与 app编码 的查询、校验、转换、写入或日志摘要
     * @param scopeType scope Type 输入值，参与 scopetype 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param sceneCode scene Code 输入值，参与 scene编码 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理clear默认账号，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 整理缺失模板变量，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
        required.removeIf(key -> variables != null && variables.containsKey(key) && variables.get(key) != null);
        return required;
    }

    /**
     * 为邮件模板补充平台公共访问地址变量，业务传入变量优先，避免开户和订单邮件硬编码域名。
     *
     * @param variables 业务变量
     * @return 合并后的变量
     */
    private Map<String, Object> enrichSystemVariables(Map<String, Object> variables) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        Map<String, String> configValues = adminConfigService.enabledConfigValues(Set.copyOf(SystemConfigKeys.EMAIL_BASE_URL_VARIABLE_KEYS.values()));
        SystemConfigKeys.EMAIL_BASE_URL_VARIABLE_KEYS.forEach((variableName, configKey) -> {
            String value = configValues.get(configKey);
            if (StringUtils.hasText(value)) {
                enriched.put(variableName, value);
            }
        });
        if (variables != null) {
            enriched.putAll(variables);
        }
        return enriched;
    }

    /**
     * 整理模板变量提取结果，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
        Map<String, Object> masked = new LinkedHashMap<>(variables);
        for (String name : sensitiveNames) {
            if (masked.containsKey(name)) {
                masked.put(name, "******");
            }
        }
        return render(template, masked);
    }

    /**
     * 脱敏variables，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
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
            return JSON.parseArray(json, String.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 解析normalizejsonarray，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造retry记录对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 校验账号输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 校验模板输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private EmailTemplateDO requireTemplate(Long id) {
        EmailTemplateDO row = templateMapper.selectOne(Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getId, id)
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "邮件模板不存在");
        }
        return row;
    }

    /**
     * 校验enabledtemplate输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param templateCode template Code 输入值，参与 template编码 的查询、校验、转换、写入或日志摘要
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 校验记录输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private EmailSendRecordDO requireRecord(Long id) {
        EmailSendRecordDO row = recordMapper.selectOne(Wrappers.<EmailSendRecordDO>lambdaQuery()
                .eq(EmailSendRecordDO::getId, id)
                .eq(EmailSendRecordDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "邮件发送记录不存在");
        }
        return row;
    }

    /**
     * 校验确保账号编码unique输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
    private void ensureAccountCodeUnique(String code, Long excludeId) {
        Long count = accountMapper.selectCount(Wrappers.<EmailAccountDO>lambdaQuery()
                .eq(EmailAccountDO::getAccountCode, code)
                .eq(EmailAccountDO::getDeleted, NOT_DELETED)
                .ne(excludeId != null, EmailAccountDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "发件账户编码不能重复");
        }
    }

    /**
     * 校验确保templateunique输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造账号响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造template响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造记录响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造send结果对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private EmailSendResult toSendResult(EmailSendRecordDO record) {
        EmailSendResult result = new EmailSendResult();
        result.setRecordId(record.getId());
        result.setEmailNo(record.getEmailNo());
        result.setSendStatus(record.getSendStatus());
        result.setErrorCode(record.getErrorCode());
        result.setErrorMessage(record.getErrorMessage());
        return result;
    }

    /**
     * 构造operator对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
     */
    private void fillOperator(EmailSendRecordDO record) {
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 解析normalize状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private Integer normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    /**
     * 解析parse邮件array，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
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
            return JSON.parseArray(json, String.class).stream().filter(StringUtils::hasText).toArray(String[]::new);
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? List.of() : source;
    }

    /**
     * 规范化trim，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 整理默认ifnull，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 规范化truncate，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param prefix prefix 输入值，参与 prefix 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String generateCode(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    /**
     * 规范化encryptsecret，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param plainText plain Text 输入值，参与 明文文本 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 规范化secret，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
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
        } catch (Exception ex) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "SMTP 密码解密失败");
        }
    }

    /**
     * 整理密钥材料，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.email.secret", System.getenv().getOrDefault("PAYMENT_EMAIL_SECRET", "local-email-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }
}

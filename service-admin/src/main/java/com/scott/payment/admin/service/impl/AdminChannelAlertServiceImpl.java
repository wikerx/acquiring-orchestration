package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.AlertEventAcknowledgeRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleItem;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleOptionsResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.EmailTemplateOption;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.PaymentMethodOption;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.UserEmailOption;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertEventDO;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertNotifyLogDO;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertRuleDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailTemplateDO;
import com.scott.payment.admin.mapper.ChannelAlertEventMapper;
import com.scott.payment.admin.mapper.ChannelAlertNotifyLogMapper;
import com.scott.payment.admin.mapper.ChannelAlertRuleMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminChannelAlertService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelAlertServiceImpl
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警管理服务实现，负责后台规则配置、事件确认和邮件通知日志查询，不执行交易统计或路由熔断。
 * @status : create
 */
@Service
public class AdminChannelAlertServiceImpl implements AdminChannelAlertService {

    /**
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * DISABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int DISABLED = 0;
    /**
     * ALL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String ALL = "ALL";
    /**
     * EMAIL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EMAIL = "EMAIL";
    /**
     * BANK CARD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String BANK_CARD = "BANK_CARD";
    /**
     * ADMIN APP CODE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String ADMIN_APP_CODE = "ADMIN";
    /**
     * DEFAULT EMAIL LOCALE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_EMAIL_LOCALE = "zh-CN";
    /**
     * DEFAULT EMAIL SCENE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_EMAIL_SCENE = "CHANNEL_ALERT";
    /**
     * EVENT STATUS ACKNOWLEDGED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EVENT_STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    /**
     * RULE CODE PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RULE_CODE_PREFIX = "CAR";
    /**
     * RULE GROUP CODE PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RULE_GROUP_CODE_PREFIX = "CARG";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");
    private static final Set<String> BUSINESS_TYPES = Set.of("ACQUIRING", "PAYOUT");
    private static final Set<String> ALERT_LEVELS = Set.of("L1_WARNING", "L2_DEGRADED", "L3_CIRCUIT_BREAK");
    private static final Set<String> RULE_TYPES = Set.of(
            "CONTINUOUS_FAILURE",
            "SUCCESS_RATE_LOW",
            "TECH_ERROR_RATE_HIGH",
            "LATENCY_HIGH"
    );
    private static final Map<String, String> RULE_TYPE_THRESHOLD = Map.of(
            "CONTINUOUS_FAILURE", "COUNT",
            "SUCCESS_RATE_LOW", "RATE",
            "TECH_ERROR_RATE_HIGH", "RATE",
            "LATENCY_HIGH", "MILLIS"
    );

    /**
     * rule Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelAlertRuleMapper ruleMapper;
    /**
     * event Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelAlertEventMapper eventMapper;
    /**
     * notify Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelAlertNotifyLogMapper notifyLogMapper;
    /**
     * channel Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelInfoMapper channelInfoMapper;
    /**
     * capability Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelPaymentCapabilityMapper capabilityMapper;
    /**
     * capability Card Brand Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    /**
     * sys App Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Account Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * sys User Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysUserMapper sysUserMapper;
    /**
     * email Template Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final EmailTemplateMapper emailTemplateMapper;

    /**
     * 创建渠道预警管理服务。
     *
     * @param ruleMapper 预警规则 Mapper
     * @param eventMapper 预警事件 Mapper
     * @param notifyLogMapper 通知日志 Mapper
     * @param channelInfoMapper 渠道基础信息 Mapper
     */
    public AdminChannelAlertServiceImpl(ChannelAlertRuleMapper ruleMapper,
                                        ChannelAlertEventMapper eventMapper,
                                        ChannelAlertNotifyLogMapper notifyLogMapper,
                                        ChannelInfoMapper channelInfoMapper,
                                        ChannelPaymentCapabilityMapper capabilityMapper,
                                        ChannelCapabilityCardBrandMapper capabilityCardBrandMapper,
                                        SysAppMapper sysAppMapper,
                                        SysAccountMapper sysAccountMapper,
                                        SysUserMapper sysUserMapper,
                                        EmailTemplateMapper emailTemplateMapper) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.channelInfoMapper = channelInfoMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCardBrandMapper = capabilityCardBrandMapper;
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysUserMapper = sysUserMapper;
        this.emailTemplateMapper = emailTemplateMapper;
    }

    /**
     * 分页查询渠道预警规则。
     *
     * @param request 查询条件
     * @return 规则分页结果
     */
    @Override
    public PageResult<ChannelAlertRuleResponse> pageRules(ChannelAlertRuleQuery request) {
        ChannelAlertRuleQuery query = request == null ? new ChannelAlertRuleQuery() : request;
        Page<ChannelAlertRuleDO> page = ruleMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildRuleQuery(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toRuleResponse).toList());
    }

    /**
     * 查询渠道预警规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     */
    @Override
    public ChannelAlertRuleResponse getRule(Long id) {
        return toRuleResponse(requireRule(id));
    }

    /**
     * 创建渠道预警规则。
     *
     * @param request 保存请求
     * @return 创建后的规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelAlertRuleResponse createRule(ChannelAlertRuleSaveRequest request) {
        validateRuleRequest(request, null);
        LocalDateTime now = LocalDateTime.now();
        ChannelInfoDO channel = requireChannel(request.getChannelId());
        ChannelAlertRuleDO row = new ChannelAlertRuleDO();
        String ruleCode = PaymentOrderNoGenerator.nextOrderNo(RULE_CODE_PREFIX, now);
        row.setRuleCode(ruleCode);
        row.setRuleGroupCode(ruleCode);
        row.setCreateBy(currentOperatorName());
        row.setCreateTime(now);
        row.setDeleted(NOT_DELETED);
        fillRule(row, request, channel, now);
        ruleMapper.insert(row);
        return toRuleResponse(row);
    }

    /**
     * 批量创建同一渠道维度下的预警规则。
     *
     * @param request 批量保存请求
     * @return 创建后的规则集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ChannelAlertRuleResponse> createRules(ChannelAlertRuleBatchSaveRequest request) {
        BatchRuleContext context = validateBatchRequest(request);
        ChannelInfoDO channel = requireChannel(request.getChannelId());
        String groupCode = PaymentOrderNoGenerator.nextOrderNo(RULE_GROUP_CODE_PREFIX, LocalDateTime.now());
        List<ChannelAlertRuleResponse> responses = new ArrayList<>();
        for (ChannelAlertRuleItem item : request.getRules()) {
            ChannelAlertRuleSaveRequest singleRequest = toSingleRequest(request, item, context.dimension());
            validateRuleRequest(singleRequest, null);
            LocalDateTime now = LocalDateTime.now();
            ChannelAlertRuleDO row = new ChannelAlertRuleDO();
            row.setRuleCode(PaymentOrderNoGenerator.nextOrderNo(RULE_CODE_PREFIX, now));
            row.setRuleGroupCode(groupCode);
            row.setCreateBy(currentOperatorName());
            row.setCreateTime(now);
            row.setDeleted(NOT_DELETED);
            fillRule(row, singleRequest, channel, now);
            ruleMapper.insert(row);
            responses.add(toRuleResponse(row));
        }
        return responses;
    }

    /**
     * 更新渠道预警规则。
     *
     * @param id 规则 ID
     * @param request 保存请求
     * @return 更新后的规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelAlertRuleResponse updateRule(Long id, ChannelAlertRuleSaveRequest request) {
        ChannelAlertRuleDO row = requireRule(id);
        validateRuleRequest(request, id);
        ChannelInfoDO channel = requireChannel(request.getChannelId());
        fillRule(row, request, channel, LocalDateTime.now());
        ruleMapper.updateById(row);
        return toRuleResponse(row);
    }

    /**
     * 查询同一渠道维度下的预警规则集合。
     *
     * @param id 任一规则 ID
     * @return 维度详情
     */
    @Override
    public ChannelAlertRuleDimensionResponse getRuleDimension(Long id) {
        ChannelAlertRuleDO origin = requireRule(id);
        List<ChannelAlertRuleDO> rows = loadDimensionRules(origin);
        return toDimensionResponse(origin, rows);
    }

    /**
     * 更新同一渠道维度下的预警规则集合。
     *
     * @param id 任一规则 ID
     * @param request 维度保存请求
     * @return 更新后的维度详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelAlertRuleDimensionResponse updateRuleDimension(Long id, ChannelAlertRuleDimensionSaveRequest request) {
        ChannelAlertRuleDO origin = requireRule(id);
        BatchRuleContext context = validateBatchRequest(request);
        ensureSameDimension(origin, request);
        ChannelInfoDO channel = requireChannel(request.getChannelId());
        List<ChannelAlertRuleDO> existingRows = loadDimensionRules(origin);
        String groupCode = StringUtils.hasText(origin.getRuleGroupCode()) ? origin.getRuleGroupCode() : origin.getRuleCode();
        Set<Long> existingIds = existingRows.stream().map(ChannelAlertRuleDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, ChannelAlertRuleDO> existingMap = existingRows.stream()
                .collect(Collectors.toMap(row -> ruleComboKey(row.getPaymentMethod(), row.getCardBrand(), row.getRuleType()), Function.identity(), (left, right) -> left));
        Set<String> desiredKeys = new HashSet<>();
        for (ChannelAlertRuleItem item : request.getRules()) {
            ChannelAlertRuleSaveRequest singleRequest = toSingleRequest(request, item, context.dimension());
            ensureRuleUnique(singleRequest, existingIds);
            String comboKey = ruleComboKey(context.dimension().paymentMethod(), context.dimension().cardBrand(), item.getRuleType());
            desiredKeys.add(comboKey);
            ChannelAlertRuleDO row = existingMap.get(comboKey);
            LocalDateTime now = LocalDateTime.now();
            if (row == null) {
                row = new ChannelAlertRuleDO();
                row.setRuleCode(PaymentOrderNoGenerator.nextOrderNo(RULE_CODE_PREFIX, now));
                row.setRuleGroupCode(groupCode);
                row.setCreateBy(currentOperatorName());
                row.setCreateTime(now);
                row.setDeleted(NOT_DELETED);
                fillRule(row, singleRequest, channel, now);
                ruleMapper.insert(row);
            } else {
                row.setRuleGroupCode(groupCode);
                fillRule(row, singleRequest, channel, now);
                ruleMapper.updateById(row);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (ChannelAlertRuleDO row : existingRows) {
            if (!desiredKeys.contains(ruleComboKey(row.getPaymentMethod(), row.getCardBrand(), row.getRuleType()))) {
                row.setDeleted(row.getId());
                row.setRuleStatus(DISABLED);
                row.setUpdateBy(currentOperatorName());
                row.setUpdateTime(now);
                ruleMapper.updateById(row);
            }
        }
        List<ChannelAlertRuleDO> refreshedRows = ruleMapper.selectList(Wrappers.<ChannelAlertRuleDO>lambdaQuery()
                .eq(ChannelAlertRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelAlertRuleDO::getRuleGroupCode, groupCode)
                .orderByAsc(ChannelAlertRuleDO::getCardBrand)
                .orderByAsc(ChannelAlertRuleDO::getRuleType)
                .orderByDesc(ChannelAlertRuleDO::getUpdateTime));
        return toDimensionResponse(origin, refreshedRows);
    }

    /**
     * 查询渠道预警规则表单选项。
     *
     * @param channelId 渠道 ID
     * @param businessType 业务类型
     * @param keyword 搜索关键字
     * @return 表单选项
     */
    @Override
    public ChannelAlertRuleOptionsResponse ruleOptions(Long channelId, String businessType, String keyword) {
        ChannelAlertRuleOptionsResponse response = new ChannelAlertRuleOptionsResponse();
        if (channelId != null) {
            ChannelInfoDO channel = requireChannel(channelId);
            response.setBusinessTypes(supportedBusinessTypes(channel));
            response.setPaymentMethods(loadPaymentMethodOptions(channelId, businessType));
            response.setCardBrands(response.getPaymentMethods().stream()
                    .filter(item -> BANK_CARD.equals(item.getPaymentMethod()))
                    .flatMap(item -> item.getCardBrands().stream())
                    .distinct()
                    .toList());
        }
        response.setUserEmails(loadUserEmailOptions(keyword));
        response.setEmailTemplates(loadEmailTemplateOptions(keyword));
        response.setEmailSceneCodes(response.getEmailTemplates().stream()
                .map(EmailTemplateOption::getSceneCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        return response;
    }

    /**
     * 更新渠道预警规则状态。
     *
     * @param id 规则 ID
     * @param status 状态值
     * @return 更新后的规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelAlertRuleResponse updateRuleStatus(Long id, Integer status) {
        ChannelAlertRuleDO row = requireRule(id);
        row.setRuleStatus(normalizeStatus(status));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(row);
        return toRuleResponse(row);
    }

    /**
     * 删除渠道预警规则。
     *
     * @param id 规则 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ChannelAlertRuleDO row = requireRule(id);
        row.setDeleted(row.getId());
        row.setRuleStatus(DISABLED);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(row);
    }

    /**
     * 分页查询渠道预警事件。
     *
     * @param request 查询条件
     * @return 事件分页结果
     */
    @Override
    public PageResult<ChannelAlertEventResponse> pageEvents(ChannelAlertEventQuery request) {
        ChannelAlertEventQuery query = request == null ? new ChannelAlertEventQuery() : request;
        Page<ChannelAlertEventDO> page = eventMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildEventQuery(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toEventResponse).toList());
    }

    /**
     * 查询渠道预警事件详情。
     *
     * @param id 事件 ID
     * @return 事件详情
     */
    @Override
    public ChannelAlertEventResponse getEvent(Long id) {
        return toEventResponse(requireEvent(id));
    }

    /**
     * 人工确认渠道预警事件。
     *
     * @param id 事件 ID
     * @param request 确认请求
     * @return 确认后的事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelAlertEventResponse acknowledgeEvent(Long id, AlertEventAcknowledgeRequest request) {
        ChannelAlertEventDO row = requireEvent(id);
        row.setEventStatus(EVENT_STATUS_ACKNOWLEDGED);
        row.setAcknowledgedBy(currentOperatorName());
        row.setAcknowledgedTime(LocalDateTime.now());
        row.setRemark(trimToNull(request == null ? null : request.getRemark()));
        row.setUpdateTime(LocalDateTime.now());
        eventMapper.updateById(row);
        return toEventResponse(row);
    }

    /**
     * 删除渠道预警事件。
     *
     * @param id 事件 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long id) {
        ChannelAlertEventDO row = requireEvent(id);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        eventMapper.updateById(row);
    }

    /**
     * 分页查询渠道预警通知日志。
     *
     * @param request 查询条件
     * @return 通知日志分页结果
     */
    @Override
    public PageResult<ChannelAlertNotifyLogResponse> pageNotifyLogs(ChannelAlertNotifyLogQuery request) {
        ChannelAlertNotifyLogQuery query = request == null ? new ChannelAlertNotifyLogQuery() : request;
        Page<ChannelAlertNotifyLogDO> page = notifyLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildNotifyLogQuery(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toNotifyLogResponse).toList());
    }

    private LambdaQueryWrapper<ChannelAlertRuleDO> buildRuleQuery(ChannelAlertRuleQuery query) {
        return Wrappers.<ChannelAlertRuleDO>lambdaQuery()
                .eq(ChannelAlertRuleDO::getDeleted, NOT_DELETED)
                .eq(query.getChannelId() != null, ChannelAlertRuleDO::getChannelId, query.getChannelId())
                .like(StringUtils.hasText(query.getChannelCode()), ChannelAlertRuleDO::getChannelCode, trim(query.getChannelCode()))
                .eq(StringUtils.hasText(query.getBusinessType()), ChannelAlertRuleDO::getBusinessType, normalizeCode(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getPaymentMethod()), ChannelAlertRuleDO::getPaymentMethod, defaultScope(query.getPaymentMethod()))
                .eq(StringUtils.hasText(query.getCardBrand()), ChannelAlertRuleDO::getCardBrand, defaultScope(query.getCardBrand()))
                .eq(StringUtils.hasText(query.getRuleType()), ChannelAlertRuleDO::getRuleType, normalizeCode(query.getRuleType()))
                .eq(StringUtils.hasText(query.getAlertLevel()), ChannelAlertRuleDO::getAlertLevel, normalizeCode(query.getAlertLevel()))
                .eq(query.getRuleStatus() != null, ChannelAlertRuleDO::getRuleStatus, query.getRuleStatus())
                .like(StringUtils.hasText(query.getRuleName()), ChannelAlertRuleDO::getRuleName, trim(query.getRuleName()))
                .orderByDesc(ChannelAlertRuleDO::getUpdateTime)
                .orderByDesc(ChannelAlertRuleDO::getId);
    }

    private LambdaQueryWrapper<ChannelAlertEventDO> buildEventQuery(ChannelAlertEventQuery query) {
        return Wrappers.<ChannelAlertEventDO>lambdaQuery()
                .eq(ChannelAlertEventDO::getDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getEventCode()), ChannelAlertEventDO::getEventCode, trim(query.getEventCode()))
                .eq(query.getRuleId() != null, ChannelAlertEventDO::getRuleId, query.getRuleId())
                .like(StringUtils.hasText(query.getRuleCode()), ChannelAlertEventDO::getRuleCode, trim(query.getRuleCode()))
                .like(StringUtils.hasText(query.getRuleName()), ChannelAlertEventDO::getRuleName, trim(query.getRuleName()))
                .eq(query.getChannelId() != null, ChannelAlertEventDO::getChannelId, query.getChannelId())
                .like(StringUtils.hasText(query.getChannelCode()), ChannelAlertEventDO::getChannelCode, trim(query.getChannelCode()))
                .eq(StringUtils.hasText(query.getBusinessType()), ChannelAlertEventDO::getBusinessType, normalizeCode(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getPaymentMethod()), ChannelAlertEventDO::getPaymentMethod, defaultScope(query.getPaymentMethod()))
                .eq(StringUtils.hasText(query.getCardBrand()), ChannelAlertEventDO::getCardBrand, defaultScope(query.getCardBrand()))
                .eq(StringUtils.hasText(query.getRuleType()), ChannelAlertEventDO::getRuleType, normalizeCode(query.getRuleType()))
                .eq(StringUtils.hasText(query.getAlertLevel()), ChannelAlertEventDO::getAlertLevel, normalizeCode(query.getAlertLevel()))
                .eq(StringUtils.hasText(query.getEventStatus()), ChannelAlertEventDO::getEventStatus, normalizeCode(query.getEventStatus()))
                .ge(query.getTriggerStartTime() != null, ChannelAlertEventDO::getTriggerTime, query.getTriggerStartTime())
                .le(query.getTriggerEndTime() != null, ChannelAlertEventDO::getTriggerTime, query.getTriggerEndTime())
                .orderByDesc(ChannelAlertEventDO::getTriggerTime)
                .orderByDesc(ChannelAlertEventDO::getId);
    }

    /**
     * 执行 build Notify Log Query 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private LambdaQueryWrapper<ChannelAlertNotifyLogDO> buildNotifyLogQuery(ChannelAlertNotifyLogQuery query) {
        return Wrappers.<ChannelAlertNotifyLogDO>lambdaQuery()
                .eq(ChannelAlertNotifyLogDO::getDeleted, NOT_DELETED)
                .eq(query.getEventId() != null, ChannelAlertNotifyLogDO::getEventId, query.getEventId())
                .like(StringUtils.hasText(query.getEventCode()), ChannelAlertNotifyLogDO::getEventCode, trim(query.getEventCode()))
                .eq(query.getRuleId() != null, ChannelAlertNotifyLogDO::getRuleId, query.getRuleId())
                .like(StringUtils.hasText(query.getRuleCode()), ChannelAlertNotifyLogDO::getRuleCode, trim(query.getRuleCode()))
                .eq(StringUtils.hasText(query.getNotifyType()), ChannelAlertNotifyLogDO::getNotifyType, normalizeCode(query.getNotifyType()))
                .eq(StringUtils.hasText(query.getNotifyStatus()), ChannelAlertNotifyLogDO::getNotifyStatus, normalizeCode(query.getNotifyStatus()))
                .ge(query.getCreateStartTime() != null, ChannelAlertNotifyLogDO::getCreateTime, query.getCreateStartTime())
                .le(query.getCreateEndTime() != null, ChannelAlertNotifyLogDO::getCreateTime, query.getCreateEndTime())
                .orderByDesc(ChannelAlertNotifyLogDO::getCreateTime)
                .orderByDesc(ChannelAlertNotifyLogDO::getId);
    }

    /**
     * 执行 validate Rule Request 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateRuleRequest(ChannelAlertRuleSaveRequest request, Long excludeId) {
        if (request == null) {
            throw badRequest("预警规则请求不能为空");
        }
        String businessType = normalizeCode(request.getBusinessType());
        String ruleType = normalizeCode(request.getRuleType());
        String alertLevel = normalizeCode(request.getAlertLevel());
        if (!BUSINESS_TYPES.contains(businessType)) {
            throw badRequest("业务类型不支持");
        }
        if (!RULE_TYPES.contains(ruleType)) {
            throw badRequest("规则类型不支持");
        }
        if (!ALERT_LEVELS.contains(alertLevel)) {
            throw badRequest("异常级别不支持");
        }
        if (request.getWindowMinutes() == null || request.getWindowMinutes() <= 0 || request.getWindowMinutes() > 43200) {
            throw badRequest("时间窗口必须在 1 到 43200 分钟之间");
        }
        validateThreshold(ruleType, request);
        validateEmails(request.getEmailRecipients(), true);
        validateEmails(request.getEmailCc(), false);
        ensureRuleUnique(request, excludeId);
    }

    /**
     * 执行 validate Batch Request 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private BatchRuleContext validateBatchRequest(ChannelAlertRuleBatchSaveRequest request) {
        if (request == null) {
            throw badRequest("预警规则请求不能为空");
        }
        if (!StringUtils.hasText(request.getRuleName())) {
            throw badRequest("规则名称不能为空");
        }
        if (request.getChannelId() == null) {
            throw badRequest("渠道不能为空");
        }
        String businessType = normalizeCode(request.getBusinessType());
        if (!BUSINESS_TYPES.contains(businessType)) {
            throw badRequest("业务类型不支持");
        }
        if (!EMAIL.equals(normalizeCode(request.getNotifyType()))) {
            throw badRequest("通知方式目前仅支持邮件");
        }
        if (request.getRules() == null || request.getRules().isEmpty()) {
            throw badRequest("至少需要配置一个规则类型");
        }
        validateEmails(request.getEmailRecipients(), true);
        validateEmails(request.getEmailCc(), false);
        Set<String> ruleTypes = new HashSet<>();
        for (ChannelAlertRuleItem item : request.getRules()) {
            if (item == null) {
                throw badRequest("规则配置不能为空");
            }
            ChannelAlertRuleSaveRequest singleRequest = toSingleRequest(request, item, new RuleDimension(defaultScope(request.getPaymentMethod()), ALL));
            String ruleType = normalizeCode(item.getRuleType());
            if (!RULE_TYPES.contains(ruleType)) {
                throw badRequest("规则类型不支持");
            }
            if (!ruleTypes.add(ruleType)) {
                throw badRequest("规则类型不能重复配置");
            }
            String alertLevel = normalizeCode(item.getAlertLevel());
            if (!ALERT_LEVELS.contains(alertLevel)) {
                throw badRequest("异常级别不支持");
            }
            if (item.getWindowMinutes() == null || item.getWindowMinutes() <= 0 || item.getWindowMinutes() > 43200) {
                throw badRequest("时间窗口必须在 1 到 43200 分钟之间");
            }
            validateThreshold(ruleType, singleRequest);
        }
        return new BatchRuleContext(resolveRuleDimension(request));
    }

    /**
     * 执行 validate Threshold 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param ruleType rule Type 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    private void validateThreshold(String ruleType, ChannelAlertRuleSaveRequest request) {
        String thresholdType = RULE_TYPE_THRESHOLD.get(ruleType);
        if ("COUNT".equals(thresholdType)) {
            if (request.getThresholdCount() == null || request.getThresholdCount() <= 0) {
                throw badRequest("连续失败规则必须配置正整数笔数阈值");
            }
            return;
        }
        if ("RATE".equals(thresholdType)) {
            if (request.getThresholdRate() == null
                    || request.getThresholdRate().compareTo(BigDecimal.ZERO) <= 0
                    || request.getThresholdRate().compareTo(new BigDecimal("100")) > 0) {
                throw badRequest("比例类规则阈值必须在 0 到 100 之间");
            }
            if (request.getMinimumSampleCount() == null || request.getMinimumSampleCount() <= 0) {
                throw badRequest("比例类规则必须配置最小样本数");
            }
            return;
        }
        if ("MILLIS".equals(thresholdType)
                && (request.getThresholdMillis() == null || request.getThresholdMillis() <= 0)) {
            throw badRequest("延迟规则必须配置正整数毫秒阈值");
        }
    }

    /**
     * 执行 ensure Rule Unique 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void ensureRuleUnique(ChannelAlertRuleSaveRequest request, Long excludeId) {
        Set<Long> excludeIds = excludeId == null ? Collections.emptySet() : Set.of(excludeId);
        ensureRuleUnique(request, excludeIds);
    }

    /**
     * 执行 ensure Rule Unique 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @param excludeIds exclude Ids 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void ensureRuleUnique(ChannelAlertRuleSaveRequest request, Set<Long> excludeIds) {
        Long count = ruleMapper.selectCount(Wrappers.<ChannelAlertRuleDO>lambdaQuery()
                .eq(ChannelAlertRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelAlertRuleDO::getChannelId, request.getChannelId())
                .eq(ChannelAlertRuleDO::getBusinessType, normalizeCode(request.getBusinessType()))
                .eq(ChannelAlertRuleDO::getPaymentMethod, defaultScope(request.getPaymentMethod()))
                .eq(ChannelAlertRuleDO::getCardBrand, defaultScope(request.getCardBrand()))
                .eq(ChannelAlertRuleDO::getRuleType, normalizeCode(request.getRuleType()))
                .notIn(excludeIds != null && !excludeIds.isEmpty(), ChannelAlertRuleDO::getId, excludeIds));
        if (count != null && count > 0) {
            throw badRequest("同一渠道、业务类型、支付方式、卡品牌和规则类型下只能配置一条预警规则");
        }
    }

    /**
     * 执行 ensure Same Dimension 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param origin origin 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    private void ensureSameDimension(ChannelAlertRuleDO origin, ChannelAlertRuleBatchSaveRequest request) {
        if (!Objects.equals(origin.getChannelId(), request.getChannelId())
                || !Objects.equals(origin.getBusinessType(), normalizeCode(request.getBusinessType()))
                || !Objects.equals(origin.getPaymentMethod(), normalizeCode(request.getPaymentMethod()))) {
            throw badRequest("编辑时不支持修改渠道、业务类型或支付方式维度");
        }
    }

/**
 * 执行 to Single Request 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param item item 输入值，含义由调用方法名称和所属业务对象限定
 * @param dimension dimension 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
 */
    private ChannelAlertRuleSaveRequest toSingleRequest(ChannelAlertRuleBatchSaveRequest request,
                                                        ChannelAlertRuleItem item,
                                                        RuleDimension dimension) {
        ChannelAlertRuleSaveRequest singleRequest = new ChannelAlertRuleSaveRequest();
        singleRequest.setRuleName(request.getRuleName());
        singleRequest.setChannelId(request.getChannelId());
        singleRequest.setBusinessType(request.getBusinessType());
        singleRequest.setPaymentMethod(dimension.paymentMethod());
        singleRequest.setCardBrand(dimension.cardBrand());
        singleRequest.setRuleType(item == null ? null : item.getRuleType());
        singleRequest.setWindowMinutes(item == null ? null : item.getWindowMinutes());
        singleRequest.setThresholdCount(item == null ? null : item.getThresholdCount());
        singleRequest.setThresholdRate(item == null ? null : item.getThresholdRate());
        singleRequest.setThresholdMillis(item == null ? null : item.getThresholdMillis());
        singleRequest.setMinimumSampleCount(item == null ? null : item.getMinimumSampleCount());
        singleRequest.setAlertLevel(item == null ? null : item.getAlertLevel());
        singleRequest.setRuleDescription(item == null ? null : item.getRuleDescription());
        singleRequest.setAutoDegrade(item == null ? null : item.getAutoDegrade());
        singleRequest.setAutoCircuitBreak(item == null ? null : item.getAutoCircuitBreak());
        singleRequest.setRuleStatus(request.getRuleStatus());
        singleRequest.setEmailRecipients(request.getEmailRecipients());
        singleRequest.setEmailCc(request.getEmailCc());
        singleRequest.setEmailTemplateCode(request.getEmailTemplateCode());
        singleRequest.setEmailSceneCode(request.getEmailSceneCode());
        singleRequest.setRemark(request.getRemark());
        return singleRequest;
    }

    /**
     * 执行 resolve Rule Dimension 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
     */
    private RuleDimension resolveRuleDimension(ChannelAlertRuleBatchSaveRequest request) {
        List<PaymentMethodOption> paymentOptions = loadPaymentMethodOptions(request.getChannelId(), request.getBusinessType());
        String requestedPaymentMethod = defaultScope(request.getPaymentMethod());
        if (paymentOptions.isEmpty()
                || (!ALL.equals(requestedPaymentMethod) && paymentOptions.stream().noneMatch(option -> Objects.equals(option.getPaymentMethod(), requestedPaymentMethod)))) {
            throw badRequest("渠道未配置可用支付方式");
        }
        if (BANK_CARD.equals(requestedPaymentMethod)) {
            PaymentMethodOption payment = paymentOptions.stream()
                    .filter(option -> BANK_CARD.equals(option.getPaymentMethod()))
                    .findFirst()
                    .orElseThrow(() -> badRequest("渠道未配置可用支付方式"));
            return new RuleDimension(requestedPaymentMethod, resolveCardBrandScope(payment, request.getCardBrands()));
        }
        return new RuleDimension(requestedPaymentMethod, ALL);
    }

    /**
     * 执行 resolve Card Brand Scope 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param payment payment 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestedCardBrands 卡相关输入，属于敏感或可识别数据，禁止直接写入日志
     * @return 解析或查询得到的业务值
     */
    private String resolveCardBrandScope(PaymentMethodOption payment, List<String> requestedCardBrands) {
        List<String> supportedBrands = payment.getCardBrands() == null ? Collections.emptyList() : payment.getCardBrands();
        if (supportedBrands.isEmpty()) {
            throw badRequest("卡支付方式未配置可用卡品牌");
        }
        LinkedHashSet<String> requestedValues = new LinkedHashSet<>();
        if (requestedCardBrands != null) {
            requestedCardBrands.stream()
                    .map(this::defaultScope)
                    .forEach(requestedValues::add);
        }
        if (requestedValues.isEmpty() || requestedValues.contains(ALL)) {
            return ALL;
        }
        List<String> selectedBrands = requestedValues.stream()
                .filter(supportedBrands::contains)
                .toList();
        if (selectedBrands.isEmpty() || selectedBrands.size() != requestedValues.size()) {
            throw badRequest("渠道未配置所选卡品牌");
        }
        return String.join(",", selectedBrands);
    }

    /**
     * 执行 load Dimension Rules 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param origin origin 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<ChannelAlertRuleDO> loadDimensionRules(ChannelAlertRuleDO origin) {
        LambdaQueryWrapper<ChannelAlertRuleDO> wrapper = Wrappers.<ChannelAlertRuleDO>lambdaQuery()
                .eq(ChannelAlertRuleDO::getDeleted, NOT_DELETED);
        if (StringUtils.hasText(origin.getRuleGroupCode())) {
            wrapper.eq(ChannelAlertRuleDO::getRuleGroupCode, origin.getRuleGroupCode());
        } else {
            wrapper.eq(ChannelAlertRuleDO::getChannelId, origin.getChannelId())
                    .eq(ChannelAlertRuleDO::getBusinessType, origin.getBusinessType())
                    .eq(ChannelAlertRuleDO::getPaymentMethod, origin.getPaymentMethod())
                    .eq(ChannelAlertRuleDO::getCardBrand, origin.getCardBrand());
        }
        return ruleMapper.selectList(wrapper
                .orderByAsc(ChannelAlertRuleDO::getCardBrand)
                .orderByAsc(ChannelAlertRuleDO::getRuleType)
                .orderByDesc(ChannelAlertRuleDO::getUpdateTime));
    }

    /**
     * 执行 to Dimension Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param origin origin 输入值，含义由调用方法名称和所属业务对象限定
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private ChannelAlertRuleDimensionResponse toDimensionResponse(ChannelAlertRuleDO origin, List<ChannelAlertRuleDO> rows) {
        List<ChannelAlertRuleDO> items = rows == null || rows.isEmpty() ? List.of(origin) : rows;
        ChannelAlertRuleDO first = items.get(0);
        ChannelAlertRuleDimensionResponse response = new ChannelAlertRuleDimensionResponse();
        response.setRuleName(first.getRuleName());
        response.setChannelId(first.getChannelId());
        response.setChannelCode(first.getChannelCode());
        response.setChannelName(channelName(first.getChannelId()));
        response.setBusinessType(first.getBusinessType());
        response.setPaymentMethod(first.getPaymentMethod());
        response.setCardBrands(splitScopeValues(first.getCardBrand()));
        response.setRules(items.stream()
                .sorted(Comparator.comparing(ChannelAlertRuleDO::getCardBrand).thenComparing(ChannelAlertRuleDO::getRuleType))
                .map(this::toRuleResponse)
                .toList());
        response.setNotifyType(first.getNotifyType());
        response.setEmailRecipients(first.getEmailRecipients());
        response.setEmailCc(first.getEmailCc());
        response.setEmailTemplateCode(first.getEmailTemplateCode());
        response.setEmailSceneCode(first.getEmailSceneCode());
        response.setRuleStatus(first.getRuleStatus());
        response.setRemark(first.getRemark());
        return response;
    }

    /**
     * 执行 supported Business Types 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channel channel 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private List<String> supportedBusinessTypes(ChannelInfoDO channel) {
        List<String> values = new ArrayList<>();
        if (channel.getSupportAcquiring() != null && channel.getSupportAcquiring() == ENABLED) {
            values.add("ACQUIRING");
        }
        if (channel.getSupportPayout() != null && channel.getSupportPayout() == ENABLED) {
            values.add("PAYOUT");
        }
        return values;
    }

    /**
     * 执行 load Payment Method Options 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelId channel Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param businessType business Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<PaymentMethodOption> loadPaymentMethodOptions(Long channelId, String businessType) {
        List<ChannelPaymentCapabilityDO> capabilities = capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(StringUtils.hasText(businessType), ChannelPaymentCapabilityDO::getBusinessType, normalizeCode(businessType))
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByAsc(ChannelPaymentCapabilityDO::getPaymentMethod));
        if (capabilities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<String>> cardBrandMap = loadCapabilityCardBrands(capabilities);
        Map<String, PaymentMethodOption> optionMap = new java.util.LinkedHashMap<>();
        for (ChannelPaymentCapabilityDO capability : capabilities) {
            String key = capability.getBusinessType() + ":" + capability.getPaymentMethod();
            PaymentMethodOption option = optionMap.computeIfAbsent(key, ignored -> {
                PaymentMethodOption item = new PaymentMethodOption();
                item.setBusinessType(capability.getBusinessType());
                item.setPaymentMethod(capability.getPaymentMethod());
                item.setCardBrands(new ArrayList<>());
                return item;
            });
            option.getCardBrands().addAll(cardBrandMap.getOrDefault(capability.getId(), Collections.emptyList()));
        }
        optionMap.values().forEach(option -> option.setCardBrands(option.getCardBrands().stream().distinct().toList()));
        return new ArrayList<>(optionMap.values());
    }

    /**
     * 执行 load Capability Card Brands 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param capabilities capabilities 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private Map<Long, List<String>> loadCapabilityCardBrands(List<ChannelPaymentCapabilityDO> capabilities) {
        List<Long> capabilityIds = capabilities.stream()
                .filter(item -> BANK_CARD.equals(item.getPaymentMethod()))
                .map(ChannelPaymentCapabilityDO::getId)
                .toList();
        if (capabilityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                        .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCardBrandDO::getBrandStatus, ENABLED)
                        .in(ChannelCapabilityCardBrandDO::getCapabilityId, capabilityIds)
                        .orderByAsc(ChannelCapabilityCardBrandDO::getSortOrder)
                        .orderByAsc(ChannelCapabilityCardBrandDO::getCardBrand))
                .stream()
                .collect(Collectors.groupingBy(ChannelCapabilityCardBrandDO::getCapabilityId,
                        Collectors.mapping(ChannelCapabilityCardBrandDO::getCardBrand, Collectors.toList())));
    }

    /**
     * 执行 load User Email Options 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param keyword keyword 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<UserEmailOption> loadUserEmailOptions(String keyword) {
        SysAppDO app = sysAppMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, ADMIN_APP_CODE)
                .eq(SysAppDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            return Collections.emptyList();
        }
        String query = trimToNull(keyword);
        List<Long> userIdsByRealName = loadUserIdsByRealName(query);
        LambdaQueryWrapper<SysAccountDO> wrapper = Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, app.getId())
                .eq(SysAccountDO::getStatus, ENABLED)
                .eq(SysAccountDO::getDeleted, NOT_DELETED)
                .isNotNull(SysAccountDO::getEmail)
                .ne(SysAccountDO::getEmail, "");
        if (StringUtils.hasText(query)) {
            wrapper.and(nested -> {
                nested.like(SysAccountDO::getLoginAccount, query)
                        .or().like(SysAccountDO::getEmail, query);
                if (!userIdsByRealName.isEmpty()) {
                    nested.or().in(SysAccountDO::getUserId, userIdsByRealName);
                }
            });
        }
        List<SysAccountDO> accounts = sysAccountMapper.selectList(wrapper
                .orderByAsc(SysAccountDO::getLoginAccount)
                .last("LIMIT 50"));
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, SysUserDO> userMap = sysUserMapper.selectList(Wrappers.<SysUserDO>lambdaQuery()
                        .in(SysUserDO::getId, accounts.stream().map(SysAccountDO::getUserId).toList())
                        .eq(SysUserDO::getDeleted, NOT_DELETED))
                .stream()
                .collect(Collectors.toMap(SysUserDO::getId, Function.identity(), (left, right) -> left));
        Map<String, UserEmailOption> emailMap = new java.util.LinkedHashMap<>();
        for (SysAccountDO account : accounts) {
            String email = trim(account.getEmail());
            if (!StringUtils.hasText(email)) {
                continue;
            }
            String key = email.toLowerCase(Locale.ROOT);
            emailMap.computeIfAbsent(key, ignored -> {
                SysUserDO user = userMap.get(account.getUserId());
                UserEmailOption option = new UserEmailOption();
                option.setAccountId(account.getId());
                option.setLoginAccount(account.getLoginAccount());
                option.setRealName(user == null ? null : user.getRealName());
                option.setEmail(email);
                return option;
            });
        }
        return new ArrayList<>(emailMap.values());
    }

    /**
     * 执行 load User Ids By Real Name 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param keyword keyword 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<Long> loadUserIdsByRealName(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return sysUserMapper.selectList(Wrappers.<SysUserDO>lambdaQuery()
                        .like(SysUserDO::getRealName, keyword)
                        .eq(SysUserDO::getStatus, ENABLED)
                        .eq(SysUserDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 50"))
                .stream()
                .map(SysUserDO::getId)
                .toList();
    }

    /**
     * 执行 load Email Template Options 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param keyword keyword 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<EmailTemplateOption> loadEmailTemplateOptions(String keyword) {
        String query = trimToNull(keyword);
        LambdaQueryWrapper<EmailTemplateDO> wrapper = Wrappers.<EmailTemplateDO>lambdaQuery()
                .eq(EmailTemplateDO::getDeleted, NOT_DELETED)
                .eq(EmailTemplateDO::getStatus, ENABLED)
                .in(EmailTemplateDO::getAppCode, List.of(ADMIN_APP_CODE, "COMMON"));
        if (StringUtils.hasText(query)) {
            wrapper.and(nested -> nested
                    .like(EmailTemplateDO::getTemplateCode, query)
                    .or().like(EmailTemplateDO::getTemplateName, query)
                    .or().like(EmailTemplateDO::getSceneCode, query));
        }
        List<EmailTemplateOption> options = emailTemplateMapper.selectList(wrapper
                        .orderByAsc(EmailTemplateDO::getSceneCode)
                        .orderByAsc(EmailTemplateDO::getTemplateCode)
                        .last("LIMIT 50"))
                .stream()
                .map(this::toEmailTemplateOption)
                .toList();
        if (options.stream().noneMatch(item -> DEFAULT_EMAIL_SCENE.equals(item.getSceneCode()))) {
            EmailTemplateOption option = new EmailTemplateOption();
            option.setTemplateCode("CHANNEL_ALERT_DEFAULT");
            option.setTemplateName("渠道预警通知模板");
            option.setSceneCode(DEFAULT_EMAIL_SCENE);
            option.setLocale(DEFAULT_EMAIL_LOCALE);
            return java.util.stream.Stream.concat(options.stream(), java.util.stream.Stream.of(option)).toList();
        }
        return options;
    }

    /**
     * 执行 to Email Template Option 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private EmailTemplateOption toEmailTemplateOption(EmailTemplateDO row) {
        EmailTemplateOption option = new EmailTemplateOption();
        option.setId(row.getId());
        option.setTemplateCode(row.getTemplateCode());
        option.setTemplateName(row.getTemplateName());
        option.setSceneCode(row.getSceneCode());
        option.setLocale(row.getLocale());
        return option;
    }

    /**
     * 执行 rule Combo Key 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param paymentMethod payment Method 输入值，含义由调用方法名称和所属业务对象限定
     * @param cardBrand 卡相关输入，属于敏感或可识别数据，禁止直接写入日志
     * @param ruleType rule Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String ruleComboKey(String paymentMethod, String cardBrand, String ruleType) {
        return defaultScope(paymentMethod) + ":" + defaultScope(cardBrand) + ":" + normalizeCode(ruleType);
    }

    /**
     * 执行 fill Rule 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @param channel channel 输入值，含义由调用方法名称和所属业务对象限定
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void fillRule(ChannelAlertRuleDO row, ChannelAlertRuleSaveRequest request, ChannelInfoDO channel, LocalDateTime now) {
        row.setRuleName(trim(request.getRuleName()));
        row.setChannelId(channel.getId());
        row.setChannelCode(channel.getChannelCode());
        row.setBusinessType(normalizeCode(request.getBusinessType()));
        row.setPaymentMethod(defaultScope(request.getPaymentMethod()));
        row.setCardBrand(defaultScope(request.getCardBrand()));
        row.setRuleType(normalizeCode(request.getRuleType()));
        row.setWindowMinutes(request.getWindowMinutes());
        row.setThresholdCount(request.getThresholdCount());
        row.setThresholdRate(request.getThresholdRate());
        row.setThresholdMillis(request.getThresholdMillis());
        row.setMinimumSampleCount(request.getMinimumSampleCount() == null ? 1 : request.getMinimumSampleCount());
        row.setAlertLevel(normalizeCode(request.getAlertLevel()));
        row.setRuleDescription(trimToNull(request.getRuleDescription()));
        row.setAutoDegrade(normalizeStatus(request.getAutoDegrade()));
        row.setAutoCircuitBreak(normalizeStatus(request.getAutoCircuitBreak()));
        row.setRuleStatus(normalizeStatus(request.getRuleStatus()));
        row.setNotifyType(EMAIL);
        row.setEmailRecipients(normalizeEmails(request.getEmailRecipients()));
        row.setEmailCc(normalizeEmails(request.getEmailCc()));
        row.setEmailTemplateCode(normalizeOptionalCode(request.getEmailTemplateCode()));
        row.setEmailSceneCode(defaultIfBlank(normalizeOptionalCode(request.getEmailSceneCode()), DEFAULT_EMAIL_SCENE));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(now);
    }

    /**
     * 执行 require Rule 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ChannelAlertRuleDO requireRule(Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "id is required");
        }
        ChannelAlertRuleDO row = ruleMapper.selectOne(Wrappers.<ChannelAlertRuleDO>lambdaQuery()
                .eq(ChannelAlertRuleDO::getId, id)
                .eq(ChannelAlertRuleDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道预警规则不存在");
        }
        return row;
    }

    /**
     * 执行 require Event 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ChannelAlertEventDO requireEvent(Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "id is required");
        }
        ChannelAlertEventDO row = eventMapper.selectOne(Wrappers.<ChannelAlertEventDO>lambdaQuery()
                .eq(ChannelAlertEventDO::getId, id)
                .eq(ChannelAlertEventDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道预警事件不存在");
        }
        return row;
    }

    /**
     * 执行 require Channel 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelId channel Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ChannelInfoDO requireChannel(Long channelId) {
        if (channelId == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "channelId is required");
        }
        ChannelInfoDO row = channelInfoMapper.selectOne(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getId, channelId)
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道不存在");
        }
        return row;
    }

    /**
     * 执行 to Rule Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private ChannelAlertRuleResponse toRuleResponse(ChannelAlertRuleDO row) {
        ChannelAlertRuleResponse response = new ChannelAlertRuleResponse();
        response.setId(row.getId());
        response.setRuleCode(row.getRuleCode());
        response.setRuleGroupCode(row.getRuleGroupCode());
        response.setRuleName(row.getRuleName());
        response.setChannelId(row.getChannelId());
        response.setChannelCode(row.getChannelCode());
        response.setChannelName(channelName(row.getChannelId()));
        response.setBusinessType(row.getBusinessType());
        response.setPaymentMethod(row.getPaymentMethod());
        response.setCardBrand(row.getCardBrand());
        response.setRuleType(row.getRuleType());
        response.setWindowMinutes(row.getWindowMinutes());
        response.setThresholdCount(row.getThresholdCount());
        response.setThresholdRate(row.getThresholdRate());
        response.setThresholdMillis(row.getThresholdMillis());
        response.setMinimumSampleCount(row.getMinimumSampleCount());
        response.setAlertLevel(row.getAlertLevel());
        response.setRuleDescription(row.getRuleDescription());
        response.setAutoDegrade(row.getAutoDegrade());
        response.setAutoCircuitBreak(row.getAutoCircuitBreak());
        response.setRuleStatus(row.getRuleStatus());
        response.setNotifyType(row.getNotifyType());
        response.setEmailRecipients(row.getEmailRecipients());
        response.setEmailCc(row.getEmailCc());
        response.setEmailTemplateCode(row.getEmailTemplateCode());
        response.setEmailSceneCode(row.getEmailSceneCode());
        response.setRemark(row.getRemark());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 执行 to Event Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private ChannelAlertEventResponse toEventResponse(ChannelAlertEventDO row) {
        ChannelAlertEventResponse response = new ChannelAlertEventResponse();
        response.setId(row.getId());
        response.setEventCode(row.getEventCode());
        response.setRuleId(row.getRuleId());
        response.setRuleCode(row.getRuleCode());
        response.setRuleName(row.getRuleName());
        response.setChannelId(row.getChannelId());
        response.setChannelCode(row.getChannelCode());
        response.setChannelName(channelName(row.getChannelId()));
        response.setBusinessType(row.getBusinessType());
        response.setPaymentMethod(row.getPaymentMethod());
        response.setCardBrand(row.getCardBrand());
        response.setRuleType(row.getRuleType());
        response.setAlertLevel(row.getAlertLevel());
        response.setWindowMinutes(row.getWindowMinutes());
        response.setWindowStartTime(row.getWindowStartTime());
        response.setWindowEndTime(row.getWindowEndTime());
        response.setSampleCount(row.getSampleCount());
        response.setFailureCount(row.getFailureCount());
        response.setSuccessCount(row.getSuccessCount());
        response.setSuccessRate(row.getSuccessRate());
        response.setErrorRate(row.getErrorRate());
        response.setMaxContinuousFailureCount(row.getMaxContinuousFailureCount());
        response.setAverageLatencyMillis(row.getAverageLatencyMillis());
        response.setTriggerValueCount(row.getTriggerValueCount());
        response.setTriggerValueRate(row.getTriggerValueRate());
        response.setTriggerValueMillis(row.getTriggerValueMillis());
        response.setThresholdSnapshot(row.getThresholdSnapshot());
        response.setEventStatus(row.getEventStatus());
        response.setNotifyStatus(row.getNotifyStatus());
        response.setTriggerTime(row.getTriggerTime());
        response.setAcknowledgedTime(row.getAcknowledgedTime());
        response.setAcknowledgedBy(row.getAcknowledgedBy());
        response.setRemark(row.getRemark());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 执行 to Notify Log Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private ChannelAlertNotifyLogResponse toNotifyLogResponse(ChannelAlertNotifyLogDO row) {
        ChannelAlertNotifyLogResponse response = new ChannelAlertNotifyLogResponse();
        response.setId(row.getId());
        response.setEventId(row.getEventId());
        response.setEventCode(row.getEventCode());
        response.setRuleId(row.getRuleId());
        response.setRuleCode(row.getRuleCode());
        response.setNotifyType(row.getNotifyType());
        response.setNotifyStatus(row.getNotifyStatus());
        response.setEmailRecipients(row.getEmailRecipients());
        response.setEmailCc(row.getEmailCc());
        response.setEmailTemplateCode(row.getEmailTemplateCode());
        response.setEmailSceneCode(row.getEmailSceneCode());
        response.setSendStartTime(row.getSendStartTime());
        response.setSendEndTime(row.getSendEndTime());
        response.setFailReason(row.getFailReason());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 执行 channel Name 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelId channel Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String channelName(Long channelId) {
        if (channelId == null) {
            return "";
        }
        ChannelInfoDO channel = channelInfoMapper.selectById(channelId);
        if (channel == null || !isNotDeleted(channel.getDeleted())) {
            return "";
        }
        return StringUtils.hasText(channel.getChannelCnName()) ? channel.getChannelCnName() : channel.getChannelEnName();
    }

    /**
     * 执行 is Not Deleted 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param deleted deleted 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isNotDeleted(Long deleted) {
        return deleted != null && deleted.longValue() == NOT_DELETED;
    }

    /**
     * 执行 validate Emails 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param emails emails 输入值，含义由调用方法名称和所属业务对象限定
     * @param required required 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateEmails(String emails, boolean required) {
        if (!StringUtils.hasText(emails)) {
            if (required) {
                throw badRequest("邮件收件人不能为空");
            }
            return;
        }
        for (String email : emails.split(",")) {
            String value = trim(email);
            if (!StringUtils.hasText(value) || !EMAIL_PATTERN.matcher(value).matches()) {
                throw badRequest("邮箱格式不正确：" + value);
            }
        }
    }

    /**
     * 执行 normalize Emails 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param emails emails 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizeEmails(String emails) {
        if (!StringUtils.hasText(emails)) {
            return null;
        }
        return String.join(",", java.util.Arrays.stream(emails.split(","))
                .map(this::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
    }

    /**
     * 执行 default Scope 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String defaultScope(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : ALL;
    }

    /**
     * 执行 split Scope Values 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private List<String> splitScopeValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of(ALL);
        }
        return java.util.Arrays.stream(value.split(","))
                .map(this::defaultScope)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 执行 normalize Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 执行 normalize Optional Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalizeOptionalCode(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : null;
    }

    /**
     * 执行 default If Blank 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
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
     * 执行 normalize Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 标准化后的业务字段值
     */
    private Integer normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    /**
     * 执行 trim 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 执行 trim To Null 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 执行 current Operator Name 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
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
     * 执行 bad Request 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private record BatchRuleContext(RuleDimension dimension) {
    }

    private record RuleDimension(String paymentMethod, String cardBrand) {
    }
}

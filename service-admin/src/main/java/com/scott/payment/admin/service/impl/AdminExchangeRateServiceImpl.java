package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateBatchSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.GenerateBusinessRateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateRuleDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateSourceDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateUsageSnapshotDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRawRateDO;
import com.scott.payment.admin.mapper.ExchangeBusinessRateMapper;
import com.scott.payment.admin.mapper.ExchangeRateRuleMapper;
import com.scott.payment.admin.mapper.ExchangeRateSourceMapper;
import com.scott.payment.admin.mapper.ExchangeRateUsageSnapshotMapper;
import com.scott.payment.admin.mapper.ExchangeRawRateMapper;
import com.scott.payment.admin.service.AdminExchangeRateService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateServiceImpl
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : 管理端汇率领域服务，负责汇率查询、规则校验、精度计算和主库写入；纯查询固定路由从库。
 * @status : create
 */
public class AdminExchangeRateServiceImpl implements AdminExchangeRateService {

    /**
     * NOT DELETED，用于保存 Admin Exchange Rate Service Impl 中与 notdeleted 相关的业务属性。
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
     * ALL，用于保存 Admin Exchange Rate Service Impl 中与 all 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ALL = "ALL";
    /**
     * AUTO，用于保存 Admin Exchange Rate Service Impl 中与 auto 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String AUTO = "AUTO";
    /**
     * MANUAL，用于保存 Admin Exchange Rate Service Impl 中与 manual 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String MANUAL = "MANUAL";
    /** 无登录上下文时写入审计字段的系统操作人标识，无单位、非敏感且不允许为空。 */
    private static final String SYSTEM_OPERATOR = "system";
    /**
     * RATE STATUS ENABLED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_ENABLED = "ENABLED";
    /**
     * RATE STATUS VOIDED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_VOIDED = "VOIDED";
    /**
     * RATE STATUS DISABLED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_DISABLED = "DISABLED";
    /**
     * RATE STATUS EXPIRED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * UP，用于保存 Admin Exchange Rate Service Impl 中与 up 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String UP = "UP";
    /**
     * DOWN，用于保存 Admin Exchange Rate Service Impl 中与 down 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DOWN = "DOWN";
    /**
     * NONE，用于保存 Admin Exchange Rate Service Impl 中与 none 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String NONE = "NONE";
    /**
     * BP，用于保存 Admin Exchange Rate Service Impl 中与 bp 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String BP = "BP";
    /**
     * PERCENT，用于保存 Admin Exchange Rate Service Impl 中与 percent 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PERCENT = "PERCENT";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    /**
     * CURRENCY PATTERN，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$|^ALL$");
    private static final Set<String> SOURCE_TYPES = Set.of("WEB", "API", "MANUAL", "IMPORT");
    private static final Set<String> RATE_TYPES = Set.of("TRANSACTION_RATE", "SETTLEMENT_RATE");
    private static final Set<String> RATE_FIELDS = Set.of("SPOT_BUY_RATE", "CASH_BUY_RATE", "SPOT_SELL_RATE", "CASH_SELL_RATE", "MIDDLE_RATE");
    private static final Set<String> ADJUST_DIRECTIONS = Set.of(UP, DOWN, NONE);
    private static final Set<String> ADJUST_METHODS = Set.of(BP, PERCENT);
    private static final Set<String> ROUNDING_MODES = Set.of("ROUND_HALF_UP", "ROUND_UP", "ROUND_DOWN");
    private static final Set<String> MANUAL_BUSINESS_RATE_STATUSES = Set.of(RATE_STATUS_ENABLED, RATE_STATUS_DISABLED);

    /**
     * source Mapper 依赖，用于 Admin Exchange Rate Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRateSourceMapper sourceMapper;
    /**
     * raw Rate Mapper 依赖，用于 Admin Exchange Rate Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRawRateMapper rawRateMapper;

    /**
     * rule Mapper 依赖，用于 Admin Exchange Rate Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRateRuleMapper ruleMapper;
    /**
     * business Rate Mapper 依赖，用于 Admin Exchange Rate Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeBusinessRateMapper businessRateMapper;

    /**
     * usage Snapshot Mapper 依赖，用于 Admin Exchange Rate Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRateUsageSnapshotMapper usageSnapshotMapper;

/**
 * 整理adminexchange汇率serviceimpl，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param sourceMapper source Mapper 输入值，参与 来源映射器 的查询、校验、转换、写入或日志摘要
 * @param rawRateMapper raw Rate Mapper 输入值，参与 raw汇率映射器 的查询、校验、转换、写入或日志摘要
 * @param ruleMapper rule Mapper 输入值，参与 规则映射器 的查询、校验、转换、写入或日志摘要
 * @param businessRateMapper business Rate Mapper 输入值，参与 business汇率映射器 的查询、校验、转换、写入或日志摘要
 * @param usageSnapshotMapper usage Snapshot Mapper 输入值，参与 usagesnapshot映射器 的查询、校验、转换、写入或日志摘要
 */
    public AdminExchangeRateServiceImpl(ExchangeRateSourceMapper sourceMapper,
                                        ExchangeRawRateMapper rawRateMapper,
                                        ExchangeRateRuleMapper ruleMapper,
                                        ExchangeBusinessRateMapper businessRateMapper,
                                        ExchangeRateUsageSnapshotMapper usageSnapshotMapper) {
        this.sourceMapper = sourceMapper;
        this.rawRateMapper = rawRateMapper;
        this.ruleMapper = ruleMapper;
        this.businessRateMapper = businessRateMapper;
        this.usageSnapshotMapper = usageSnapshotMapper;
    }

    /**
     * 分页查询汇率源配置。
     *
     * @param request 查询条件，允许为空
     * @return 汇率源分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<SourceResponse> pageSources(SourceQuery request) {
        SourceQuery query = request == null ? new SourceQuery() : request;
        Page<ExchangeRateSourceDO> page = sourceMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                        .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getSourceType()), ExchangeRateSourceDO::getSourceType, trimUpper(query.getSourceType()))
                        .eq(query.getSourceStatus() != null, ExchangeRateSourceDO::getSourceStatus, query.getSourceStatus())
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(ExchangeRateSourceDO::getSourceCode, trimUpper(query.getKeyword()))
                                .or().like(ExchangeRateSourceDO::getSourceName, trim(query.getKeyword())))
                        .orderByAsc(ExchangeRateSourceDO::getPriority)
                        .orderByAsc(ExchangeRateSourceDO::getSourceCode));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toSourceResponse).toList());
    }

    /**
     * 按查询条件导出汇率源配置。
     *
     * @param request 查询条件，允许为空
     * @return 汇率源列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SourceResponse> listSources(SourceQuery request) {
        SourceQuery query = request == null ? new SourceQuery() : request;
        return sourceMapper.selectList(Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                        .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getSourceType()), ExchangeRateSourceDO::getSourceType, trimUpper(query.getSourceType()))
                        .eq(query.getSourceStatus() != null, ExchangeRateSourceDO::getSourceStatus, query.getSourceStatus())
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(ExchangeRateSourceDO::getSourceCode, trimUpper(query.getKeyword()))
                                .or().like(ExchangeRateSourceDO::getSourceName, trim(query.getKeyword())))
                        .orderByAsc(ExchangeRateSourceDO::getPriority)
                        .orderByAsc(ExchangeRateSourceDO::getSourceCode))
                .stream()
                .map(this::toSourceResponse)
                .toList();
    }

    /**
     * 查询汇率源详情。
     *
     * @param id 汇率源主键
     * @return 汇率源详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SourceResponse getSource(Long id) {
        return toSourceResponse(findSource(id));
    }

    /**
     * 新增汇率源配置。
     *
     * @param request 保存请求
     * @return 新增后的汇率源详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SourceResponse createSource(SourceSaveRequest request) {
        validateSourceRequest(request, null);
        ExchangeRateSourceDO entity = new ExchangeRateSourceDO();
        fillSource(entity, request, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        sourceMapper.insert(entity);
        return toSourceResponse(entity);
    }

    /**
     * 修改汇率源配置。
     *
     * @param id      汇率源主键
     * @param request 保存请求
     * @return 修改后的汇率源详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SourceResponse updateSource(Long id, SourceSaveRequest request) {
        ExchangeRateSourceDO entity = findSource(id);
        validateSourceRequest(request, id);
        fillSource(entity, request, LocalDateTime.now());
        sourceMapper.updateById(entity);
        return toSourceResponse(entity);
    }

    /**
     * 启用或停用汇率源。
     *
     * @param id     汇率源主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的汇率源详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SourceResponse updateSourceStatus(Long id, Integer status) {
        ExchangeRateSourceDO entity = findSource(id);
        validateStatus(status);
        entity.setSourceStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        sourceMapper.updateById(entity);
        return toSourceResponse(entity);
    }

    /**
     * 软删除未被原始汇率、规则或业务汇率引用的汇率源。
     *
     * @param id 汇率源主键
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteSource(Long id) {
        ExchangeRateSourceDO entity = findSource(id);
        if (hasRawRate(entity.getSourceCode()) || hasRule(entity.getSourceCode()) || hasBusinessRate(entity.getSourceCode())) {
            throw badRequest("汇率源已存在原始汇率、规则或业务汇率，不能删除");
        }
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        sourceMapper.updateById(entity);
    }

    /**
     * 分页查询原始汇率记录。
     *
     * @param request 查询条件，允许为空
     * @return 原始汇率分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RawRateResponse> pageRawRates(RawRateQuery request) {
        RawRateQuery query = request == null ? new RawRateQuery() : request;
        Page<ExchangeRawRateDO> page = rawRateMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                buildRawRateQueryWrapper(query));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRawRateResponse).toList());
    }

    /**
     * 按查询条件导出原始汇率记录。
     *
     * @param request 查询条件，允许为空
     * @return 原始汇率列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<RawRateResponse> listRawRates(RawRateQuery request) {
        RawRateQuery query = request == null ? new RawRateQuery() : request;
        return rawRateMapper.selectList(buildRawRateQueryWrapper(query))
                .stream()
                .map(this::toRawRateResponse)
                .toList();
    }

    /**
     * 查询原始汇率详情。
     *
     * @param id 原始汇率主键
     * @return 原始汇率详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RawRateResponse getRawRate(Long id) {
        return toRawRateResponse(findRawRate(id));
    }

    /**
     * 手工新增原始汇率记录，报价字段必须至少存在一个有效 BigDecimal 值。
     *
     * @param request 原始汇率保存请求
     * @return 新增后的原始汇率详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RawRateResponse createManualRawRate(RawRateSaveRequest request) {
        validateRawRateRequest(request);
        ExchangeRawRateDO entity = new ExchangeRawRateDO();
        fillRawRate(entity, request, LocalDateTime.now());
        entity.setCreateMethod(MANUAL);
        entity.setRateStatus(RATE_STATUS_ENABLED);
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        rawRateMapper.insert(entity);
        return toRawRateResponse(entity);
    }

    /**
     * 作废未生成业务汇率的原始汇率；已经作废的记录重复调用保持幂等返回。
     *
     * @param id         原始汇率主键
     * @param voidReason 作废原因
     * @return 作废后的原始汇率详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RawRateResponse voidRawRate(Long id, String voidReason) {
        ExchangeRawRateDO entity = findRawRate(id);
        if (RATE_STATUS_VOIDED.equals(entity.getRateStatus())) {
            return toRawRateResponse(entity);
        }
        if (hasBusinessRateByRawRate(id)) {
            throw badRequest("原始汇率已生成业务汇率，不能作废");
        }
        if (!StringUtils.hasText(voidReason)) {
            throw badRequest("作废原因不能为空");
        }
        entity.setRateStatus(RATE_STATUS_VOIDED);
        entity.setVoidReason(trim(voidReason));
        entity.setUpdateTime(LocalDateTime.now());
        rawRateMapper.updateById(entity);
        return toRawRateResponse(entity);
    }

    /**
     * 分页查询汇率规则。
     *
     * @param request 查询条件，允许为空
     * @return 汇率规则分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RuleResponse> pageRules(RuleQuery request) {
        RuleQuery query = request == null ? new RuleQuery() : request;
        Page<ExchangeRateRuleDO> page = ruleMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                        .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeRateRuleDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getSourceCode()), ExchangeRateRuleDO::getSourceCode, trimUpper(query.getSourceCode()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateRuleDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateRuleDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .eq(StringUtils.hasText(query.getAdjustDirection()), ExchangeRateRuleDO::getAdjustDirection, trimUpper(query.getAdjustDirection()))
                        .eq(StringUtils.hasText(query.getAdjustMethod()), ExchangeRateRuleDO::getAdjustMethod, trimUpper(query.getAdjustMethod()))
                        .eq(query.getRuleStatus() != null, ExchangeRateRuleDO::getRuleStatus, query.getRuleStatus())
                        .orderByAsc(ExchangeRateRuleDO::getPriority)
                        .orderByDesc(ExchangeRateRuleDO::getUpdateTime));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRuleResponse).toList());
    }

    /**
     * 按查询条件导出汇率规则。
     *
     * @param request 查询条件，允许为空
     * @return 汇率规则列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<RuleResponse> listRules(RuleQuery request) {
        RuleQuery query = request == null ? new RuleQuery() : request;
        return ruleMapper.selectList(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                        .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeRateRuleDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getSourceCode()), ExchangeRateRuleDO::getSourceCode, trimUpper(query.getSourceCode()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateRuleDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateRuleDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .eq(StringUtils.hasText(query.getAdjustDirection()), ExchangeRateRuleDO::getAdjustDirection, trimUpper(query.getAdjustDirection()))
                        .eq(StringUtils.hasText(query.getAdjustMethod()), ExchangeRateRuleDO::getAdjustMethod, trimUpper(query.getAdjustMethod()))
                        .eq(query.getRuleStatus() != null, ExchangeRateRuleDO::getRuleStatus, query.getRuleStatus())
                        .orderByAsc(ExchangeRateRuleDO::getPriority)
                        .orderByDesc(ExchangeRateRuleDO::getUpdateTime))
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    /**
     * 查询汇率规则详情。
     *
     * @param id 规则主键
     * @return 汇率规则详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RuleResponse getRule(Long id) {
        return toRuleResponse(findRule(id));
    }

    /**
     * 新增汇率规则，并校验同范围启用规则时间窗口不能重叠。
     *
     * @param request 规则保存请求
     * @return 新增后的规则详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RuleResponse createRule(RuleSaveRequest request) {
        validateRuleRequest(request, null);
        ExchangeRateRuleDO entity = new ExchangeRateRuleDO();
        fillRule(entity, request, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        ruleMapper.insert(entity);
        return toRuleResponse(entity);
    }

    /**
     * 修改汇率规则，并校验同范围启用规则时间窗口不能重叠。
     *
     * @param id      规则主键
     * @param request 规则保存请求
     * @return 修改后的规则详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RuleResponse updateRule(Long id, RuleSaveRequest request) {
        ExchangeRateRuleDO entity = findRule(id);
        validateRuleRequest(request, id);
        fillRule(entity, request, LocalDateTime.now());
        ruleMapper.updateById(entity);
        return toRuleResponse(entity);
    }

    /**
     * 启用或停用汇率规则。
     *
     * @param id     规则主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的规则详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RuleResponse updateRuleStatus(Long id, Integer status) {
        ExchangeRateRuleDO entity = findRule(id);
        validateStatus(status);
        entity.setRuleStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(entity);
        return toRuleResponse(entity);
    }

    /**
     * 分页查询最终业务汇率。
     *
     * @param request 查询条件，允许为空
     * @return 业务汇率分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<BusinessRateResponse> pageBusinessRates(BusinessRateQuery request) {
        BusinessRateQuery query = request == null ? new BusinessRateQuery() : request;
        Page<ExchangeBusinessRateDO> page = businessRateMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                buildBusinessRateQueryWrapper(query));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toBusinessRateResponse).toList());
    }

    /**
     * 按查询条件导出业务汇率。
     *
     * @param request 查询条件，允许为空
     * @return 业务汇率列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<BusinessRateResponse> listBusinessRates(BusinessRateQuery request) {
        BusinessRateQuery query = request == null ? new BusinessRateQuery() : request;
        return businessRateMapper.selectList(buildBusinessRateQueryWrapper(query))
                .stream()
                .map(this::toBusinessRateResponse)
                .toList();
    }

    /**
     * 查询业务汇率详情。
     *
     * @param id 业务汇率主键
     * @return 业务汇率详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public BusinessRateResponse getBusinessRate(Long id) {
        return toBusinessRateResponse(findBusinessRate(id));
    }

    /**
     * 手工录入最终可用业务汇率；启用记录会使同范围旧启用汇率失效。
     *
     * @param request 业务汇率保存请求
     * @return 新增后的业务汇率
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public BusinessRateResponse createManualBusinessRate(BusinessRateSaveRequest request) {
        validateBusinessRateRequest(request);
        ExchangeBusinessRateDO entity = insertManualBusinessRate(request, LocalDateTime.now());
        return toBusinessRateResponse(entity);
    }

    /**
     * 批量手工录入最终可用业务汇率。
     *
     * @param request 批量保存请求
     * @return 新增后的业务汇率列表
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public List<BusinessRateResponse> createManualBusinessRates(BusinessRateBatchSaveRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw badRequest("业务汇率批量录入列表不能为空");
        }
        if (request.getItems().size() > 500) {
            throw badRequest("单次最多录入500条业务汇率");
        }
        LocalDateTime now = LocalDateTime.now();
        return request.getItems().stream()
                .peek(this::validateBusinessRateRequest)
                .map(item -> insertManualBusinessRate(item, now))
                .map(this::toBusinessRateResponse)
                .toList();
    }

    /**
     * 根据原始报价和规则生成最终业务汇率，并将同汇率类型、来源和币种对的旧启用汇率置为过期。
     *
     * @param request 业务汇率生成请求
     * @return 生成后的业务汇率详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public BusinessRateResponse generateBusinessRate(GenerateBusinessRateRequest request) {
        if (request == null || request.getRawRateId() == null || request.getRuleId() == null) {
            throw badRequest("原始汇率和汇率规则不能为空");
        }
        ExchangeRawRateDO rawRate = findRawRate(request.getRawRateId());
        ExchangeRateRuleDO rule = findRule(request.getRuleId());
        validateBusinessRateSource(rawRate, rule);
        BigDecimal originalRate = selectRawRateValue(rawRate, rule.getRateField());
        BigDecimal finalRate = calculateFinalRate(originalRate, rule);
        LocalDateTime now = LocalDateTime.now();
        expireCurrentBusinessRate(rule, rawRate, now);
        ExchangeBusinessRateDO entity = new ExchangeBusinessRateDO();
        entity.setRateType(rule.getRateType());
        entity.setSourceCode(rawRate.getSourceCode());
        entity.setBaseCurrency(rawRate.getBaseCurrency());
        entity.setQuoteCurrency(rawRate.getQuoteCurrency());
        entity.setRawRateId(rawRate.getId());
        entity.setRuleId(rule.getId());
        entity.setOriginalRate(originalRate);
        entity.setFinalRate(finalRate);
        entity.setAdjustDescription(buildAdjustDescription(rule, originalRate, finalRate));
        entity.setEffectiveTime(rawRate.getEffectiveTime() == null ? now : rawRate.getEffectiveTime());
        entity.setGenerateMethod(AUTO);
        entity.setRateStatus(RATE_STATUS_ENABLED);
        entity.setRemark(trim(request.getRemark()));
        String operator = currentOperatorName();
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        entity.setDeleted(NOT_DELETED);
        businessRateMapper.insert(entity);
        return toBusinessRateResponse(entity);
    }

    /**
     * 启用或停用业务汇率。
     *
     * @param id     业务汇率主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的业务汇率详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public BusinessRateResponse updateBusinessRateStatus(Long id, Integer status) {
        ExchangeBusinessRateDO entity = findBusinessRate(id);
        validateStatus(status);
        entity.setRateStatus(status == ENABLED ? RATE_STATUS_ENABLED : RATE_STATUS_DISABLED);
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        businessRateMapper.updateById(entity);
        return toBusinessRateResponse(entity);
    }

    /**
     * 分页查询业务链路固化的汇率使用快照。
     *
     * @param request 查询条件，允许为空
     * @return 汇率使用快照分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<UsageSnapshotResponse> pageUsageSnapshots(UsageSnapshotQuery request) {
        UsageSnapshotQuery query = request == null ? new UsageSnapshotQuery() : request;
        Page<ExchangeRateUsageSnapshotDO> page = usageSnapshotMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ExchangeRateUsageSnapshotDO>lambdaQuery()
                        .eq(ExchangeRateUsageSnapshotDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeRateUsageSnapshotDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getUsageScene()), ExchangeRateUsageSnapshotDO::getUsageScene, trimUpper(query.getUsageScene()))
                        .eq(StringUtils.hasText(query.getBusinessType()), ExchangeRateUsageSnapshotDO::getBusinessType, trimUpper(query.getBusinessType()))
                        .eq(StringUtils.hasText(query.getBusinessNo()), ExchangeRateUsageSnapshotDO::getBusinessNo, trim(query.getBusinessNo()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateUsageSnapshotDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateUsageSnapshotDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .ge(query.getAppliedStartTime() != null, ExchangeRateUsageSnapshotDO::getAppliedTime, query.getAppliedStartTime())
                        .le(query.getAppliedEndTime() != null, ExchangeRateUsageSnapshotDO::getAppliedTime, query.getAppliedEndTime())
                        .orderByDesc(ExchangeRateUsageSnapshotDO::getAppliedTime)
                        .orderByDesc(ExchangeRateUsageSnapshotDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toUsageSnapshotResponse).toList());
    }

    /**
     * 按查询条件导出汇率使用快照。
     *
     * @param request 查询条件，允许为空
     * @return 使用快照列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<UsageSnapshotResponse> listUsageSnapshots(UsageSnapshotQuery request) {
        UsageSnapshotQuery query = request == null ? new UsageSnapshotQuery() : request;
        return usageSnapshotMapper.selectList(Wrappers.<ExchangeRateUsageSnapshotDO>lambdaQuery()
                        .eq(ExchangeRateUsageSnapshotDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeRateUsageSnapshotDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getUsageScene()), ExchangeRateUsageSnapshotDO::getUsageScene, trimUpper(query.getUsageScene()))
                        .eq(StringUtils.hasText(query.getBusinessType()), ExchangeRateUsageSnapshotDO::getBusinessType, trimUpper(query.getBusinessType()))
                        .eq(StringUtils.hasText(query.getBusinessNo()), ExchangeRateUsageSnapshotDO::getBusinessNo, trim(query.getBusinessNo()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateUsageSnapshotDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateUsageSnapshotDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .ge(query.getAppliedStartTime() != null, ExchangeRateUsageSnapshotDO::getAppliedTime, query.getAppliedStartTime())
                        .le(query.getAppliedEndTime() != null, ExchangeRateUsageSnapshotDO::getAppliedTime, query.getAppliedEndTime())
                        .orderByDesc(ExchangeRateUsageSnapshotDO::getAppliedTime)
                        .orderByDesc(ExchangeRateUsageSnapshotDO::getId))
                .stream()
                .map(this::toUsageSnapshotResponse)
                .toList();
    }

    /**
     * 查询汇率使用快照详情。
     *
     * @param id 快照主键
     * @return 使用快照详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public UsageSnapshotResponse getUsageSnapshot(Long id) {
        ExchangeRateUsageSnapshotDO entity = usageSnapshotMapper.selectOne(Wrappers.<ExchangeRateUsageSnapshotDO>lambdaQuery()
                .eq(ExchangeRateUsageSnapshotDO::getId, id)
                .eq(ExchangeRateUsageSnapshotDO::getDeleted, NOT_DELETED));
        if (entity == null) {
            throw notFound("汇率使用快照不存在");
        }
        return toUsageSnapshotResponse(entity);
    }

    /**
     * 根据汇率规则计算最终业务汇率。
     *
     * @param originalRate 原始报价字段值
     * @param rule         汇率规则
     * @return 最终业务汇率
     */
    public BigDecimal calculateFinalRate(BigDecimal originalRate, ExchangeRateRuleDO rule) {
        if (originalRate == null || originalRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("原始报价必须大于0");
        }
        if (rule == null) {
            throw badRequest("汇率规则不能为空");
        }
        BigDecimal multiplier = BigDecimal.ONE;
        if (!NONE.equals(rule.getAdjustDirection())) {
            BigDecimal adjustRatio = adjustRatio(rule.getAdjustMethod(), rule.getAdjustValue());
            multiplier = UP.equals(rule.getAdjustDirection())
                    ? BigDecimal.ONE.add(adjustRatio)
                    : BigDecimal.ONE.subtract(adjustRatio);
        }
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("汇率调整后倍率必须大于0");
        }
        return originalRate.multiply(multiplier).setScale(rule.getDecimalScale(), toRoundingMode(rule.getRoundingMode()));
    }

    /**
     * 校验来源请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
    private void validateSourceRequest(SourceSaveRequest request, Long excludeId) {
        if (request == null) {
            throw badRequest("汇率源请求不能为空");
        }
        String sourceCode = trimUpper(request.getSourceCode());
        if (!CODE_PATTERN.matcher(sourceCode).matches()) {
            throw badRequest("汇率源编码只能包含大写字母、数字、下划线，长度2-64");
        }
        if (!SOURCE_TYPES.contains(trimUpper(request.getSourceType()))) {
            throw badRequest("汇率源类型必须为 WEB、API、MANUAL 或 IMPORT");
        }
        validateStatus(request.getSourceStatus());
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            throw badRequest("超时时间必须大于0");
        }
        Long count = sourceMapper.selectCount(Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                .eq(ExchangeRateSourceDO::getSourceCode, sourceCode)
                .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED)
                .ne(excludeId != null, ExchangeRateSourceDO::getId, excludeId));
        if (count > 0) {
            throw badRequest("汇率源编码已存在");
        }
    }

    /**
     * 校验raw汇率request输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void validateRawRateRequest(RawRateSaveRequest request) {
        if (request == null) {
            throw badRequest("原始汇率请求不能为空");
        }
        requireSourceExists(trimUpper(request.getSourceCode()));
        validateCurrency(trimUpper(request.getBaseCurrency()), false);
        validateCurrency(trimUpper(request.getQuoteCurrency()), false);
        if (trimUpper(request.getBaseCurrency()).equals(trimUpper(request.getQuoteCurrency()))) {
            throw badRequest("原始币种和目标币种不能相同");
        }
        if (!hasAnyRate(request.getCashBuyRate(), request.getCashSellRate(), request.getSpotBuyRate(), request.getSpotSellRate(), request.getMiddleRate())) {
            throw badRequest("至少需要填写一个有效报价字段");
        }
        validatePositiveRate(request.getCashBuyRate(), "现钞买入价");
        validatePositiveRate(request.getCashSellRate(), "现钞卖出价");
        validatePositiveRate(request.getSpotBuyRate(), "现汇买入价");
        validatePositiveRate(request.getSpotSellRate(), "现汇卖出价");
        validatePositiveRate(request.getMiddleRate(), "中间折算价");
        Long count = rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, trimUpper(request.getSourceCode()))
                .eq(ExchangeRawRateDO::getBaseCurrency, trimUpper(request.getBaseCurrency()))
                .eq(ExchangeRawRateDO::getQuoteCurrency, trimUpper(request.getQuoteCurrency()))
                .eq(ExchangeRawRateDO::getPublishTime, request.getPublishTime())
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED));
        if (count > 0) {
            throw badRequest("同一汇率源、币种对和发布时间的原始汇率已存在");
        }
    }

    /**
     * 校验规则请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
    private void validateRuleRequest(RuleSaveRequest request, Long excludeId) {
        if (request == null) {
            throw badRequest("汇率规则请求不能为空");
        }
        if (!RATE_TYPES.contains(trimUpper(request.getRateType()))) {
            throw badRequest("汇率类型必须为 TRANSACTION_RATE 或 SETTLEMENT_RATE");
        }
        String sourceCode = trimUpper(request.getSourceCode());
        if (!ALL.equals(sourceCode)) {
            requireSourceExists(sourceCode);
        }
        validateCurrency(trimUpper(request.getBaseCurrency()), true);
        validateCurrency(trimUpper(request.getQuoteCurrency()), true);
        if (!ALL.equals(trimUpper(request.getBaseCurrency())) && trimUpper(request.getBaseCurrency()).equals(trimUpper(request.getQuoteCurrency()))) {
            throw badRequest("原始币种和目标币种不能相同");
        }
        if (!RATE_FIELDS.contains(trimUpper(request.getRateField()))) {
            throw badRequest("取值字段不支持");
        }
        if (!ADJUST_DIRECTIONS.contains(trimUpper(request.getAdjustDirection()))) {
            throw badRequest("调整方向不支持");
        }
        if (!ADJUST_METHODS.contains(trimUpper(request.getAdjustMethod()))) {
            throw badRequest("调整方式不支持");
        }
        if (request.getAdjustValue() == null || request.getAdjustValue().compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("调整值不能小于0");
        }
        if (request.getDecimalScale() == null || request.getDecimalScale() < 2 || request.getDecimalScale() > 12) {
            throw badRequest("小数位必须在2到12之间");
        }
        if (!ROUNDING_MODES.contains(trimUpper(request.getRoundingMode()))) {
            throw badRequest("舍入方式不支持");
        }
        validateStatus(request.getRuleStatus());
        if (request.getEffectiveStartTime() != null && request.getEffectiveEndTime() != null
                && request.getEffectiveEndTime().isBefore(request.getEffectiveStartTime())) {
            throw badRequest("规则失效时间不能早于生效时间");
        }
        if (request.getRuleStatus() == ENABLED && hasOverlappedEnabledRule(request, excludeId)) {
            throw badRequest("同一汇率类型、来源和币种对存在时间重叠的启用规则");
        }
    }

    /**
     * 校验business汇率request输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void validateBusinessRateRequest(BusinessRateSaveRequest request) {
        if (request == null) {
            throw badRequest("业务汇率请求不能为空");
        }
        if (!RATE_TYPES.contains(trimUpper(request.getRateType()))) {
            throw badRequest("汇率类型必须为 TRANSACTION_RATE 或 SETTLEMENT_RATE");
        }
        requireSourceExists(trimUpper(request.getSourceCode()));
        validateCurrency(trimUpper(request.getBaseCurrency()), false);
        validateCurrency(trimUpper(request.getQuoteCurrency()), false);
        if (trimUpper(request.getBaseCurrency()).equals(trimUpper(request.getQuoteCurrency()))) {
            throw badRequest("原始币种和目标币种不能相同");
        }
        validatePositiveRequiredRate(request.getOriginalRate(), "原始汇率");
        validatePositiveRequiredRate(request.getFinalRate(), "最终汇率");
        if (request.getEffectiveTime() == null) {
            throw badRequest("业务汇率生效时间不能为空");
        }
        String rateStatus = StringUtils.hasText(request.getRateStatus()) ? trimUpper(request.getRateStatus()) : RATE_STATUS_ENABLED;
        if (!MANUAL_BUSINESS_RATE_STATUSES.contains(rateStatus)) {
            throw badRequest("手工业务汇率状态必须为 ENABLED 或 DISABLED");
        }
    }

    /**
     * 判断 has overlapped enabled rule 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasOverlappedEnabledRule(RuleSaveRequest request, Long excludeId) {
        return ruleMapper.selectList(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                        .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)
                        .eq(ExchangeRateRuleDO::getRuleStatus, ENABLED)
                        .eq(ExchangeRateRuleDO::getRateType, trimUpper(request.getRateType()))
                        .eq(ExchangeRateRuleDO::getSourceCode, trimUpper(request.getSourceCode()))
                        .eq(ExchangeRateRuleDO::getBaseCurrency, trimUpper(request.getBaseCurrency()))
                        .eq(ExchangeRateRuleDO::getQuoteCurrency, trimUpper(request.getQuoteCurrency()))
                        .ne(excludeId != null, ExchangeRateRuleDO::getId, excludeId))
                .stream()
                .anyMatch(existing -> timeRangeOverlaps(request.getEffectiveStartTime(), request.getEffectiveEndTime(),
                        existing.getEffectiveStartTime(), existing.getEffectiveEndTime()));
    }

    /**
     * 整理时间范围overlaps，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param start1 start 1 输入值，参与 start1 的查询、校验、转换、写入或日志摘要
     * @param end1 end 1 输入值，参与 end1 的查询、校验、转换、写入或日志摘要
     * @param start2 start 2 输入值，参与 start2 的查询、校验、转换、写入或日志摘要
     * @param end2 end 2 输入值，参与 end2 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean timeRangeOverlaps(LocalDateTime start1, LocalDateTime end1, LocalDateTime start2, LocalDateTime end2) {
        LocalDateTime min = LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime max = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        LocalDateTime leftStart = start1 == null ? min : start1;
        LocalDateTime leftEnd = end1 == null ? max : end1;
        LocalDateTime rightStart = start2 == null ? min : start2;
        LocalDateTime rightEnd = end2 == null ? max : end2;
        return !leftEnd.isBefore(rightStart) && !rightEnd.isBefore(leftStart);
    }

    /**
     * 校验business汇率来源输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     */
    private void validateBusinessRateSource(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule) {
        if (!RATE_STATUS_ENABLED.equals(rawRate.getRateStatus())) {
            throw badRequest("只有启用状态的原始汇率可以生成业务汇率");
        }
        if (rule.getRuleStatus() != ENABLED) {
            throw badRequest("只有启用状态的汇率规则可以生成业务汇率");
        }
        if (!ALL.equals(rule.getSourceCode()) && !rule.getSourceCode().equals(rawRate.getSourceCode())) {
            throw badRequest("规则汇率源与原始汇率不匹配");
        }
        if (!ALL.equals(rule.getBaseCurrency()) && !rule.getBaseCurrency().equals(rawRate.getBaseCurrency())) {
            throw badRequest("规则原始币种与原始汇率不匹配");
        }
        if (!ALL.equals(rule.getQuoteCurrency()) && !rule.getQuoteCurrency().equals(rawRate.getQuoteCurrency())) {
            throw badRequest("规则目标币种与原始汇率不匹配");
        }
        LocalDateTime effectiveTime = rawRate.getEffectiveTime() == null ? rawRate.getPublishTime() : rawRate.getEffectiveTime();
        if (rule.getEffectiveStartTime() != null && effectiveTime.isBefore(rule.getEffectiveStartTime())) {
            throw badRequest("原始汇率生效时间早于规则生效时间");
        }
        if (rule.getEffectiveEndTime() != null && effectiveTime.isAfter(rule.getEffectiveEndTime())) {
            throw badRequest("原始汇率生效时间晚于规则失效时间");
        }
    }

    /**
     * 使同一规则和原始汇率对应的当前启用业务汇率失效，避免同一币种对存在多个启用版本。
     *
     * @param rule 用于确定汇率类型的规则
     * @param rawRate 用于确定来源和币种对的原始汇率
     * @param expireTime 旧业务汇率的失效时间和审计更新时间
     */
    private void expireCurrentBusinessRate(ExchangeRateRuleDO rule, ExchangeRawRateDO rawRate, LocalDateTime expireTime) {
        expireCurrentBusinessRate(rule.getRateType(), rawRate.getSourceCode(), rawRate.getBaseCurrency(), rawRate.getQuoteCurrency(), expireTime);
    }

    /**
     * 将同一汇率类型、来源和币种对下的启用记录批量置为已失效，并记录实际操作人。
     * 调用方事务覆盖查询和更新，任一记录更新失败时整体回滚。
     *
     * @param rateType 汇率类型编码
     * @param sourceCode 汇率来源编码
     * @param baseCurrency 原始币种，ISO 4217 三位大写代码
     * @param quoteCurrency 目标币种，ISO 4217 三位大写代码
     * @param expireTime 失效时间和审计更新时间
     */
    private void expireCurrentBusinessRate(String rateType, String sourceCode, String baseCurrency, String quoteCurrency, LocalDateTime expireTime) {
        businessRateMapper.selectList(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                        .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)
                        .eq(ExchangeBusinessRateDO::getRateStatus, RATE_STATUS_ENABLED)
                        .eq(ExchangeBusinessRateDO::getRateType, rateType)
                        .eq(ExchangeBusinessRateDO::getSourceCode, sourceCode)
                        .eq(ExchangeBusinessRateDO::getBaseCurrency, baseCurrency)
                        .eq(ExchangeBusinessRateDO::getQuoteCurrency, quoteCurrency))
                .forEach(existing -> {
                    existing.setRateStatus(RATE_STATUS_EXPIRED);
                    existing.setExpireTime(expireTime);
                    existing.setUpdateBy(currentOperatorName());
                    existing.setUpdateTime(expireTime);
                    businessRateMapper.updateById(existing);
                });
    }

    /**
     * 写入一条人工业务汇率；若新记录直接启用，先使同范围的旧启用记录失效。
     * 调用方已完成字段校验并提供统一业务时间，创建人和更新人使用当前管理端操作人。
     *
     * @param request 已通过业务校验的人工汇率请求
     * @param now 本批次统一使用的创建和更新时间
     * @return 已写入主库的业务汇率实体
     */
    private ExchangeBusinessRateDO insertManualBusinessRate(BusinessRateSaveRequest request, LocalDateTime now) {
        String rateType = trimUpper(request.getRateType());
        String sourceCode = trimUpper(request.getSourceCode());
        String baseCurrency = trimUpper(request.getBaseCurrency());
        String quoteCurrency = trimUpper(request.getQuoteCurrency());
        String rateStatus = StringUtils.hasText(request.getRateStatus()) ? trimUpper(request.getRateStatus()) : RATE_STATUS_ENABLED;
        if (RATE_STATUS_ENABLED.equals(rateStatus)) {
            expireCurrentBusinessRate(rateType, sourceCode, baseCurrency, quoteCurrency, now);
        }
        ExchangeBusinessRateDO entity = new ExchangeBusinessRateDO();
        entity.setRateType(rateType);
        entity.setSourceCode(sourceCode);
        entity.setBaseCurrency(baseCurrency);
        entity.setQuoteCurrency(quoteCurrency);
        entity.setOriginalRate(request.getOriginalRate());
        entity.setFinalRate(request.getFinalRate());
        entity.setAdjustDescription("MANUAL original " + request.getOriginalRate().toPlainString()
                + ", final " + request.getFinalRate().toPlainString());
        entity.setEffectiveTime(request.getEffectiveTime());
        entity.setGenerateMethod(MANUAL);
        entity.setRateStatus(rateStatus);
        entity.setRemark(trim(request.getRemark()));
        String operator = currentOperatorName();
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        entity.setDeleted(NOT_DELETED);
        businessRateMapper.insert(entity);
        return entity;
    }

    /**
     * 查询原始汇率值，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param rateField rate Field 输入值，参与 汇率field 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BigDecimal selectRawRateValue(ExchangeRawRateDO rawRate, String rateField) {
        BigDecimal value = switch (rateField) {
            case "SPOT_BUY_RATE" -> rawRate.getSpotBuyRate();
            case "CASH_BUY_RATE" -> rawRate.getCashBuyRate();
            case "SPOT_SELL_RATE" -> rawRate.getSpotSellRate();
            case "CASH_SELL_RATE" -> rawRate.getCashSellRate();
            case "MIDDLE_RATE" -> rawRate.getMiddleRate();
            default -> null;
        };
        if (value == null) {
            throw badRequest("原始汇率缺少规则要求的取值字段：" + rateField);
        }
        return value;
    }

    /**
     * 规范化adjustratio，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param adjustMethod adjust Method 输入值，参与 adjustmethod 的查询、校验、转换、写入或日志摘要
     * @param adjustValue adjust Value 输入值，参与 adjust值 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal adjustRatio(String adjustMethod, BigDecimal adjustValue) {
        if (adjustValue == null) {
            return BigDecimal.ZERO;
        }
        if (BP.equals(adjustMethod)) {
            return adjustValue.divide(new BigDecimal("10000"), 12, RoundingMode.HALF_UP);
        }
        return adjustValue.divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP);
    }

    /**
     * 构造roundingmode对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param roundingMode rounding Mode 输入值，参与 roundingmode 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private RoundingMode toRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ROUND_UP" -> RoundingMode.UP;
            case "ROUND_DOWN" -> RoundingMode.DOWN;
            default -> RoundingMode.HALF_UP;
        };
    }

    /**
     * 构造adjustdescription对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @param originalRate original Rate 输入值，参与 original汇率 的查询、校验、转换、写入或日志摘要
     * @param finalRate final Rate 输入值，参与 final汇率 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String buildAdjustDescription(ExchangeRateRuleDO rule, BigDecimal originalRate, BigDecimal finalRate) {
        return rule.getRateField() + " " + originalRate.toPlainString()
                + ", " + rule.getAdjustDirection() + " " + rule.getAdjustValue().toPlainString()
                + " " + rule.getAdjustMethod() + ", scale " + rule.getDecimalScale()
                + ", " + rule.getRoundingMode() + ", final " + finalRate.toPlainString();
    }

    /**
     * 构造来源对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillSource(ExchangeRateSourceDO entity, SourceSaveRequest request, LocalDateTime now) {
        entity.setSourceCode(trimUpper(request.getSourceCode()));
        entity.setSourceName(trim(request.getSourceName()));
        entity.setSourceType(trimUpper(request.getSourceType()));
        entity.setRequestUrl(trim(request.getRequestUrl()));
        entity.setDefaultSource(request.getDefaultSource() == null ? DISABLED : request.getDefaultSource());
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        entity.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 10 : request.getTimeoutSeconds());
        entity.setSourceStatus(request.getSourceStatus());
        entity.setRemark(trim(request.getRemark()));
        entity.setUpdateTime(now);
    }

    /**
     * 构造raw汇率对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillRawRate(ExchangeRawRateDO entity, RawRateSaveRequest request, LocalDateTime now) {
        entity.setSourceCode(trimUpper(request.getSourceCode()));
        entity.setBaseCurrency(trimUpper(request.getBaseCurrency()));
        entity.setQuoteCurrency(trimUpper(request.getQuoteCurrency()));
        entity.setCashBuyRate(request.getCashBuyRate());
        entity.setCashSellRate(request.getCashSellRate());
        entity.setSpotBuyRate(request.getSpotBuyRate());
        entity.setSpotSellRate(request.getSpotSellRate());
        entity.setMiddleRate(request.getMiddleRate());
        entity.setPublishTime(request.getPublishTime());
        entity.setFetchTime(now);
        entity.setEffectiveTime(request.getEffectiveTime() == null ? request.getPublishTime() : request.getEffectiveTime());
        entity.setBatchNo(trim(request.getBatchNo()));
        entity.setUpdateTime(now);
    }

    /**
     * 构造规则对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillRule(ExchangeRateRuleDO entity, RuleSaveRequest request, LocalDateTime now) {
        entity.setRateType(trimUpper(request.getRateType()));
        entity.setSourceCode(trimUpper(request.getSourceCode()));
        entity.setBaseCurrency(trimUpper(request.getBaseCurrency()));
        entity.setQuoteCurrency(trimUpper(request.getQuoteCurrency()));
        entity.setRateField(trimUpper(request.getRateField()));
        entity.setAdjustDirection(trimUpper(request.getAdjustDirection()));
        entity.setAdjustMethod(trimUpper(request.getAdjustMethod()));
        entity.setAdjustValue(request.getAdjustValue());
        entity.setDecimalScale(request.getDecimalScale());
        entity.setRoundingMode(trimUpper(request.getRoundingMode()));
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        entity.setEffectiveStartTime(request.getEffectiveStartTime());
        entity.setEffectiveEndTime(request.getEffectiveEndTime());
        entity.setRuleStatus(request.getRuleStatus());
        entity.setRemark(trim(request.getRemark()));
        entity.setUpdateTime(now);
    }

    /**
     * 构造来源响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SourceResponse toSourceResponse(ExchangeRateSourceDO entity) {
        SourceResponse response = new SourceResponse();
        response.setId(entity.getId());
        response.setSourceCode(entity.getSourceCode());
        response.setSourceName(entity.getSourceName());
        response.setSourceType(entity.getSourceType());
        response.setRequestUrl(entity.getRequestUrl());
        response.setDefaultSource(entity.getDefaultSource());
        response.setPriority(entity.getPriority());
        response.setTimeoutSeconds(entity.getTimeoutSeconds());
        response.setSourceStatus(entity.getSourceStatus());
        response.setLastFetchTime(entity.getLastFetchTime());
        response.setLastFetchStatus(entity.getLastFetchStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 构造raw汇率响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private RawRateResponse toRawRateResponse(ExchangeRawRateDO entity) {
        RawRateResponse response = new RawRateResponse();
        response.setId(entity.getId());
        response.setSourceCode(entity.getSourceCode());
        response.setBaseCurrency(entity.getBaseCurrency());
        response.setQuoteCurrency(entity.getQuoteCurrency());
        response.setCashBuyRate(entity.getCashBuyRate());
        response.setCashSellRate(entity.getCashSellRate());
        response.setSpotBuyRate(entity.getSpotBuyRate());
        response.setSpotSellRate(entity.getSpotSellRate());
        response.setMiddleRate(entity.getMiddleRate());
        response.setPublishTime(entity.getPublishTime());
        response.setFetchTime(entity.getFetchTime());
        response.setEffectiveTime(entity.getEffectiveTime());
        response.setCreateMethod(entity.getCreateMethod());
        response.setBatchNo(entity.getBatchNo());
        response.setRateStatus(entity.getRateStatus());
        response.setVoidReason(entity.getVoidReason());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 构造规则响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private RuleResponse toRuleResponse(ExchangeRateRuleDO entity) {
        RuleResponse response = new RuleResponse();
        response.setId(entity.getId());
        response.setRateType(entity.getRateType());
        response.setSourceCode(entity.getSourceCode());
        response.setBaseCurrency(entity.getBaseCurrency());
        response.setQuoteCurrency(entity.getQuoteCurrency());
        response.setRateField(entity.getRateField());
        response.setAdjustDirection(entity.getAdjustDirection());
        response.setAdjustMethod(entity.getAdjustMethod());
        response.setAdjustValue(entity.getAdjustValue());
        response.setDecimalScale(entity.getDecimalScale());
        response.setRoundingMode(entity.getRoundingMode());
        response.setPriority(entity.getPriority());
        response.setEffectiveStartTime(entity.getEffectiveStartTime());
        response.setEffectiveEndTime(entity.getEffectiveEndTime());
        response.setRuleStatus(entity.getRuleStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 将业务汇率持久化实体转换为管理端响应，完整保留汇率精度、时效和审计字段。
     *
     * @param entity 已从数据库读取或刚写入的业务汇率实体
     * @return 管理端业务汇率响应
     */
    private BusinessRateResponse toBusinessRateResponse(ExchangeBusinessRateDO entity) {
        BusinessRateResponse response = new BusinessRateResponse();
        response.setId(entity.getId());
        response.setRateType(entity.getRateType());
        response.setSourceCode(entity.getSourceCode());
        response.setBaseCurrency(entity.getBaseCurrency());
        response.setQuoteCurrency(entity.getQuoteCurrency());
        response.setRawRateId(entity.getRawRateId());
        response.setRuleId(entity.getRuleId());
        response.setOriginalRate(entity.getOriginalRate());
        response.setFinalRate(entity.getFinalRate());
        response.setAdjustDescription(entity.getAdjustDescription());
        response.setEffectiveTime(entity.getEffectiveTime());
        response.setExpireTime(entity.getExpireTime());
        response.setGenerateMethod(entity.getGenerateMethod());
        response.setRateStatus(entity.getRateStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateBy(entity.getUpdateBy());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 构建原始汇率分页和导出共用的查询条件，避免两类结果出现筛选或排序口径差异。
     *
     * @param query 已完成空值归一化的查询条件
     * @return 按拉取时间、主键倒序排列的 MyBatis 查询对象
     */
    LambdaQueryWrapper<ExchangeRawRateDO> buildRawRateQueryWrapper(RawRateQuery query) {
        return Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getSourceCode()), ExchangeRawRateDO::getSourceCode, trimUpper(query.getSourceCode()))
                .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRawRateDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRawRateDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                .eq(StringUtils.hasText(query.getRateStatus()), ExchangeRawRateDO::getRateStatus, trimUpper(query.getRateStatus()))
                .eq(StringUtils.hasText(query.getCreateMethod()), ExchangeRawRateDO::getCreateMethod, trimUpper(query.getCreateMethod()))
                .ge(query.getPublishStartTime() != null, ExchangeRawRateDO::getPublishTime, query.getPublishStartTime())
                .le(query.getPublishEndTime() != null, ExchangeRawRateDO::getPublishTime, query.getPublishEndTime())
                .ge(query.getFetchStartTime() != null, ExchangeRawRateDO::getFetchTime, query.getFetchStartTime())
                .le(query.getFetchEndTime() != null, ExchangeRawRateDO::getFetchTime, query.getFetchEndTime())
                .ge(query.getEffectiveStartTime() != null, ExchangeRawRateDO::getEffectiveTime, query.getEffectiveStartTime())
                .le(query.getEffectiveEndTime() != null, ExchangeRawRateDO::getEffectiveTime, query.getEffectiveEndTime())
                .orderByDesc(ExchangeRawRateDO::getFetchTime)
                .orderByDesc(ExchangeRawRateDO::getId);
    }

    /**
     * 构建业务汇率分页和导出共用的查询条件，时间边界均包含起止时刻。
     *
     * @param query 已完成空值归一化的查询条件
     * @return 按生效时间、主键倒序排列的 MyBatis 查询对象
     */
    LambdaQueryWrapper<ExchangeBusinessRateDO> buildBusinessRateQueryWrapper(BusinessRateQuery query) {
        return Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getRateType()), ExchangeBusinessRateDO::getRateType, trimUpper(query.getRateType()))
                .eq(StringUtils.hasText(query.getSourceCode()), ExchangeBusinessRateDO::getSourceCode, trimUpper(query.getSourceCode()))
                .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeBusinessRateDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeBusinessRateDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                .eq(StringUtils.hasText(query.getRateStatus()), ExchangeBusinessRateDO::getRateStatus, trimUpper(query.getRateStatus()))
                .eq(StringUtils.hasText(query.getGenerateMethod()), ExchangeBusinessRateDO::getGenerateMethod, trimUpper(query.getGenerateMethod()))
                .ge(query.getEffectiveStartTime() != null, ExchangeBusinessRateDO::getEffectiveTime, query.getEffectiveStartTime())
                .le(query.getEffectiveEndTime() != null, ExchangeBusinessRateDO::getEffectiveTime, query.getEffectiveEndTime())
                .ge(query.getCreateStartTime() != null, ExchangeBusinessRateDO::getCreateTime, query.getCreateStartTime())
                .le(query.getCreateEndTime() != null, ExchangeBusinessRateDO::getCreateTime, query.getCreateEndTime())
                .orderByDesc(ExchangeBusinessRateDO::getEffectiveTime)
                .orderByDesc(ExchangeBusinessRateDO::getId);
    }

    /**
     * 获取审计字段使用的操作人名称；任务或无登录上下文调用时使用 system。
     *
     * @return 管理员姓名、登录账号或 system
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return SYSTEM_OPERATOR;
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return SYSTEM_OPERATOR;
    }

    /**
     * 构造usagesnapshot响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private UsageSnapshotResponse toUsageSnapshotResponse(ExchangeRateUsageSnapshotDO entity) {
        UsageSnapshotResponse response = new UsageSnapshotResponse();
        response.setId(entity.getId());
        response.setRateType(entity.getRateType());
        response.setUsageScene(entity.getUsageScene());
        response.setBusinessType(entity.getBusinessType());
        response.setBusinessNo(entity.getBusinessNo());
        response.setBaseCurrency(entity.getBaseCurrency());
        response.setQuoteCurrency(entity.getQuoteCurrency());
        response.setUsedRate(entity.getUsedRate());
        response.setBusinessRateId(entity.getBusinessRateId());
        response.setRawRateId(entity.getRawRateId());
        response.setRuleId(entity.getRuleId());
        response.setCalculationDescription(entity.getCalculationDescription());
        response.setAppliedTime(entity.getAppliedTime());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    /**
     * 查询来源，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ExchangeRateSourceDO findSource(Long id) {
        ExchangeRateSourceDO entity = sourceMapper.selectOne(Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                .eq(ExchangeRateSourceDO::getId, id)
                .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED));
        if (entity == null) {
            throw notFound("汇率源不存在");
        }
        return entity;
    }

    /**
     * 查询raw汇率，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ExchangeRawRateDO findRawRate(Long id) {
        ExchangeRawRateDO entity = rawRateMapper.selectOne(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getId, id)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED));
        if (entity == null) {
            throw notFound("原始汇率不存在");
        }
        return entity;
    }

    /**
     * 查询规则，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ExchangeRateRuleDO findRule(Long id) {
        ExchangeRateRuleDO entity = ruleMapper.selectOne(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                .eq(ExchangeRateRuleDO::getId, id)
                .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED));
        if (entity == null) {
            throw notFound("汇率规则不存在");
        }
        return entity;
    }

    /**
     * 查询业务汇率，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ExchangeBusinessRateDO findBusinessRate(Long id) {
        ExchangeBusinessRateDO entity = businessRateMapper.selectOne(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getId, id)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED));
        if (entity == null) {
            throw notFound("业务汇率不存在");
        }
        return entity;
    }

    /**
     * 校验来源exists输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     */
    private void requireSourceExists(String sourceCode) {
        Long count = sourceMapper.selectCount(Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                .eq(ExchangeRateSourceDO::getSourceCode, sourceCode)
                .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED));
        if (count == 0) {
            throw badRequest("汇率源不存在：" + sourceCode);
        }
    }

    /**
     * 判断 has raw rate 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasRawRate(String sourceCode) {
        return rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has rule 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasRule(String sourceCode) {
        return ruleMapper.selectCount(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                .eq(ExchangeRateRuleDO::getSourceCode, sourceCode)
                .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has business rate 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasBusinessRate(String sourceCode) {
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getSourceCode, sourceCode)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has business rate by raw rate 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param rawRateId raw Rate ID 输入值，参与 raw汇率ID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasBusinessRateByRawRate(Long rawRateId) {
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getRawRateId, rawRateId)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 校验状态输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
    private void validateStatus(Integer status) {
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw badRequest("状态必须为0停用或1启用");
        }
    }

    /**
     * 校验币种输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @param allowAll allow All 输入值，参与 allowall 的查询、校验、转换、写入或日志摘要
     */
    private void validateCurrency(String currency, boolean allowAll) {
        if (!StringUtils.hasText(currency) || !CURRENCY_PATTERN.matcher(currency).matches() || (!allowAll && ALL.equals(currency))) {
            throw badRequest("币种必须为 ISO 4217 三位字母代码" + (allowAll ? "或 ALL" : ""));
        }
    }

    /**
     * 判断 has any rate 条件是否成立，用于控制 Admin Exchange Rate Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param rates rates 输入值，参与 汇率 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasAnyRate(BigDecimal... rates) {
        for (BigDecimal rate : rates) {
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验positive汇率输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param rate rate 输入值，参与 汇率 的查询、校验、转换、写入或日志摘要
     * @param fieldName field Name 输入值，参与 fieldname 的查询、校验、转换、写入或日志摘要
     */
    private void validatePositiveRate(BigDecimal rate, String fieldName) {
        if (rate != null && rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(fieldName + "必须大于0");
        }
    }

    /**
     * 校验positiverequired汇率输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param rate rate 输入值，参与 汇率 的查询、校验、转换、写入或日志摘要
     * @param fieldName field Name 输入值，参与 fieldname 的查询、校验、转换、写入或日志摘要
     */
    private void validatePositiveRequiredRate(BigDecimal rate, String fieldName) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(fieldName + "必须大于0");
        }
    }

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /**
     * 规范化notfound，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException notFound(String message) {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), message);
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
        return trim(value) == null ? null : trim(value).toUpperCase(Locale.ROOT);
    }
}

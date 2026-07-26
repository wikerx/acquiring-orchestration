package com.scott.payment.admin.service.impl;

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
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
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
 * @description : AdminExchangeRateServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminExchangeRateServiceImpl implements AdminExchangeRateService {

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
     * AUTO 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String AUTO = "AUTO";
    /**
     * MANUAL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MANUAL = "MANUAL";
    /**
     * RATE STATUS ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_ENABLED = "ENABLED";
    /**
     * RATE STATUS VOIDED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_VOIDED = "VOIDED";
    /**
     * RATE STATUS DISABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_DISABLED = "DISABLED";
    /**
     * RATE STATUS EXPIRED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * UP 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String UP = "UP";
    /**
     * DOWN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DOWN = "DOWN";
    /**
     * NONE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String NONE = "NONE";
    /**
     * BP 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String BP = "BP";
    /**
     * PERCENT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PERCENT = "PERCENT";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    /**
     * CURRENCY PATTERN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：ISO 4217 三位币种代码；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
     * source Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeRateSourceMapper sourceMapper;
    /**
     * raw Rate Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeRawRateMapper rawRateMapper;

    /**
     * rule Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeRateRuleMapper ruleMapper;
    /**
     * business Rate Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeBusinessRateMapper businessRateMapper;

    /**
     * usage Snapshot Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeRateUsageSnapshotMapper usageSnapshotMapper;

/**
 * 创建 AdminExchangeRateServiceImpl 实例并注入其运行所需依赖。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param sourceMapper source Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param rawRateMapper raw Rate Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param ruleMapper rule Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param businessRateMapper business Rate Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param usageSnapshotMapper usage Snapshot Mapper 输入值，含义由调用方法名称和所属业务对象限定
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
    public PageResult<RawRateResponse> pageRawRates(RawRateQuery request) {
        RawRateQuery query = request == null ? new RawRateQuery() : request;
        Page<ExchangeRawRateDO> page = rawRateMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ExchangeRawRateDO>lambdaQuery()
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
                        .orderByDesc(ExchangeRawRateDO::getPublishTime)
                        .orderByDesc(ExchangeRawRateDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRawRateResponse).toList());
    }

    /**
     * 按查询条件导出原始汇率记录。
     *
     * @param request 查询条件，允许为空
     * @return 原始汇率列表
     */
    @Override
    public List<RawRateResponse> listRawRates(RawRateQuery request) {
        RawRateQuery query = request == null ? new RawRateQuery() : request;
        return rawRateMapper.selectList(Wrappers.<ExchangeRawRateDO>lambdaQuery()
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
                        .orderByDesc(ExchangeRawRateDO::getPublishTime)
                        .orderByDesc(ExchangeRawRateDO::getId))
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
    public PageResult<BusinessRateResponse> pageBusinessRates(BusinessRateQuery request) {
        BusinessRateQuery query = request == null ? new BusinessRateQuery() : request;
        Page<ExchangeBusinessRateDO> page = businessRateMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                        .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeBusinessRateDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getSourceCode()), ExchangeBusinessRateDO::getSourceCode, trimUpper(query.getSourceCode()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeBusinessRateDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeBusinessRateDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .eq(StringUtils.hasText(query.getRateStatus()), ExchangeBusinessRateDO::getRateStatus, trimUpper(query.getRateStatus()))
                        .eq(StringUtils.hasText(query.getGenerateMethod()), ExchangeBusinessRateDO::getGenerateMethod, trimUpper(query.getGenerateMethod()))
                        .orderByDesc(ExchangeBusinessRateDO::getEffectiveTime)
                        .orderByDesc(ExchangeBusinessRateDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toBusinessRateResponse).toList());
    }

    /**
     * 按查询条件导出业务汇率。
     *
     * @param request 查询条件，允许为空
     * @return 业务汇率列表
     */
    @Override
    public List<BusinessRateResponse> listBusinessRates(BusinessRateQuery request) {
        BusinessRateQuery query = request == null ? new BusinessRateQuery() : request;
        return businessRateMapper.selectList(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                        .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getRateType()), ExchangeBusinessRateDO::getRateType, trimUpper(query.getRateType()))
                        .eq(StringUtils.hasText(query.getSourceCode()), ExchangeBusinessRateDO::getSourceCode, trimUpper(query.getSourceCode()))
                        .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeBusinessRateDO::getBaseCurrency, trimUpper(query.getBaseCurrency()))
                        .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeBusinessRateDO::getQuoteCurrency, trimUpper(query.getQuoteCurrency()))
                        .eq(StringUtils.hasText(query.getRateStatus()), ExchangeBusinessRateDO::getRateStatus, trimUpper(query.getRateStatus()))
                        .eq(StringUtils.hasText(query.getGenerateMethod()), ExchangeBusinessRateDO::getGenerateMethod, trimUpper(query.getGenerateMethod()))
                        .orderByDesc(ExchangeBusinessRateDO::getEffectiveTime)
                        .orderByDesc(ExchangeBusinessRateDO::getId))
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
        entity.setCreateTime(now);
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
    @Transactional(rollbackFor = Exception.class)
    public BusinessRateResponse updateBusinessRateStatus(Long id, Integer status) {
        ExchangeBusinessRateDO entity = findBusinessRate(id);
        validateStatus(status);
        entity.setRateStatus(status == ENABLED ? RATE_STATUS_ENABLED : RATE_STATUS_DISABLED);
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
     * 校验 validate Source Request 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
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
     * 校验 validate Raw Rate Request 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
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
     * 校验 validate Rule Request 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
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
     * 校验 validate Business Rate Request 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
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
     * 判断 has Overlapped Enabled Rule 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
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
     * 完成 time Range Overlaps 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param start1 start1 输入值，含义由调用方法名称和所属业务对象限定
     * @param end1 end1 输入值，含义由调用方法名称和所属业务对象限定
     * @param start2 start2 输入值，含义由调用方法名称和所属业务对象限定
     * @param end2 end2 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 校验 validate Business Rate Source 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
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
     * 推进 expire Current Business Rate 对应的状态或处理结果，并保留后续查询所需信息。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param expireTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void expireCurrentBusinessRate(ExchangeRateRuleDO rule, ExchangeRawRateDO rawRate, LocalDateTime expireTime) {
        expireCurrentBusinessRate(rule.getRateType(), rawRate.getSourceCode(), rawRate.getBaseCurrency(), rawRate.getQuoteCurrency(), expireTime);
    }

    /**
     * 推进 expire Current Business Rate 对应的状态或处理结果，并保留后续查询所需信息。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rateType rate Type 输入值，含义由调用方法名称和所属业务对象限定
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @param baseCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param quoteCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param expireTime 时间值，使用系统约定时区或调用方传入的业务时区解释
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
                    existing.setUpdateTime(expireTime);
                    businessRateMapper.updateById(existing);
                });
    }

    /**
     * 写入或更新 insert Manual Business Rate 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(NOT_DELETED);
        businessRateMapper.insert(entity);
        return entity;
    }

    /**
     * 查询 select Raw Rate Value 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param rateField rate Field 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 完成 adjust Ratio 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param adjustMethod adjust Method 输入值，含义由调用方法名称和所属业务对象限定
     * @param adjustValue adjust Value 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 转换生成 to Rounding Mode 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param roundingMode rounding Mode 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private RoundingMode toRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ROUND_UP" -> RoundingMode.UP;
            case "ROUND_DOWN" -> RoundingMode.DOWN;
            default -> RoundingMode.HALF_UP;
        };
    }

    /**
     * 构建 build Adjust Description 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @param originalRate original Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param finalRate final Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private String buildAdjustDescription(ExchangeRateRuleDO rule, BigDecimal originalRate, BigDecimal finalRate) {
        return rule.getRateField() + " " + originalRate.toPlainString()
                + ", " + rule.getAdjustDirection() + " " + rule.getAdjustValue().toPlainString()
                + " " + rule.getAdjustMethod() + ", scale " + rule.getDecimalScale()
                + ", " + rule.getRoundingMode() + ", final " + finalRate.toPlainString();
    }

    /**
     * 填充 fill Source 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
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
     * 填充 fill Raw Rate 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
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
     * 填充 fill Rule 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
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
     * 转换生成 to Source Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 转换生成 to Raw Rate Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 转换生成 to Rule Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 转换生成 to Business Rate Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 转换生成 to Usage Snapshot Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 查询 find Source 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 查询 find Raw Rate 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 查询 find Rule 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 查询 find Business Rate 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 强制校验 require Source Exists 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
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
     * 判断 has Raw Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasRawRate(String sourceCode) {
        return rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has Rule 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasRule(String sourceCode) {
        return ruleMapper.selectCount(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                .eq(ExchangeRateRuleDO::getSourceCode, sourceCode)
                .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has Business Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasBusinessRate(String sourceCode) {
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getSourceCode, sourceCode)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断 has Business Rate By Raw Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRateId raw Rate Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasBusinessRateByRawRate(Long rawRateId) {
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getRawRateId, rawRateId)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 校验 validate Status 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     */
    private void validateStatus(Integer status) {
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw badRequest("状态必须为0停用或1启用");
        }
    }

    /**
     * 校验 validate Currency 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @param allowAll allow All 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateCurrency(String currency, boolean allowAll) {
        if (!StringUtils.hasText(currency) || !CURRENCY_PATTERN.matcher(currency).matches() || (!allowAll && ALL.equals(currency))) {
            throw badRequest("币种必须为 ISO 4217 三位字母代码" + (allowAll ? "或 ALL" : ""));
        }
    }

    /**
     * 判断 has Any Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rates rates 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
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
     * 校验 validate Positive Rate 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rate rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param fieldName field Name 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validatePositiveRate(BigDecimal rate, String fieldName) {
        if (rate != null && rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(fieldName + "必须大于0");
        }
    }

    /**
     * 校验 validate Positive Required Rate 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rate rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param fieldName field Name 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validatePositiveRequiredRate(BigDecimal rate, String fieldName) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(fieldName + "必须大于0");
        }
    }

    /**
     * 完成 bad Request 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /**
     * 完成 not Found 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException notFound(String message) {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), message);
    }

    /**
     * 完成 trim 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 完成 trim Upper 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String trimUpper(String value) {
        return trim(value) == null ? null : trim(value).toUpperCase(Locale.ROOT);
    }
}

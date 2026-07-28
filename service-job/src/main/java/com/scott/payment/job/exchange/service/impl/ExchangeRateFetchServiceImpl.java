package com.scott.payment.job.exchange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeBusinessRateDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateFetchLogDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateRuleDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRawRateDO;
import com.scott.payment.job.exchange.provider.ExchangeRateProvider;
import com.scott.payment.job.exchange.provider.ExchangeRateProviderRegistry;
import com.scott.payment.job.exchange.service.ExchangeRateFetchService;
import com.scott.payment.job.mapper.ExchangeJobBusinessRateMapper;
import com.scott.payment.job.mapper.ExchangeJobRateRuleMapper;
import com.scott.payment.job.mapper.ExchangeJobRateSourceMapper;
import com.scott.payment.job.mapper.ExchangeJobRawRateMapper;
import com.scott.payment.job.mapper.ExchangeRateFetchLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchServiceImpl
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Rate Fetch Service Impl 服务实现，位于 调度任务服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class ExchangeRateFetchServiceImpl implements ExchangeRateFetchService {

    /**
     * NOT DELETED，用于保存 Exchange Rate Fetch Service Impl 中与 notdeleted 相关的业务属性。
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
     * BOC，用于保存 Exchange Rate Fetch Service Impl 中与 boc 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String BOC = "BOC";
    /**
     * AUTO，用于保存 Exchange Rate Fetch Service Impl 中与 auto 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String AUTO = "AUTO";
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
     * RATE STATUS EXPIRED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * SUCCESS，用于保存 Exchange Rate Fetch Service Impl 中与 success 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SUCCESS = "SUCCESS";
    /**
     * FAILED，用于保存 Exchange Rate Fetch Service Impl 中与 failed 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String FAILED = "FAILED";
    /**
     * PARTIAL SUCCESS，用于保存 Exchange Rate Fetch Service Impl 中与 partialsuccess 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    /**
     * ALL，用于保存 Exchange Rate Fetch Service Impl 中与 all 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ALL = "ALL";
    /**
     * UP，用于保存 Exchange Rate Fetch Service Impl 中与 up 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String UP = "UP";
    /**
     * NONE，用于保存 Exchange Rate Fetch Service Impl 中与 none 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String NONE = "NONE";
    /**
     * BP，用于保存 Exchange Rate Fetch Service Impl 中与 bp 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String BP = "BP";

    /**
     * source Mapper 依赖，用于 Exchange Rate Fetch Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeJobRateSourceMapper sourceMapper;
    /**
     * raw Rate Mapper 依赖，用于 Exchange Rate Fetch Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeJobRawRateMapper rawRateMapper;
    /**
     * rule Mapper 依赖，用于 Exchange Rate Fetch Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeJobRateRuleMapper ruleMapper;
    /**
     * business Rate Mapper 依赖，用于 Exchange Rate Fetch Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeJobBusinessRateMapper businessRateMapper;
    /**
     * fetch Log Mapper 依赖，用于 Exchange Rate Fetch Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRateFetchLogMapper fetchLogMapper;
    /**
     * provider Registry，用于保存 Exchange Rate Fetch Service Impl 中与 providerregistry 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExchangeRateProviderRegistry providerRegistry;

    /**
     * 构造汇率拉取服务实现。
     *
     * @param sourceMapper          汇率源 Mapper
     * @param rawRateMapper         原始汇率 Mapper
     * @param ruleMapper            汇率规则 Mapper
     * @param businessRateMapper    业务汇率 Mapper
     * @param fetchLogMapper        拉取日志 Mapper
     * @param providerRegistry      汇率源 Provider 注册表
     */
    public ExchangeRateFetchServiceImpl(ExchangeJobRateSourceMapper sourceMapper,
                                        ExchangeJobRawRateMapper rawRateMapper,
                                        ExchangeJobRateRuleMapper ruleMapper,
                                        ExchangeJobBusinessRateMapper businessRateMapper,
                                        ExchangeRateFetchLogMapper fetchLogMapper,
                                        ExchangeRateProviderRegistry providerRegistry) {
        this.sourceMapper = sourceMapper;
        this.rawRateMapper = rawRateMapper;
        this.ruleMapper = ruleMapper;
        this.businessRateMapper = businessRateMapper;
        this.fetchLogMapper = fetchLogMapper;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 执行汇率源拉取任务。
     *
     * <p>本方法在一个事务中完成 Provider 拉取、原始汇率去重入库、业务汇率自动生成、拉取日志记录和汇率源最近拉取状态更新。
     * dryRun 模式只执行解析和校验，不写入原始汇率和业务汇率记录。</p>
     *
     * @param request 拉取请求，允许为空，默认使用 BOC
     * @param context 任务执行上下文，可为空
     * @return 拉取结果统计
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExchangeRateFetchResult fetch(ExchangeRateFetchRequest request, JobExecuteContext context) {
        ExchangeRateFetchRequest actualRequest = request == null ? new ExchangeRateFetchRequest() : request;
        String sourceCode = StringUtils.hasText(actualRequest.getSourceCode())
                ? actualRequest.getSourceCode().trim().toUpperCase(Locale.ROOT)
                : BOC;
        String batchNo = context != null && StringUtils.hasText(context.getRunId())
                ? context.getRunId()
                : "FX-" + UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.now();
        ExchangeRateFetchResult result = new ExchangeRateFetchResult();
        result.setBatchNo(batchNo);
        result.setSourceCode(sourceCode);
        ExchangeRateSourceDO source = null;
        try {
            source = findEnabledSource(sourceCode);
            ExchangeRateProvider provider = providerRegistry.getRequiredProvider(sourceCode);
            List<RawRateItem> items = provider.fetch(source);
            result.setTotalCount(items.size());
            boolean dryRun = Boolean.TRUE.equals(actualRequest.getDryRun());
            for (RawRateItem item : items) {
                processItem(source, item, batchNo, dryRun, result);
            }
            result.setFetchStatus(result.getSkipCount() > 0 ? PARTIAL_SUCCESS : SUCCESS);
        } catch (Exception ex) {
            result.setFetchStatus(FAILED);
            result.setErrorMessage(ex.getMessage());
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            insertFetchLog(result, source, startTime, endTime);
            if (source != null) {
                updateSourceFetchStatus(source, result.getFetchStatus(), endTime);
            }
        }
        return result;
    }

    private void processItem(ExchangeRateSourceDO source,
                             RawRateItem item,
                             String batchNo,
                             boolean dryRun,
                             ExchangeRateFetchResult result) {
        String sourceCurrencyName = item.getSourceCurrencyName();
        String baseCurrency = item.getBaseCurrency();
        if (!StringUtils.hasText(baseCurrency)) {
            skip(result, "汇率源未返回标准币种代码：" + sourceCurrencyName);
            return;
        }
        if (item.getPublishTime() == null) {
            skip(result, "缺少发布时间：" + sourceCurrencyName);
            return;
        }
        if (!hasAnyRate(item)) {
            skip(result, "缺少有效报价：" + sourceCurrencyName);
            return;
        }
        if (existsRawRate(source.getSourceCode(), baseCurrency, item.getQuoteCurrency(), item.getPublishTime())) {
            result.setDuplicateCount(result.getDuplicateCount() + 1);
            backfillMissingBusinessRates(source.getSourceCode(), baseCurrency, item.getQuoteCurrency(), item.getPublishTime(), result);
            return;
        }
        if (!dryRun) {
            ExchangeRawRateDO entity = new ExchangeRawRateDO();
            entity.setSourceCode(source.getSourceCode());
            entity.setBaseCurrency(baseCurrency);
            entity.setQuoteCurrency(item.getQuoteCurrency());
            entity.setCashBuyRate(item.getCashBuyRate());
            entity.setCashSellRate(item.getCashSellRate());
            entity.setSpotBuyRate(item.getSpotBuyRate());
            entity.setSpotSellRate(item.getSpotSellRate());
            entity.setMiddleRate(item.getMiddleRate());
            entity.setPublishTime(item.getPublishTime());
            entity.setFetchTime(LocalDateTime.now());
            entity.setEffectiveTime(item.getPublishTime());
            entity.setCreateMethod(AUTO);
            entity.setBatchNo(batchNo);
            entity.setRateStatus(RATE_STATUS_ENABLED);
            entity.setCreateBy("system");
            entity.setCreateTime(entity.getFetchTime());
            entity.setUpdateBy("system");
            entity.setUpdateTime(entity.getFetchTime());
            entity.setDeleted(NOT_DELETED);
            rawRateMapper.insert(entity);
            generateBusinessRates(entity, result);
        }
        result.setSuccessCount(result.getSuccessCount() + 1);
    }

    private void backfillMissingBusinessRates(String sourceCode,
                                              String baseCurrency,
                                              String quoteCurrency,
                                              LocalDateTime publishTime,
                                              ExchangeRateFetchResult result) {
        ExchangeRawRateDO rawRate = rawRateMapper.selectOne(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getBaseCurrency, baseCurrency)
                .eq(ExchangeRawRateDO::getQuoteCurrency, quoteCurrency)
                .eq(ExchangeRawRateDO::getPublishTime, publishTime)
                .eq(ExchangeRawRateDO::getRateStatus, RATE_STATUS_ENABLED)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (rawRate == null) {
            return;
        }
        LocalDateTime generateTime = LocalDateTime.now();
        List<ExchangeRateRuleDO> rules = findMatchedRules(rawRate, generateTime);
        Map<String, ExchangeRateRuleDO> bestRuleByType = rules.stream()
                .collect(Collectors.toMap(ExchangeRateRuleDO::getRateType, Function.identity(), this::betterRule));
        bestRuleByType.values().stream()
                .filter(rule -> !existsBusinessRate(rawRate.getId(), rule.getId()))
                .sorted(Comparator.comparing(ExchangeRateRuleDO::getRateType))
                .forEach(rule -> generateBusinessRate(rawRate, rule, generateTime));
        if (!bestRuleByType.isEmpty()) {
            result.getWarnings().add("已回补重复原始汇率缺失的业务汇率：" + sourceCode + " " + baseCurrency + "/" + quoteCurrency);
        }
    }

    /**
     * 创建business汇率，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     */
    private void generateBusinessRates(ExchangeRawRateDO rawRate, ExchangeRateFetchResult result) {
        LocalDateTime generateTime = LocalDateTime.now();
        List<ExchangeRateRuleDO> rules = findMatchedRules(rawRate, generateTime);
        if (rules.isEmpty()) {
            result.getWarnings().add("未匹配启用汇率规则：" + rawRate.getSourceCode()
                    + " " + rawRate.getBaseCurrency() + "/" + rawRate.getQuoteCurrency());
            return;
        }
        Map<String, ExchangeRateRuleDO> bestRuleByType = rules.stream()
                .collect(Collectors.toMap(ExchangeRateRuleDO::getRateType, Function.identity(), this::betterRule));
        bestRuleByType.values().stream()
                .sorted(Comparator.comparing(ExchangeRateRuleDO::getRateType))
                .forEach(rule -> generateBusinessRate(rawRate, rule, generateTime));
    }

    /**
     * 查询命中的汇率规则，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 调度任务服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param generateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<ExchangeRateRuleDO> findMatchedRules(ExchangeRawRateDO rawRate, LocalDateTime generateTime) {
        return ruleMapper.selectList(Wrappers.<ExchangeRateRuleDO>lambdaQuery()
                        .eq(ExchangeRateRuleDO::getDeleted, NOT_DELETED)
                        .eq(ExchangeRateRuleDO::getRuleStatus, ENABLED)
                        .and(wrapper -> wrapper.eq(ExchangeRateRuleDO::getSourceCode, rawRate.getSourceCode())
                                .or().eq(ExchangeRateRuleDO::getSourceCode, ALL))
                        .and(wrapper -> wrapper.eq(ExchangeRateRuleDO::getBaseCurrency, rawRate.getBaseCurrency())
                                .or().eq(ExchangeRateRuleDO::getBaseCurrency, ALL))
                        .and(wrapper -> wrapper.eq(ExchangeRateRuleDO::getQuoteCurrency, rawRate.getQuoteCurrency())
                                .or().eq(ExchangeRateRuleDO::getQuoteCurrency, ALL))
                        .and(wrapper -> wrapper.isNull(ExchangeRateRuleDO::getEffectiveStartTime)
                                .or().le(ExchangeRateRuleDO::getEffectiveStartTime, generateTime))
                        .and(wrapper -> wrapper.isNull(ExchangeRateRuleDO::getEffectiveEndTime)
                                .or().ge(ExchangeRateRuleDO::getEffectiveEndTime, generateTime)))
                .stream()
                .filter(rule -> selectRawRateValue(rawRate, rule.getRateField()) != null)
                .toList();
    }

    /**
     * 整理better规则，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param left left 输入值，参与 left 的查询、校验、转换、写入或日志摘要
     * @param right right 输入值，参与 right 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ExchangeRateRuleDO betterRule(ExchangeRateRuleDO left, ExchangeRateRuleDO right) {
        int priorityCompare = Integer.compare(priority(left), priority(right));
        if (priorityCompare < 0) {
            return left;
        }
        if (priorityCompare > 0) {
            return right;
        }
        int specificityCompare = Integer.compare(specificity(right), specificity(left));
        if (specificityCompare < 0) {
            return left;
        }
        if (specificityCompare > 0) {
            return right;
        }
        return left.getId() != null && right.getId() != null && right.getId() < left.getId() ? right : left;
    }

    /**
     * 规范化priority，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int priority(ExchangeRateRuleDO rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    /**
     * 规范化specificity，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int specificity(ExchangeRateRuleDO rule) {
        int score = 0;
        score += ALL.equals(rule.getSourceCode()) ? 0 : 1;
        score += ALL.equals(rule.getBaseCurrency()) ? 0 : 1;
        score += ALL.equals(rule.getQuoteCurrency()) ? 0 : 1;
        return score;
    }

    /**
     * 创建业务汇率，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @param generateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void generateBusinessRate(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule, LocalDateTime generateTime) {
        BigDecimal originalRate = selectRawRateValue(rawRate, rule.getRateField());
        if (originalRate == null || originalRate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal finalRate = calculateFinalRate(originalRate, rule);
        LocalDateTime effectiveTime = businessEffectiveTime(rawRate, rule, generateTime);
        expireCurrentBusinessRate(rule.getRateType(), rawRate.getSourceCode(), rawRate.getBaseCurrency(), rawRate.getQuoteCurrency(), generateTime);
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
        entity.setEffectiveTime(effectiveTime);
        entity.setGenerateMethod(AUTO);
        entity.setRateStatus(RATE_STATUS_ENABLED);
        entity.setCreateBy("system");
        entity.setCreateTime(generateTime);
        entity.setUpdateBy("system");
        entity.setUpdateTime(generateTime);
        entity.setDeleted(NOT_DELETED);
        businessRateMapper.insert(entity);
    }

    /**
     * 整理失效currentbusiness汇率，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rateType rate Type 输入值，参与 汇率type 的查询、校验、转换、写入或日志摘要
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
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
                    existing.setUpdateBy("system");
                    existing.setUpdateTime(expireTime);
                    businessRateMapper.updateById(existing);
                });
    }

    /**
     * 判断 exists business rate 条件是否成立，用于控制 Exchange Rate Fetch Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param rawRateId raw Rate ID 输入值，参与 raw汇率ID 的查询、校验、转换、写入或日志摘要
     * @param ruleId rule ID 输入值，参与 规则ID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean existsBusinessRate(Long rawRateId, Long ruleId) {
        if (rawRateId == null || ruleId == null) {
            return false;
        }
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getRawRateId, rawRateId)
                .eq(ExchangeBusinessRateDO::getRuleId, ruleId)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 整理businesseffective时间，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @param generateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime businessEffectiveTime(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule, LocalDateTime generateTime) {
        LocalDateTime rawEffectiveTime = rawRate.getEffectiveTime() == null ? generateTime : rawRate.getEffectiveTime();
        if (rule.getEffectiveStartTime() != null && rawEffectiveTime.isBefore(rule.getEffectiveStartTime())) {
            return rule.getEffectiveStartTime();
        }
        return rawEffectiveTime;
    }

    /**
     * 查询启用的汇率来源，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 调度任务服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ExchangeRateSourceDO findEnabledSource(String sourceCode) {
        ExchangeRateSourceDO source = sourceMapper.selectOne(Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                .eq(ExchangeRateSourceDO::getSourceCode, sourceCode)
                .eq(ExchangeRateSourceDO::getSourceStatus, ENABLED)
                .eq(ExchangeRateSourceDO::getDeleted, NOT_DELETED));
        if (source == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "enabled exchange rate source not found: " + sourceCode);
        }
        return source;
    }

    /**
     * 判断 exists raw rate 条件是否成立，用于控制 Exchange Rate Fetch Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param sourceCode source Code 输入值，参与 来源编码 的查询、校验、转换、写入或日志摘要
     * @param baseCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param quoteCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param publishTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean existsRawRate(String sourceCode, String baseCurrency, String quoteCurrency, LocalDateTime publishTime) {
        return rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getBaseCurrency, baseCurrency)
                .eq(ExchangeRawRateDO::getQuoteCurrency, quoteCurrency)
                .eq(ExchangeRawRateDO::getPublishTime, publishTime)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 创建汇率抓取日志，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 调度任务服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param startTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void insertFetchLog(ExchangeRateFetchResult result, ExchangeRateSourceDO source, LocalDateTime startTime, LocalDateTime endTime) {
        ExchangeRateFetchLogDO log = new ExchangeRateFetchLogDO();
        log.setBatchNo(result.getBatchNo());
        log.setSourceCode(result.getSourceCode());
        log.setFetchStartTime(startTime);
        log.setFetchEndTime(endTime);
        log.setFetchStatus(result.getFetchStatus());
        log.setRequestUrl(source == null ? null : source.getRequestUrl());
        log.setTotalCount(result.getTotalCount());
        log.setSuccessCount(result.getSuccessCount());
        log.setDuplicateCount(result.getDuplicateCount());
        log.setSkipCount(result.getSkipCount());
        log.setErrorMessage(result.getErrorMessage());
        log.setCreateTime(LocalDateTime.now());
        fetchLogMapper.insert(log);
    }

    /**
     * 更新汇率来源抓取状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 调度任务服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param fetchStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void updateSourceFetchStatus(ExchangeRateSourceDO source, String fetchStatus, LocalDateTime endTime) {
        source.setLastFetchTime(endTime);
        source.setLastFetchStatus(fetchStatus);
        source.setUpdateBy("system");
        source.setUpdateTime(endTime);
        sourceMapper.updateById(source);
    }

    /**
     * 判断 has any rate 条件是否成立，用于控制 Exchange Rate Fetch Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param item item 输入值，参与 item 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasAnyRate(RawRateItem item) {
        return positive(item.getCashBuyRate())
                || positive(item.getCashSellRate())
                || positive(item.getSpotBuyRate())
                || positive(item.getSpotSellRate())
                || positive(item.getMiddleRate());
    }

    /**
     * 规范化positive，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 规范化skip，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param warning warning 输入值，参与 warning 的查询、校验、转换、写入或日志摘要
     */
    private void skip(ExchangeRateFetchResult result, String warning) {
        result.setSkipCount(result.getSkipCount() + 1);
        result.getWarnings().add(warning);
    }

    /**
     * 查询原始汇率值，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 调度任务服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param rawRate raw Rate 输入值，参与 raw汇率 的查询、校验、转换、写入或日志摘要
     * @param rateField rate Field 输入值，参与 汇率field 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BigDecimal selectRawRateValue(ExchangeRawRateDO rawRate, String rateField) {
        return switch (rateField) {
            case "SPOT_BUY_RATE" -> rawRate.getSpotBuyRate();
            case "CASH_BUY_RATE" -> rawRate.getCashBuyRate();
            case "SPOT_SELL_RATE" -> rawRate.getSpotSellRate();
            case "CASH_SELL_RATE" -> rawRate.getCashSellRate();
            case "MIDDLE_RATE" -> rawRate.getMiddleRate();
            default -> null;
        };
    }

    /**
     * 解析calculatefinal汇率，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 调度任务服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param originalRate original Rate 输入值，参与 original汇率 的查询、校验、转换、写入或日志摘要
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal calculateFinalRate(BigDecimal originalRate, ExchangeRateRuleDO rule) {
        BigDecimal multiplier = BigDecimal.ONE;
        if (!NONE.equals(rule.getAdjustDirection())) {
            BigDecimal adjustRatio = adjustRatio(rule.getAdjustMethod(), rule.getAdjustValue());
            multiplier = UP.equals(rule.getAdjustDirection())
                    ? BigDecimal.ONE.add(adjustRatio)
                    : BigDecimal.ONE.subtract(adjustRatio);
        }
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "exchange rate multiplier must be positive");
        }
        return originalRate.multiply(multiplier)
                .setScale(rule.getDecimalScale(), toRoundingMode(rule.getRoundingMode()));
    }

    /**
     * 规范化adjustratio，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
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
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
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
}

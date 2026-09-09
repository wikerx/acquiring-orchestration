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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchServiceImpl
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : 汇率汇率fetch服务实现，位于 调度任务服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class ExchangeRateFetchServiceImpl implements ExchangeRateFetchService {

    /**
     * {@code NOT_DELETED}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * {@code BOC}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String BOC = "BOC";
    /**
     * 自动常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String AUTO = "AUTO";
    /**
     * 汇率状态启用标识，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_ENABLED = "ENABLED";
    /**
     * {@code RATE_STATUS_EXPIRED}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * 成功常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SUCCESS = "SUCCESS";
    /**
     * 失败常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String FAILED = "FAILED";
    /**
     * {@code PARTIAL_SUCCESS}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    /**
     * {@code ALL}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ALL = "ALL";
    /**
     * {@code UP}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String UP = "UP";
    /**
     * {@code NONE}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String NONE = "NONE";
    /**
     * {@code BP}常量，统一 {@code ExchangeRateFetchServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String BP = "BP";

    private final ExchangeJobRateSourceMapper sourceMapper;
    private final ExchangeJobRawRateMapper rawRateMapper;
    private final ExchangeJobRateRuleMapper ruleMapper;
    private final ExchangeJobBusinessRateMapper businessRateMapper;
    private final ExchangeRateFetchLogMapper fetchLogMapper;
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
            result.setErrorMessage(ex.getClass().getSimpleName());
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            insertFetchLog(result, source, startTime, endTime);
            if (source != null) {
                updateSourceFetchStatus(source, result.getFetchStatus(), endTime);
            }
        }
        return result;
    }

    /**
     * 校验并处理汇率源返回的一条原始报价。
     * <p>
     * 缺少标准币种、发布时间或有效报价时记为跳过；重复原始报价不再次插入，但会尝试补齐
     * 缺失的业务汇率。{@code dryRun} 只做校验和统计，不写原始或业务汇率表。
     * </p>
     *
     * @param source  已启用的汇率源
     * @param item    原始报价
     * @param batchNo 本次抓取批次号
     * @param dryRun  是否仅演练
     * @param result  本批次统计结果
     */
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

    /**
     * 为已存在的原始报价补齐尚未生成的业务汇率。
     * <p>
     * 按币种对、发布时间和启用状态精确定位原始报价；每种 rateType 只选择优先级最高规则，
     * 并通过原始汇率与规则组合判重，避免重复生成交易或结算汇率。
     * </p>
     *
     * @param sourceCode   汇率源编码
     * @param baseCurrency 基准币种
     * @param quoteCurrency 报价币种
     * @param publishTime  原始报价发布时间
     * @param result       本批次统计结果
     */
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

    private int priority(ExchangeRateRuleDO rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    private int specificity(ExchangeRateRuleDO rule) {
        int score = 0;
        score += ALL.equals(rule.getSourceCode()) ? 0 : 1;
        score += ALL.equals(rule.getBaseCurrency()) ? 0 : 1;
        score += ALL.equals(rule.getQuoteCurrency()) ? 0 : 1;
        return score;
    }

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

    private boolean existsBusinessRate(Long rawRateId, Long ruleId) {
        if (rawRateId == null || ruleId == null) {
            return false;
        }
        return businessRateMapper.selectCount(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getRawRateId, rawRateId)
                .eq(ExchangeBusinessRateDO::getRuleId, ruleId)
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    private LocalDateTime businessEffectiveTime(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule, LocalDateTime generateTime) {
        LocalDateTime rawEffectiveTime = rawRate.getEffectiveTime() == null ? generateTime : rawRate.getEffectiveTime();
        if (rule.getEffectiveStartTime() != null && rawEffectiveTime.isBefore(rule.getEffectiveStartTime())) {
            return rule.getEffectiveStartTime();
        }
        return rawEffectiveTime;
    }

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

    private boolean existsRawRate(String sourceCode, String baseCurrency, String quoteCurrency, LocalDateTime publishTime) {
        return rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getBaseCurrency, baseCurrency)
                .eq(ExchangeRawRateDO::getQuoteCurrency, quoteCurrency)
                .eq(ExchangeRawRateDO::getPublishTime, publishTime)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)) > 0;
    }

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

    private void updateSourceFetchStatus(ExchangeRateSourceDO source, String fetchStatus, LocalDateTime endTime) {
        source.setLastFetchTime(endTime);
        source.setLastFetchStatus(fetchStatus);
        source.setUpdateBy("system");
        source.setUpdateTime(endTime);
        sourceMapper.updateById(source);
    }

    private boolean hasAnyRate(RawRateItem item) {
        return positive(item.getCashBuyRate())
                || positive(item.getCashSellRate())
                || positive(item.getSpotBuyRate())
                || positive(item.getSpotSellRate())
                || positive(item.getMiddleRate());
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private void skip(ExchangeRateFetchResult result, String warning) {
        result.setSkipCount(result.getSkipCount() + 1);
        result.getWarnings().add(warning);
    }

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

    private BigDecimal adjustRatio(String adjustMethod, BigDecimal adjustValue) {
        if (adjustValue == null) {
            return BigDecimal.ZERO;
        }
        if (BP.equals(adjustMethod)) {
            return adjustValue.divide(new BigDecimal("10000"), 12, RoundingMode.HALF_UP);
        }
        return adjustValue.divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP);
    }

    private RoundingMode toRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ROUND_UP" -> RoundingMode.UP;
            case "ROUND_DOWN" -> RoundingMode.DOWN;
            default -> RoundingMode.HALF_UP;
        };
    }

    private String buildAdjustDescription(ExchangeRateRuleDO rule, BigDecimal originalRate, BigDecimal finalRate) {
        return rule.getRateField() + " " + originalRate.toPlainString()
                + ", " + rule.getAdjustDirection() + " " + rule.getAdjustValue().toPlainString()
                + " " + rule.getAdjustMethod() + ", scale " + rule.getDecimalScale()
                + ", " + rule.getRoundingMode() + ", final " + finalRate.toPlainString();
    }
}

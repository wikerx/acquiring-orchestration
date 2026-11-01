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
 * 汇率抓取服务实现，负责保存外部原始报价，并按生效规则生成可追溯的业务汇率。
 */
@Service
public class ExchangeRateFetchServiceImpl implements ExchangeRateFetchService {

    /**
     * 未逻辑删除标识，用于所有汇率配置和事实查询。
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     */
    private static final int ENABLED = 1;
    /**
     * 中国银行汇率源编码，也是未指定来源时的默认抓取源。
     */
    private static final String BOC = "BOC";
    /**
     * 自动生成方式，区分调度计算汇率与人工录入汇率。
     */
    private static final String AUTO = "AUTO";

    private static final String RATE_STATUS_ENABLED = "ENABLED";

    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * 本批全部有效报价处理成功。
     */
    private static final String SUCCESS = "SUCCESS";
    /**
     * 本批抓取或解析失败，未得到可用报价。
     */
    private static final String FAILED = "FAILED";
    /**
     * 本批至少保存一条报价，同时存在被跳过或处理失败的报价。
     */
    private static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    /**
     * 规则通配值，表示来源或币种维度不作限定。
     */
    private static final String ALL = "ALL";
    /**
     * 向上调整方向编码。
     */
    private static final String UP = "UP";
    /**
     * 不调整原始汇率的方向编码。
     */
    private static final String NONE = "NONE";
    /**
     * 基点调整口径，一万基点等于一倍比例。
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

    /**
     * 为一条新原始报价按各业务汇率类型选择最佳规则并生成当前有效汇率。
     *
     * @param rawRate 已持久化原始报价
     * @param result 当前拉取批次统计
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
     * 查询在生成时刻有效且能读取指定原始报价字段的候选规则。
     *
     * @param rawRate 原始报价
     * @param generateTime 业务汇率生成时刻
     * @return 可参与规则选优的候选列表
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
     * 按优先级、匹配维度具体程度和规则主键稳定选择更优规则。
     *
     * @param left 候选规则
     * @param right 候选规则
     * @return 确定性胜出规则
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
     * 获取规则优先级，未配置时使用兼容默认值 100。
     *
     * @param rule 汇率规则
     * @return 越小越优先的排序值
     */
    private int priority(ExchangeRateRuleDO rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    /**
     * 计算来源、原始币种和目标币种三个维度的精确匹配数量。
     *
     * @param rule 汇率规则
     * @return 0 到 3 的具体程度分值
     */
    private int specificity(ExchangeRateRuleDO rule) {
        int score = 0;
        score += ALL.equals(rule.getSourceCode()) ? 0 : 1;
        score += ALL.equals(rule.getBaseCurrency()) ? 0 : 1;
        score += ALL.equals(rule.getQuoteCurrency()) ? 0 : 1;
        return score;
    }

    /**
     * 使用冻结的原始报价和规则生成一条业务汇率，并失效同维度旧汇率。
     *
     * @param rawRate 原始报价
     * @param rule 胜出的业务汇率规则
     * @param generateTime 生成时刻
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
     * 失效同类型、来源和币种对下的旧业务汇率。
     *
     * @param rateType 业务汇率类型
     * @param sourceCode 汇率来源
     * @param baseCurrency 原始币种
     * @param quoteCurrency 目标币种
     * @param expireTime 失效时刻
     */
    private void expireCurrentBusinessRate(String rateType, String sourceCode, String baseCurrency,
                                           String quoteCurrency, LocalDateTime expireTime) {
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
     * 判断同一原始报价和规则是否已生成业务汇率，支持重复拉取幂等回补。
     *
     * @param rawRateId 原始报价主键
     * @param ruleId 汇率规则主键
     * @return true 表示生成事实已存在
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
     * 取原始报价生效时间与规则生效起点中的较晚值作为业务汇率生效时间。
     *
     * @param rawRate 原始报价
     * @param rule 汇率规则
     * @param generateTime 缺少原始生效时间时的回退时刻
     * @return 业务汇率生效时刻
     */
    private LocalDateTime businessEffectiveTime(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule,
                                                LocalDateTime generateTime) {
        LocalDateTime rawEffectiveTime = rawRate.getEffectiveTime() == null ? generateTime : rawRate.getEffectiveTime();
        if (rule.getEffectiveStartTime() != null && rawEffectiveTime.isBefore(rule.getEffectiveStartTime())) {
            return rule.getEffectiveStartTime();
        }
        return rawEffectiveTime;
    }

    /**
     * 获取指定编码的启用汇率源，缺失时明确终止本批拉取。
     *
     * @param sourceCode 汇率源编码
     * @return 启用的汇率源配置
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
     * 按来源、币种对和发布时间判断原始报价是否已入库。
     *
     * @param sourceCode 汇率源编码
     * @param baseCurrency 原始币种
     * @param quoteCurrency 目标币种
     * @param publishTime 外部发布时间
     * @return true 表示同一报价事实已存在
     */
    private boolean existsRawRate(String sourceCode, String baseCurrency, String quoteCurrency,
                                  LocalDateTime publishTime) {
        return rawRateMapper.selectCount(Wrappers.<ExchangeRawRateDO>lambdaQuery()
                .eq(ExchangeRawRateDO::getSourceCode, sourceCode)
                .eq(ExchangeRawRateDO::getBaseCurrency, baseCurrency)
                .eq(ExchangeRawRateDO::getQuoteCurrency, quoteCurrency)
                .eq(ExchangeRawRateDO::getPublishTime, publishTime)
                .eq(ExchangeRawRateDO::getDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 持久化本次拉取批次的状态、计数和安全错误说明。
     *
     * @param result 拉取结果
     * @param source 汇率源配置
     * @param startTime 拉取开始时刻
     * @param endTime 拉取结束时刻
     */
    private void insertFetchLog(ExchangeRateFetchResult result, ExchangeRateSourceDO source,
                                LocalDateTime startTime, LocalDateTime endTime) {
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
     * 更新汇率源最后一次拉取结果，供调度监控判断数据新鲜度。
     *
     * @param source 汇率源配置
     * @param fetchStatus 本批拉取状态
     * @param endTime 拉取结束时刻
     */
    private void updateSourceFetchStatus(ExchangeRateSourceDO source, String fetchStatus, LocalDateTime endTime) {
        source.setLastFetchTime(endTime);
        source.setLastFetchStatus(fetchStatus);
        source.setUpdateBy("system");
        source.setUpdateTime(endTime);
        sourceMapper.updateById(source);
    }

    /**
     * 判断外部报价是否至少包含一个有效正数价格字段。
     *
     * @param item 原始报价
     * @return true 表示可持久化并参与规则计算
     */
    private boolean hasAnyRate(RawRateItem item) {
        return positive(item.getCashBuyRate())
                || positive(item.getCashSellRate())
                || positive(item.getSpotBuyRate())
                || positive(item.getSpotSellRate())
                || positive(item.getMiddleRate());
    }

    /**
     * 判断报价是否为正数。
     *
     * @param value 报价值
     * @return true 表示报价有效
     */
    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 累加跳过计数并记录当前报价未处理原因。
     *
     * @param result 当前拉取批次统计
     * @param warning 跳过原因
     */
    private void skip(ExchangeRateFetchResult result, String warning) {
        result.setSkipCount(result.getSkipCount() + 1);
        result.getWarnings().add(warning);
    }

    /**
     * 按受控规则字段选择原始报价，未知字段不参与规则匹配。
     *
     * @param rawRate 原始报价
     * @param rateField 规则指定报价字段
     * @return 对应报价；字段未知时返回 {@code null}
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
     * 应用 BP 或百分比调整，并只在最终业务汇率边界按规则舍入。
     *
     * @param originalRate 原始汇率
     * @param rule 调整和舍入规则
     * @return 最终业务汇率
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
     * 将基点或百分比调整值转换为比例；空调整值按零处理。
     *
     * @param adjustMethod BP 或百分比口径
     * @param adjustValue 调整数值
     * @return 12 位中间精度调整比例
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
     * 将配置舍入编码转换为 JDK 舍入模式，历史未知值兼容 HALF_UP。
     *
     * @param roundingMode 舍入编码
     * @return JDK 舍入模式
     */
    private RoundingMode toRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ROUND_UP" -> RoundingMode.UP;
            case "ROUND_DOWN" -> RoundingMode.DOWN;
            default -> RoundingMode.HALF_UP;
        };
    }

    /**
     * 构造不含敏感信息的汇率计算审计说明。
     *
     * @param rule 实际使用规则
     * @param originalRate 原始汇率
     * @param finalRate 最终业务汇率
     * @return 可持久化的计算说明
     */
    private String buildAdjustDescription(ExchangeRateRuleDO rule, BigDecimal originalRate, BigDecimal finalRate) {
        BigDecimal adjustValue = rule.getAdjustValue() == null ? BigDecimal.ZERO : rule.getAdjustValue();
        return rule.getRateField() + " " + originalRate.toPlainString()
                + ", " + rule.getAdjustDirection() + " " + adjustValue.toPlainString()
                + " " + rule.getAdjustMethod() + ", scale " + rule.getDecimalScale()
                + ", " + rule.getRoundingMode() + ", final " + finalRate.toPlainString();
    }
}

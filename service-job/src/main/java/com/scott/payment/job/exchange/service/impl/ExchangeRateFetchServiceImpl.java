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
 * @description : ExchangeRateFetchServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ExchangeRateFetchServiceImpl implements ExchangeRateFetchService {

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
     * BOC 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String BOC = "BOC";
    /**
     * AUTO 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String AUTO = "AUTO";
    /**
     * RATE STATUS ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_ENABLED = "ENABLED";
    /**
     * RATE STATUS EXPIRED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RATE_STATUS_EXPIRED = "EXPIRED";
    /**
     * SUCCESS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SUCCESS = "SUCCESS";
    /**
     * FAILED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FAILED = "FAILED";
    /**
     * PARTIAL SUCCESS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    /**
     * ALL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String ALL = "ALL";
    /**
     * UP 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String UP = "UP";
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
     * source Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeJobRateSourceMapper sourceMapper;
    /**
     * raw Rate Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeJobRawRateMapper rawRateMapper;
    /**
     * rule Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeJobRateRuleMapper ruleMapper;
    /**
     * business Rate Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeJobBusinessRateMapper businessRateMapper;
    /**
     * fetch Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExchangeRateFetchLogMapper fetchLogMapper;
    /**
     * provider Registry 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

/**
 * 完成 process Item 分支的校验或状态更新。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param source source 输入值，含义由调用方法名称和所属业务对象限定
 * @param item item 输入值，含义由调用方法名称和所属业务对象限定
 * @param batchNo batch No 输入值，含义由调用方法名称和所属业务对象限定
 * @param dryRun dry Run 输入值，含义由调用方法名称和所属业务对象限定
 * @param result result 输入值，含义由调用方法名称和所属业务对象限定
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
 * 完成 backfill Missing Business Rates 分支的校验或状态更新。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param baseCurrency 币种代码，格式为 ISO 4217 三位大写字母
 * @param quoteCurrency 币种代码，格式为 ISO 4217 三位大写字母
 * @param publishTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param result result 输入值，含义由调用方法名称和所属业务对象限定
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
     * 完成 generate Business Rates 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
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
     * 查询 find Matched Rules 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param generateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 解析或查询得到的业务值
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
     * 完成 better Rule 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param left left 输入值，含义由调用方法名称和所属业务对象限定
     * @param right right 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 priority 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private int priority(ExchangeRateRuleDO rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    /**
     * 完成 specificity 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private int specificity(ExchangeRateRuleDO rule) {
        int score = 0;
        score += ALL.equals(rule.getSourceCode()) ? 0 : 1;
        score += ALL.equals(rule.getBaseCurrency()) ? 0 : 1;
        score += ALL.equals(rule.getQuoteCurrency()) ? 0 : 1;
        return score;
    }

    /**
     * 完成 generate Business Rate 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
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
                    existing.setUpdateBy("system");
                    existing.setUpdateTime(expireTime);
                    businessRateMapper.updateById(existing);
                });
    }

    /**
     * 判断 exists Business Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRateId raw Rate Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param ruleId rule Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
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
     * 完成 business Effective Time 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawRate raw Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @param generateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime businessEffectiveTime(ExchangeRawRateDO rawRate, ExchangeRateRuleDO rule, LocalDateTime generateTime) {
        LocalDateTime rawEffectiveTime = rawRate.getEffectiveTime() == null ? generateTime : rawRate.getEffectiveTime();
        if (rule.getEffectiveStartTime() != null && rawEffectiveTime.isBefore(rule.getEffectiveStartTime())) {
            return rule.getEffectiveStartTime();
        }
        return rawEffectiveTime;
    }

    /**
     * 查询 find Enabled Source 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 判断 exists Raw Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceCode source Code 输入值，含义由调用方法名称和所属业务对象限定
     * @param baseCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param quoteCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param publishTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 满足当前业务条件时返回 true，否则返回 false
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
     * 写入或更新 insert Fetch Log 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
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
     * 写入或更新 update Source Fetch Status 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param fetchStatus 状态编码，取值必须来自对应枚举或数据库受控字典
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
     * 判断 has Any Rate 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param item item 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasAnyRate(RawRateItem item) {
        return positive(item.getCashBuyRate())
                || positive(item.getCashSellRate())
                || positive(item.getSpotBuyRate())
                || positive(item.getSpotSellRate())
                || positive(item.getMiddleRate());
    }

    /**
     * 完成 positive 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 完成 skip 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @param warning warning 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void skip(ExchangeRateFetchResult result, String warning) {
        result.setSkipCount(result.getSkipCount() + 1);
        result.getWarnings().add(warning);
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
     * 计算 calculate Final Rate 对应的数值结果，调用方负责保证金额和币种上下文一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param originalRate original Rate 输入值，含义由调用方法名称和所属业务对象限定
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
}

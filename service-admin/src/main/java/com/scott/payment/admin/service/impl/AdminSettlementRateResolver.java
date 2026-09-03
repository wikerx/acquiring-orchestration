package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateSourceDO;
import com.scott.payment.admin.mapper.ExchangeBusinessRateMapper;
import com.scott.payment.admin.mapper.ExchangeRateSourceMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementRateResolver
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用试算结算汇率解析服务，只选择指定币种到 USD 的当前有效正向业务汇率，禁止取反向汇率倒数。
 * @status : create
 */
@Service
public class AdminSettlementRateResolver {

    /**
     * {@code USD}常量，统一 {@code AdminSettlementRateResolver} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String USD = "USD";
    /**
     * 结算汇率常量，统一 {@code AdminSettlementRateResolver} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SETTLEMENT_RATE = "SETTLEMENT_RATE";
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ENABLED = "ENABLED";

    private final ExchangeBusinessRateMapper businessRateMapper;
    private final ExchangeRateSourceMapper sourceMapper;

    /**
     * 创建结算汇率解析服务。
     *
     * @param businessRateMapper 业务汇率数据访问组件
     * @param sourceMapper 汇率来源配置数据访问组件
     */
    public AdminSettlementRateResolver(ExchangeBusinessRateMapper businessRateMapper,
                                       ExchangeRateSourceMapper sourceMapper) {
        this.businessRateMapper = businessRateMapper;
        this.sourceMapper = sourceMapper;
    }

    /**
     * 解析估值时间点标签币种到 USD 的直接结算汇率。
     *
     * <p>USD 使用系统恒等汇率 1；其他币种只查询 {@code baseCurrency -> USD}，不会查询
     * {@code USD -> baseCurrency}，也不会计算倒数。多来源同时有效时依次按默认来源、来源优先级、
     * 汇率生效时间和记录主键选择。</p>
     *
     * @param labelCurrency ISO 4217 三位标签币种，不允许为空
     * @param valuationTime 系统业务时间下的试算估值时间，不允许为空
     * @return 直接结算汇率及审计元数据
     * @throws ServiceException 币种非法或当前没有可用正向结算汇率时抛出
     */
    @DS(DataSourceName.MASTER)
    public ResolvedSettlementRate resolve(String labelCurrency, LocalDateTime valuationTime) {
        String baseCurrency = normalizeCurrency(labelCurrency);
        if (valuationTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "汇率估值时间不能为空");
        }
        if (USD.equals(baseCurrency)) {
            return new ResolvedSettlementRate(null, "SYSTEM_IDENTITY", BigDecimal.ONE,
                    valuationTime, valuationTime);
        }

        List<ExchangeBusinessRateDO> rates = businessRateMapper.selectList(
                Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                        .eq(ExchangeBusinessRateDO::getDeleted, 0L)
                        .eq(ExchangeBusinessRateDO::getRateType, SETTLEMENT_RATE)
                        .eq(ExchangeBusinessRateDO::getRateStatus, ENABLED)
                        .eq(ExchangeBusinessRateDO::getBaseCurrency, baseCurrency)
                        .eq(ExchangeBusinessRateDO::getQuoteCurrency, USD)
                        .le(ExchangeBusinessRateDO::getEffectiveTime, valuationTime)
                        .and(value -> value.isNull(ExchangeBusinessRateDO::getExpireTime)
                                .or().gt(ExchangeBusinessRateDO::getExpireTime, valuationTime)));
        if (rates.isEmpty()) {
            throw missingRate(baseCurrency);
        }

        Map<String, ExchangeRateSourceDO> sourceByCode = sourceMapper.selectList(
                        Wrappers.<ExchangeRateSourceDO>lambdaQuery()
                                .eq(ExchangeRateSourceDO::getSourceStatus, 1)
                                .eq(ExchangeRateSourceDO::getDeleted, 0L))
                .stream()
                .collect(Collectors.toMap(ExchangeRateSourceDO::getSourceCode,
                        Function.identity(), (left, right) -> left));

        ExchangeBusinessRateDO selected = rates.stream()
                .filter(rate -> rate.getFinalRate() != null && rate.getFinalRate().signum() > 0)
                .filter(rate -> sourceByCode.containsKey(rate.getSourceCode()))
                .min(candidateComparator(sourceByCode))
                .orElseThrow(() -> missingRate(baseCurrency));
        return new ResolvedSettlementRate(selected.getId(), selected.getSourceCode(), selected.getFinalRate(),
                selected.getEffectiveTime(), valuationTime);
    }

    private Comparator<ExchangeBusinessRateDO> candidateComparator(Map<String, ExchangeRateSourceDO> sourceByCode) {
        return Comparator
                .comparingInt((ExchangeBusinessRateDO rate) -> defaultRank(sourceByCode.get(rate.getSourceCode())))
                .thenComparingInt(rate -> priority(sourceByCode.get(rate.getSourceCode())))
                .thenComparing(ExchangeBusinessRateDO::getEffectiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExchangeBusinessRateDO::getId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int defaultRank(ExchangeRateSourceDO source) {
        return source != null && Integer.valueOf(1).equals(source.getDefaultSource()) ? 0 : 1;
    }

    private int priority(ExchangeRateSourceDO source) {
        return source == null || source.getPriority() == null ? Integer.MAX_VALUE : source.getPriority();
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency) || currency.trim().length() != 3) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "标签币种必须为三位 ISO 4217 代码");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private ServiceException missingRate(String baseCurrency) {
        return new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                "未配置当前有效的 " + baseCurrency + "->USD 正向结算汇率，禁止使用反向汇率倒数");
    }

    /**
     * 费用试算实际采用的结算汇率快照元数据。
     *
     * @param businessRateId 业务汇率记录 ID；USD 恒等汇率为空
     * @param sourceCode 汇率来源编码；USD 恒等汇率为 SYSTEM_IDENTITY
     * @param rate 一单位标签币种可兑换的 USD 数量，必须大于零
     * @param effectiveTime 汇率生效时间
     * @param valuationTime 本次试算估值时间
     */
    public record ResolvedSettlementRate(Long businessRateId,
                                         String sourceCode,
                                         BigDecimal rate,
                                         LocalDateTime effectiveTime,
                                         LocalDateTime valuationTime) {
    }
}

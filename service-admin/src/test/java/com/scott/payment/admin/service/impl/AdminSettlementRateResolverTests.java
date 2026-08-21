package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateSourceDO;
import com.scott.payment.admin.mapper.ExchangeBusinessRateMapper;
import com.scott.payment.admin.mapper.ExchangeRateSourceMapper;
import com.scott.payment.component.core.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementRateResolverTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用试算结算汇率解析测试，验证 USD 恒等汇率、正向有效汇率选择和缺失阻断。
 * @status : create
 */
class AdminSettlementRateResolverTests {

    private final ExchangeBusinessRateMapper businessRateMapper = mock(ExchangeBusinessRateMapper.class);
    private final ExchangeRateSourceMapper sourceMapper = mock(ExchangeRateSourceMapper.class);
    private final AdminSettlementRateResolver resolver = new AdminSettlementRateResolver(businessRateMapper, sourceMapper);

    /** 初始化 Lambda 查询字段元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, ExchangeBusinessRateDO.class);
        TableInfoHelper.initTableInfo(assistant, ExchangeRateSourceDO.class);
    }

    /** USD 标签币种直接使用恒等汇率，不访问汇率表。 */
    @Test
    void shouldUseIdentityRateForUsd() {
        LocalDateTime valuationTime = LocalDateTime.of(2026, 8, 18, 10, 0);

        AdminSettlementRateResolver.ResolvedSettlementRate resolved = resolver.resolve("usd", valuationTime);

        System.out.println("费用试算汇率：验证 USD 直接使用 1 且不读取业务汇率表");
        assertThat(resolved.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(resolved.businessRateId()).isNull();
        assertThat(resolved.sourceCode()).isEqualTo("SYSTEM_IDENTITY");
        verify(businessRateMapper, never()).selectList(any());
        verify(sourceMapper, never()).selectList(any());
    }

    /** 多个有效正向汇率同时存在时，优先选择默认来源，再按来源优先级和生效时间排序。 */
    @Test
    void shouldPreferDefaultEnabledDirectSettlementRateSource() {
        LocalDateTime valuationTime = LocalDateTime.of(2026, 8, 18, 10, 0);
        ExchangeBusinessRateDO defaultRate = rate(11L, "BOC", "EUR", "USD", "1.20",
                valuationTime.minusMinutes(10));
        ExchangeBusinessRateDO secondaryRate = rate(12L, "NBP", "EUR", "USD", "1.30",
                valuationTime.minusMinutes(1));
        when(businessRateMapper.selectList(any())).thenReturn(List.of(secondaryRate, defaultRate));
        when(sourceMapper.selectList(any())).thenReturn(List.of(
                source("BOC", 1, 5),
                source("NBP", 0, 1)
        ));

        AdminSettlementRateResolver.ResolvedSettlementRate resolved = resolver.resolve("EUR", valuationTime);

        System.out.println("费用试算汇率：验证只在 EUR->USD 正向有效候选中优先选择默认来源 BOC");
        assertThat(resolved.businessRateId()).isEqualTo(11L);
        assertThat(resolved.rate()).isEqualByComparingTo("1.20");
        assertThat(resolved.sourceCode()).isEqualTo("BOC");
    }

    /** 没有指定方向的有效结算汇率时必须失败，不能查询或计算反向汇率倒数。 */
    @Test
    void shouldRejectWhenDirectSettlementRateIsMissing() {
        when(businessRateMapper.selectList(any())).thenReturn(List.of());

        System.out.println("费用试算汇率：验证 EUR->USD 缺失时直接阻断，不使用 USD->EUR 倒数");
        assertThatThrownBy(() -> resolver.resolve("EUR", LocalDateTime.of(2026, 8, 18, 10, 0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("EUR->USD")
                .hasMessageContaining("正向结算汇率");
        verify(sourceMapper, never()).selectList(any());
    }

    private static ExchangeBusinessRateDO rate(Long id,
                                                String sourceCode,
                                                String baseCurrency,
                                                String quoteCurrency,
                                                String value,
                                                LocalDateTime effectiveTime) {
        ExchangeBusinessRateDO row = new ExchangeBusinessRateDO();
        row.setId(id);
        row.setRateType("SETTLEMENT_RATE");
        row.setSourceCode(sourceCode);
        row.setBaseCurrency(baseCurrency);
        row.setQuoteCurrency(quoteCurrency);
        row.setFinalRate(new BigDecimal(value));
        row.setEffectiveTime(effectiveTime);
        row.setRateStatus("ENABLED");
        row.setDeleted(0L);
        return row;
    }

    private static ExchangeRateSourceDO source(String sourceCode, int defaultSource, int priority) {
        ExchangeRateSourceDO row = new ExchangeRateSourceDO();
        row.setSourceCode(sourceCode);
        row.setDefaultSource(defaultSource);
        row.setPriority(priority);
        row.setSourceStatus(1);
        row.setDeleted(0L);
        return row;
    }
}

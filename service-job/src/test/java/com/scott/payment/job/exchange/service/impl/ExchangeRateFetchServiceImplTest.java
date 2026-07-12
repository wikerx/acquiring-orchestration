package com.scott.payment.job.exchange.service.impl;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeBusinessRateDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateRuleDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRawRateDO;
import com.scott.payment.job.exchange.provider.ExchangeRateProvider;
import com.scott.payment.job.exchange.provider.ExchangeRateProviderRegistry;
import com.scott.payment.job.mapper.ExchangeJobBusinessRateMapper;
import com.scott.payment.job.mapper.ExchangeJobRateRuleMapper;
import com.scott.payment.job.mapper.ExchangeJobRateSourceMapper;
import com.scott.payment.job.mapper.ExchangeJobRawRateMapper;
import com.scott.payment.job.mapper.ExchangeRateFetchLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchServiceImplTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Exchange Rate Fetch Service Impl Test，位于 service-job 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateFetchServiceImplTest {

    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Mock
    private ExchangeJobRateSourceMapper sourceMapper;
    /**
     * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @Mock
    private ExchangeJobRawRateMapper rawRateMapper;
    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Mock
    private ExchangeJobRateRuleMapper ruleMapper;
    /**
     * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @Mock
    private ExchangeJobBusinessRateMapper businessRateMapper;
    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Mock
    private ExchangeRateFetchLogMapper fetchLogMapper;

    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private ExchangeRateFetchServiceImpl service;

    @BeforeEach
    void setUp() {
        ExchangeRateProviderRegistry providerRegistry = new ExchangeRateProviderRegistry(List.of(new SingleUsdProvider()));
        service = new ExchangeRateFetchServiceImpl(
                sourceMapper,
                rawRateMapper,
                ruleMapper,
                businessRateMapper,
                fetchLogMapper,
                providerRegistry
        );
    }

    @Test
    void shouldGenerateBusinessRateWhenBocRawRateMatchesAllCurrencyRule() {
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(rawRateMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(transactionRule()));
        when(businessRateMapper.selectList(any())).thenReturn(List.of());

        ExchangeRateFetchResult result = service.fetch(new ExchangeRateFetchRequest(), null);

        assertThat(result.getFetchStatus()).isEqualTo("SUCCESS");
        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<ExchangeBusinessRateDO> captor = ArgumentCaptor.forClass(ExchangeBusinessRateDO.class);
        verify(businessRateMapper).insert(captor.capture());
        ExchangeBusinessRateDO businessRate = captor.getValue();
        assertThat(businessRate.getRateType()).isEqualTo("TRANSACTION_RATE");
        assertThat(businessRate.getSourceCode()).isEqualTo("BOC");
        assertThat(businessRate.getBaseCurrency()).isEqualTo("USD");
        assertThat(businessRate.getQuoteCurrency()).isEqualTo("CNY");
        assertThat(businessRate.getOriginalRate()).isEqualByComparingTo("7.186500000000");
        assertThat(businessRate.getFinalRate()).isEqualByComparingTo("7.19368650");
        assertThat(businessRate.getGenerateMethod()).isEqualTo("AUTO");
        assertThat(businessRate.getRateStatus()).isEqualTo("ENABLED");
    }

    @Test
    void shouldNotGenerateBusinessRateWhenDuplicatedRawRateAlreadyHasGeneratedRate() {
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(rawRateMapper.selectCount(any())).thenReturn(1L);
        when(rawRateMapper.selectOne(any())).thenReturn(rawRate());
        when(ruleMapper.selectList(any())).thenReturn(List.of(transactionRule()));
        when(businessRateMapper.selectCount(any())).thenReturn(1L);

        ExchangeRateFetchResult result = service.fetch(new ExchangeRateFetchRequest(), null);

        assertThat(result.getFetchStatus()).isEqualTo("SUCCESS");
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        verify(rawRateMapper, never()).insert(any(ExchangeRawRateDO.class));
        verify(businessRateMapper, never()).insert(any(ExchangeBusinessRateDO.class));
    }

    @Test
    void shouldBackfillBusinessRateWhenDuplicatedRawRateHasNoGeneratedRate() {
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(rawRateMapper.selectCount(any())).thenReturn(1L);
        when(rawRateMapper.selectOne(any())).thenReturn(rawRate());
        when(ruleMapper.selectList(any())).thenReturn(List.of(transactionRule()));
        when(businessRateMapper.selectCount(any())).thenReturn(0L);
        when(businessRateMapper.selectList(any())).thenReturn(List.of());

        ExchangeRateFetchResult result = service.fetch(new ExchangeRateFetchRequest(), null);

        assertThat(result.getFetchStatus()).isEqualTo("SUCCESS");
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        ArgumentCaptor<ExchangeBusinessRateDO> captor = ArgumentCaptor.forClass(ExchangeBusinessRateDO.class);
        verify(businessRateMapper).insert(captor.capture());
        assertThat(captor.getValue().getFinalRate()).isEqualByComparingTo("7.19368650");
    }

    @Test
    void shouldUseRuleStartTimeWhenRuleIsCreatedAfterRawRatePublishTime() {
        ExchangeRateRuleDO rule = transactionRule();
        rule.setEffectiveStartTime(LocalDateTime.of(2026, 7, 3, 10, 31));
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(rawRateMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(businessRateMapper.selectList(any())).thenReturn(List.of());

        service.fetch(new ExchangeRateFetchRequest(), null);

        ArgumentCaptor<ExchangeBusinessRateDO> captor = ArgumentCaptor.forClass(ExchangeBusinessRateDO.class);
        verify(businessRateMapper).insert(captor.capture());
        assertThat(captor.getValue().getEffectiveTime()).isEqualTo("2026-07-03T10:31:00");
    }


    private ExchangeRateSourceDO source() {
        ExchangeRateSourceDO source = new ExchangeRateSourceDO();
        source.setSourceCode("BOC");
        source.setSourceStatus(1);
        source.setRequestUrl("https://www.boc.cn/sourcedb/whpj/");
        source.setDeleted(0L);
        return source;
    }

    private ExchangeRateRuleDO transactionRule() {
        ExchangeRateRuleDO rule = new ExchangeRateRuleDO();
        rule.setId(10L);
        rule.setRateType("TRANSACTION_RATE");
        rule.setSourceCode("BOC");
        rule.setBaseCurrency("ALL");
        rule.setQuoteCurrency("ALL");
        rule.setRateField("SPOT_BUY_RATE");
        rule.setAdjustDirection("UP");
        rule.setAdjustMethod("BP");
        rule.setAdjustValue(new BigDecimal("10"));
        rule.setDecimalScale(8);
        rule.setRoundingMode("ROUND_HALF_UP");
        rule.setPriority(100);
        rule.setRuleStatus(1);
        rule.setDeleted(0L);
        return rule;
    }

    private ExchangeRawRateDO rawRate() {
        ExchangeRawRateDO rawRate = new ExchangeRawRateDO();
        rawRate.setId(100L);
        rawRate.setSourceCode("BOC");
        rawRate.setBaseCurrency("USD");
        rawRate.setQuoteCurrency("CNY");
        rawRate.setSpotBuyRate(new BigDecimal("7.186500000000"));
        rawRate.setPublishTime(LocalDateTime.of(2026, 7, 3, 10, 30));
        rawRate.setEffectiveTime(LocalDateTime.of(2026, 7, 3, 10, 30));
        rawRate.setRateStatus("ENABLED");
        rawRate.setDeleted(0L);
        return rawRate;
    }

    /**
     * 固定返回一条中行美元报价，避免测试访问外部网络。
     */
    private static class SingleUsdProvider implements ExchangeRateProvider {

        /**
         * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
         */
        @Override
        public String sourceCode() {
            return "BOC";
        }

        /**
         * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
         * @param source 请求参数或业务处理上下文，不能为空时由上层校验约束。
         * @return 处理后的业务结果或页面展示数据。
         */
        @Override
        public List<RawRateItem> fetch(ExchangeRateSourceDO source) {
            RawRateItem item = new RawRateItem();
            item.setSourceCurrencyName("美元");
            item.setBaseCurrency("USD");
            item.setQuoteCurrency("CNY");
            item.setSpotBuyRate(new BigDecimal("7.186500000000"));
            item.setPublishTime(LocalDateTime.of(2026, 7, 3, 10, 30));
            return List.of(item);
        }
    }
}

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

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchServiceImplTest
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Rate Fetch Service Impl Test 服务实现，位于 调度任务服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
class ExchangeRateFetchServiceImplTest {

    @Mock
    /**
     * source Mapper 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExchangeJobRateSourceMapper sourceMapper;
    @Mock
    /**
     * raw Rate Mapper 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExchangeJobRawRateMapper rawRateMapper;
    @Mock
    /**
     * rule Mapper 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExchangeJobRateRuleMapper ruleMapper;
    @Mock
    /**
     * business Rate Mapper 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExchangeJobBusinessRateMapper businessRateMapper;
    @Mock
    /**
     * fetch Log Mapper 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExchangeRateFetchLogMapper fetchLogMapper;

    /**
     * service 依赖，用于 Exchange Rate Fetch Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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

        @Override
        public String sourceCode() {
            return "BOC";
        }

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

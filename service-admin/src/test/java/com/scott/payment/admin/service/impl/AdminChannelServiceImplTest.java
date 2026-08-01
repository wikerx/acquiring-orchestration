package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingSaveRequest;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelLimitRuleDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMetadataSchemaDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelLimitRuleMapper;
import com.scott.payment.admin.mapper.ChannelMetadataSchemaMapper;
import com.scott.payment.admin.mapper.ChannelMidConfigMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelServiceImplTest
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : Admin Channel Service Impl Test 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
class AdminChannelServiceImplTest {

    @Mock
    /**
     * channel Info Mapper 依赖，用于 Admin Channel Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ChannelInfoMapper channelInfoMapper;
    /**
     * 渠道 MID 参数模板数据访问对象。
     */
    @Mock
    private ChannelMetadataSchemaMapper metadataSchemaMapper;
    @Mock
    /**
     * capability Mapper 依赖，用于 Admin Channel Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ChannelPaymentCapabilityMapper capabilityMapper;
    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @Mock
    private ChannelCapabilityCurrencyMapper capabilityCurrencyMapper;
    @Mock
    /**
     * capability Card Brand Mapper 依赖，用于 Admin Channel Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    @Mock
    /**
     * limit Rule Mapper，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：Spring 容器构造器注入。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private ChannelLimitRuleMapper limitRuleMapper;
    /**
     * 渠道真实 MID 配置数据访问对象，用于验证 MID 配置新增、更新和重复校验。
     */
    @Mock
    private ChannelMidConfigMapper midConfigMapper;
    /**
     * 商户与渠道 MID 绑定关系数据访问对象，用于验证绑定新增和重复校验。
     */
    @Mock
    private MerchantChannelMidBindingMapper midBindingMapper;
    @Mock
    /**
     * dict Data Mapper 依赖，用于 Admin Channel Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysDictDataMapper dictDataMapper;

    /**
     * service 依赖，用于 Admin Channel Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private AdminChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminChannelServiceImpl(
                channelInfoMapper,
                metadataSchemaMapper,
                capabilityMapper,
                capabilityCurrencyMapper,
                capabilityCardBrandMapper,
                limitRuleMapper,
                midConfigMapper,
                midBindingMapper,
                dictDataMapper,
                mock(ManagedCacheInvalidationCoordinator.class)
        );
    }

    @Test
    void shouldMapSupport3dsFieldsToDatabaseColumn() throws NoSuchFieldException {
        assertThat(ChannelInfoDO.class.getDeclaredField("support3ds")
                .getAnnotation(TableField.class)
                .value()).isEqualTo("support_3ds");
        assertThat(ChannelPaymentCapabilityDO.class.getDeclaredField("support3ds")
                .getAnnotation(TableField.class)
                .value()).isEqualTo("support_3ds");
        assertThat(ChannelPaymentCapabilityDO.class.getDeclaredField("supportIncrementalAuthorization")
                .getAnnotation(TableField.class)
                .value()).isEqualTo("support_incremental_authorization");
    }

    @Test
    void shouldRejectDuplicateChannelCode() {
        when(channelInfoMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createChannel(channelRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("渠道编码已存在");
    }

    @Test
    void shouldRejectChannelDefaultRequestUrlWithoutHttpScheme() {
        when(channelInfoMapper.selectCount(any())).thenReturn(0L);
        ChannelInfoSaveRequest request = channelRequest();
        request.setDefaultRequestUrl("ftp://api.channel.test");

        assertThatThrownBy(() -> service.createChannel(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("默认请求地址必须以 http:// 或 https:// 开头");
    }

    @Test
    void shouldCreateBankCardCapabilityWithCardBrands() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(capabilityMapper.selectCount(any())).thenReturn(0L);
        when(capabilityMapper.insert(any(ChannelPaymentCapabilityDO.class))).thenAnswer(invocation -> {
            ChannelPaymentCapabilityDO row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        });
        when(capabilityCurrencyMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(currency("USD")));
        when(capabilityCardBrandMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(cardBrand("VISA", 1), cardBrand("MASTERCARD", 2)));

        CapabilityResponse response = service.createCapability(bankCardCapabilityRequest(List.of("visa", "mastercard")));

        assertThat(response.getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(response.getTransactionType()).isEqualTo("PAYMENT,AUTHORIZATION");
        assertThat(response.getTransactionTypes()).containsExactly("PAYMENT", "AUTHORIZATION");
        assertThat(response.getSupportIncrementalAuthorization()).isEqualTo(1);
        assertThat(response.getCardBrands()).containsExactly("VISA", "MASTERCARD");
        assertThat(response.getCurrencyCodes()).containsExactly("USD");
        verify(capabilityMapper).insert(any(ChannelPaymentCapabilityDO.class));
        verify(capabilityCardBrandMapper, times(2)).insert(any(ChannelCapabilityCardBrandDO.class));
    }

    @Test
    void shouldRejectBankCardCapabilityWithoutCardBrands() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        CapabilitySaveRequest request = bankCardCapabilityRequest(List.of());

        assertThatThrownBy(() -> service.createCapability(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("银行卡支付能力必须绑定卡品牌");
    }

    @Test
    void shouldRejectCardBrandsForNonCardPaymentMethod() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        CapabilitySaveRequest request = bankCardCapabilityRequest(List.of("VISA"));
        request.setPaymentMethod("paypal");

        assertThatThrownBy(() -> service.createCapability(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非银行卡支付方式不能绑定卡品牌");
    }

    @Test
    void shouldRejectDuplicateCapabilityScope() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(capabilityMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCapability(bankCardCapabilityRequest(List.of("VISA"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一渠道、业务类型和支付方式不能重复");
    }

    @Test
    void shouldUpdateCapabilitySupportIncrementalAuthorization() {
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityCurrencyMapper.selectList(any())).thenReturn(List.of(currency("USD")));
        when(capabilityCardBrandMapper.selectList(any())).thenReturn(List.of(cardBrand("VISA", 1)));

        CapabilityResponse response = service.updateCapabilitySupport(101L, null, 1);

        assertThat(response.getSupportIncrementalAuthorization()).isEqualTo(1);
        verify(capabilityMapper).updateById(any(ChannelPaymentCapabilityDO.class));
    }

    @Test
    void shouldDefaultCapability3dsDisabledWhenChannelDoesNotSupport3ds() {
        when(channelInfoMapper.selectOne(any())).thenReturn(channelWithout3ds());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(capabilityMapper.selectCount(any())).thenReturn(0L);
        when(capabilityMapper.insert(any(ChannelPaymentCapabilityDO.class))).thenAnswer(invocation -> {
            ChannelPaymentCapabilityDO row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        });
        when(capabilityCurrencyMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(currency("USD")));
        when(capabilityCardBrandMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(cardBrand("VISA", 1)));

        CapabilityResponse response = service.createCapability(bankCardCapabilityRequest(List.of("visa")));

        assertThat(response.getSupport3ds()).isZero();
    }

    @Test
    void shouldRejectCapability3dsSwitchWhenChannelDoesNotSupport3ds() {
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(channelInfoMapper.selectOne(any())).thenReturn(channelWithout3ds());

        assertThatThrownBy(() -> service.updateCapabilitySupport(101L, 1, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("渠道未开启收单3DS能力");
    }

    @Test
    void shouldCreateLimitRuleWithoutTransactionType() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(0L);
        when(limitRuleMapper.insert(any(ChannelLimitRuleDO.class))).thenAnswer(invocation -> {
            ChannelLimitRuleDO row = invocation.getArgument(0);
            row.setId(301L);
            return 1;
        });

        LimitSaveRequest request = limitRequest();

        LimitResponse response = service.createLimit(request);

        ArgumentCaptor<ChannelLimitRuleDO> captor = ArgumentCaptor.forClass(ChannelLimitRuleDO.class);
        verify(limitRuleMapper).insert(captor.capture());
        assertThat(response.getLimitType()).isEqualTo("SINGLE_MAX");
        assertThat(captor.getValue().getLimitType()).isEqualTo("SINGLE_MAX");
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(captor.getValue().getCardBrand()).isEqualTo("VISA");
    }

    @Test
    void shouldRejectLimitWhenCardBrandIsNotEnabledByCapability() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(0L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createLimit(limitRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("该银行卡支付能力未绑定启用的卡品牌");
    }

    @Test
    void shouldRejectLimitWhenNonCardPaymentBindsCardBrand() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(paypalCapability());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        LimitSaveRequest request = limitRequest();
        request.setPaymentMethod("PAYPAL");
        request.setCardBrand("VISA");

        assertThatThrownBy(() -> service.createLimit(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非银行卡支付方式不能绑定卡品牌");
    }

    @Test
    void shouldRejectLimitAmountLessThanMinimum() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        LimitSaveRequest request = limitRequest();
        request.setLimitAmount(new BigDecimal("0.00"));

        assertThatThrownBy(() -> service.createLimit(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("限额金额必须大于等于0.01");
    }

    @Test
    void shouldRejectDuplicateLimitScopeRegardlessOfStatus() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createLimit(limitRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一渠道、业务类型、支付方式/卡品牌和限额类型不能重复");
    }

    @Test
    void shouldRejectWeeklyLimitGreaterThanSevenTimesDailyLimit() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(0L);
        when(limitRuleMapper.selectList(any())).thenReturn(List.of(existingLimit("DAILY", "100.00")));

        assertThatThrownBy(() -> service.createLimit(limitRequest("weekly", "701.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("周限额不能超过日限额的7倍");
    }

    @Test
    void shouldRejectMonthlyLimitGreaterThanFourTimesWeeklyLimit() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(0L);
        when(limitRuleMapper.selectList(any())).thenReturn(List.of(existingLimit("WEEKLY", "700.00")));

        assertThatThrownBy(() -> service.createLimit(limitRequest("monthly", "2800.01")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("月限额不能超过周限额的4倍");
    }

    @Test
    void shouldCreateLimitRulesInBatch() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(0L);
        when(limitRuleMapper.insert(any(ChannelLimitRuleDO.class))).thenAnswer(invocation -> {
            ChannelLimitRuleDO row = invocation.getArgument(0);
            row.setId(row.getLimitType().equals("SINGLE_MIN") ? 301L : 302L);
            return 1;
        });

        LimitBatchSaveRequest batch = new LimitBatchSaveRequest();
        batch.setItems(List.of(
                limitRequest("single_min", "1.00"),
                limitRequest("single_max", "100.00")
        ));

        List<LimitResponse> responses = service.createLimits(batch);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(LimitResponse::getLimitType)
                .containsExactly("SINGLE_MIN", "SINGLE_MAX");
        verify(limitRuleMapper, times(2)).insert(any(ChannelLimitRuleDO.class));
    }

    @Test
    void shouldSaveLimitDimensionByUpdatingExistingAndCreatingMissingRules() {
        ChannelLimitRuleDO existing = existingLimit("SINGLE_MIN", "1.00");
        when(limitRuleMapper.selectOne(any()))
                .thenReturn(existing)
                .thenReturn(existing)
                .thenReturn(null);
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(limitRuleMapper.selectCount(any())).thenReturn(0L);
        when(limitRuleMapper.insert(any(ChannelLimitRuleDO.class))).thenAnswer(invocation -> {
            ChannelLimitRuleDO row = invocation.getArgument(0);
            row.setId(302L);
            return 1;
        });

        LimitBatchSaveRequest batch = new LimitBatchSaveRequest();
        batch.setItems(List.of(
                limitRequest("single_min", "2.00"),
                limitRequest("single_max", "100.00")
        ));

        List<LimitResponse> responses = service.saveLimitDimension(batch);

        ArgumentCaptor<ChannelLimitRuleDO> updateCaptor = ArgumentCaptor.forClass(ChannelLimitRuleDO.class);
        verify(limitRuleMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(301L);
        assertThat(updateCaptor.getValue().getLimitAmount()).isEqualByComparingTo("2.00");
        verify(limitRuleMapper).insert(any(ChannelLimitRuleDO.class));
        assertThat(responses).hasSize(2);
    }

    @Test
    void shouldRejectInvalidLimitAmountRelationInBatch() {
        LimitBatchSaveRequest batch = new LimitBatchSaveRequest();
        batch.setItems(List.of(
                limitRequest("daily", "100.00"),
                limitRequest("weekly", "701.00")
        ));

        assertThatThrownBy(() -> service.createLimits(batch))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("周限额不能超过日限额的7倍");
    }

    /**
     * 新增渠道时如果未传超时时间，应落入统一默认秒数，避免渠道调用没有明确超时边界。
     */
    @Test
    void shouldDefaultChannelTimeoutSecondsWhenCreateChannel() {
        when(channelInfoMapper.selectCount(any())).thenReturn(0L);
        when(channelInfoMapper.insert(any(ChannelInfoDO.class))).thenAnswer(invocation -> {
            ChannelInfoDO row = invocation.getArgument(0);
            row.setId(1L);
            return 1;
        });

        service.createChannel(channelRequest());

        ArgumentCaptor<ChannelInfoDO> captor = ArgumentCaptor.forClass(ChannelInfoDO.class);
        verify(channelInfoMapper).insert(captor.capture());
        assertThat(captor.getValue().getConnectTimeoutSeconds()).isEqualTo(10);
        assertThat(captor.getValue().getReadTimeoutSeconds()).isEqualTo(30);
    }

    /**
     * 新增 MID 时应把范围字段归一化为大写，并按渠道模板保存真实 MID 元数据。
     */
    @Test
    void shouldCreateMidConfigWithNormalizedScopes() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(capabilityMapper.selectList(any())).thenReturn(List.of(enabledCapability()));
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(metadataSchemaMapper.selectList(any())).thenReturn(List.of());
        when(midConfigMapper.selectCount(any())).thenReturn(0L);
        when(midConfigMapper.insert(any(ChannelMidConfigDO.class))).thenAnswer(invocation -> {
            ChannelMidConfigDO row = invocation.getArgument(0);
            row.setId(501L);
            return 1;
        });

        ChannelMidConfigResponse response = service.createMid(midRequest());

        ArgumentCaptor<ChannelMidConfigDO> captor = ArgumentCaptor.forClass(ChannelMidConfigDO.class);
        verify(midConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getChannelCode()).isEqualTo("TEST_CHANNEL");
        assertThat(captor.getValue().getChannelMid()).isEqualTo("TESTDEVMER031");
        assertThat(captor.getValue().getBusinessType()).isEqualTo("ACQUIRING");
        assertThat(captor.getValue().getPaymentMethodScope()).isEqualTo("BANK_CARD");
        assertThat(captor.getValue().getCardBrandScope()).isEqualTo("VISA,MASTERCARD");
        assertThat(captor.getValue().getTransactionTypeScope()).isEqualTo("PAYMENT,AUTHORIZATION");
        assertThat(captor.getValue().getCurrencyScope()).isEqualTo("USD");
        assertThat(captor.getValue().getAllowedCountryScope()).isEqualTo("ALL");
        assertThat(captor.getValue().getSettlementCycle()).isEqualTo("T+1");
        assertThat(response.getChannelMid()).isEqualTo("TESTDEVMER031");
    }

    /**
     * MID 名称允许后台不录入，落库时使用渠道 MID 兜底以满足历史非空约束。
     */
    @Test
    void shouldFallbackMidNameToChannelMidWhenBlank() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        when(capabilityMapper.selectList(any())).thenReturn(List.of(enabledCapability()));
        when(capabilityMapper.selectOne(any())).thenReturn(enabledCapability());
        when(capabilityCardBrandMapper.selectCount(any())).thenReturn(1L);
        when(metadataSchemaMapper.selectList(any())).thenReturn(List.of());
        when(midConfigMapper.selectCount(any())).thenReturn(0L);
        ChannelMidConfigSaveRequest request = midRequest();
        request.setMidName(" ");

        service.createMid(request);

        ArgumentCaptor<ChannelMidConfigDO> captor = ArgumentCaptor.forClass(ChannelMidConfigDO.class);
        verify(midConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getMidName()).isEqualTo("TESTDEVMER031");
    }

    /**
     * MID 元数据展示应以渠道元数据模板为准：文本类型允许明文展示，敏感类型才脱敏。
     */
    @Test
    void shouldMaskMidMetadataBySchemaTypeInsteadOfFieldName() {
        ChannelMidConfigDO mid = enabledMid();
        mid.setMetadataValueJson("{\"merchantSecret\":\"plain-secret\",\"apiPassword\":\"real-password\"}");
        when(midConfigMapper.selectOne(any())).thenReturn(mid);
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(metadataSchemaMapper.selectList(any())).thenReturn(List.of(
                metadataSchema("merchantSecret", "商户秘钥", "TEXT", 0),
                metadataSchema("apiPassword", "API密码", "PASSWORD", 0)
        ));

        ChannelMidConfigResponse response = service.getMid(501L);

        assertThat(response.getMetadataValueJson()).contains("\"merchantSecret\":\"plain-secret\"");
        assertThat(response.getMetadataValueJson()).contains("\"apiPassword\":\"***\"");
    }

    /**
     * 新增商户 MID 绑定时只保存绑定关系，并从 MID 配置带出渠道冗余字段。
     */
    @Test
    void shouldCreateMerchantMidBindingFromMidConfig() {
        when(midConfigMapper.selectOne(any())).thenReturn(enabledMid());
        when(midBindingMapper.selectCount(any())).thenReturn(0L);
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(midBindingMapper.insert(any(MerchantChannelMidBindingDO.class))).thenAnswer(invocation -> {
            MerchantChannelMidBindingDO row = invocation.getArgument(0);
            row.setId(601L);
            return 1;
        });

        MerchantChannelMidBindingResponse response = service.createMidBinding(midBindingRequest());

        ArgumentCaptor<MerchantChannelMidBindingDO> captor = ArgumentCaptor.forClass(MerchantChannelMidBindingDO.class);
        verify(midBindingMapper).insert(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("200001");
        assertThat(captor.getValue().getChannelId()).isEqualTo(1L);
        assertThat(captor.getValue().getChannelCode()).isEqualTo("TEST_CHANNEL");
        assertThat(captor.getValue().getMidConfigId()).isEqualTo(501L);
        assertThat(captor.getValue().getChannelMid()).isEqualTo("TESTDEVMER031");
        assertThat(response.getMidName()).isEqualTo("MPGS TEST MID");
    }

    private CapabilitySaveRequest bankCardCapabilityRequest(List<String> cardBrands) {
        CapabilitySaveRequest request = new CapabilitySaveRequest();
        request.setChannelId(1L);
        request.setBusinessType("acquiring");
        request.setPaymentMethod("bank_card");
        request.setTransactionTypes(List.of("payment", "authorization"));
        request.setCurrencyCodes(List.of("usd"));
        request.setCardBrands(cardBrands);
        request.setSupport3ds(1);
        request.setSupportIncrementalAuthorization(1);
        request.setCapabilityStatus(1);
        request.setSortOrder(1);
        return request;
    }

    private ChannelInfoSaveRequest channelRequest() {
        ChannelInfoSaveRequest request = new ChannelInfoSaveRequest();
        request.setChannelCode("test_channel");
        request.setChannelCnName("测试渠道");
        request.setChannelEnName("Test Channel");
        request.setChannelStatus(1);
        request.setSupportAcquiring(1);
        request.setSupportPayout(1);
        request.setSupport3ds(1);
        return request;
    }

    private ChannelMidConfigSaveRequest midRequest() {
        ChannelMidConfigSaveRequest request = new ChannelMidConfigSaveRequest();
        request.setChannelId(1L);
        request.setChannelMid("");
        request.setMidName("MPGS TEST MID");
        request.setBusinessType("acquiring");
        request.setPaymentMethodScope("bank_card");
        request.setCardBrandScope("visa,mastercard");
        request.setTransactionTypeScope("authorization,capture");
        request.setCurrencyScope("usd");
        request.setAllowedCountryScope("all");
        request.setDefaultSettlementCurrency("usd");
        request.setSettlementCycle("t1");
        request.setSettlementTimeZone("Asia/Shanghai");
        request.setMetadataValueJson("{\"merchantId\":\"TESTDEVMER031\"}");
        request.setMidStatus(1);
        return request;
    }

    private MerchantChannelMidBindingSaveRequest midBindingRequest() {
        MerchantChannelMidBindingSaveRequest request = new MerchantChannelMidBindingSaveRequest();
        request.setMerchantId("200001");
        request.setMidConfigId(501L);
        request.setBindingStatus(1);
        return request;
    }

    private LimitSaveRequest limitRequest() {
        return limitRequest("single_max", "100.00");
    }

    private LimitSaveRequest limitRequest(String limitType, String amount) {
        LimitSaveRequest request = new LimitSaveRequest();
        request.setChannelId(1L);
        request.setBusinessType("acquiring");
        request.setPaymentMethod("bank_card");
        request.setCardBrand("visa");
        request.setLimitType(limitType);
        request.setLimitAmount(new BigDecimal(amount));
        request.setRuleStatus(1);
        return request;
    }

    private ChannelInfoDO enabledChannel() {
        ChannelInfoDO row = new ChannelInfoDO();
        row.setId(1L);
        row.setChannelCode("TEST_CHANNEL");
        row.setChannelCnName("测试渠道");
        row.setChannelEnName("Test Channel");
        row.setChannelStatus(1);
        row.setSupportAcquiring(1);
        row.setSupportPayout(1);
        row.setSupport3ds(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelMidConfigDO enabledMid() {
        ChannelMidConfigDO row = new ChannelMidConfigDO();
        row.setId(501L);
        row.setChannelId(1L);
        row.setChannelCode("TEST_CHANNEL");
        row.setChannelMid("TESTDEVMER031");
        row.setMidName("MPGS TEST MID");
        row.setBusinessType("ACQUIRING");
        row.setMidStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelMetadataSchemaDO metadataSchema(String fieldKey, String fieldLabel, String fieldType, int sensitiveFlag) {
        ChannelMetadataSchemaDO row = new ChannelMetadataSchemaDO();
        row.setChannelId(1L);
        row.setFieldKey(fieldKey);
        row.setFieldLabel(fieldLabel);
        row.setFieldType(fieldType);
        row.setRequiredFlag(0);
        row.setSensitiveFlag(sensitiveFlag);
        row.setFieldStatus(1);
        row.setSortOrder(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelInfoDO channelWithout3ds() {
        ChannelInfoDO row = enabledChannel();
        row.setSupport3ds(0);
        return row;
    }

    private ChannelCapabilityCurrencyDO currency(String code) {
        ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
        row.setCurrencyCode(code);
        return row;
    }

    private ChannelPaymentCapabilityDO enabledCapability() {
        ChannelPaymentCapabilityDO row = new ChannelPaymentCapabilityDO();
        row.setId(101L);
        row.setChannelId(1L);
        row.setChannelCode("TEST_CHANNEL");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethod("BANK_CARD");
        row.setTransactionType("PAYMENT,AUTHORIZATION");
        row.setSupport3ds(1);
        row.setSupportIncrementalAuthorization(0);
        row.setCapabilityStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelPaymentCapabilityDO paypalCapability() {
        ChannelPaymentCapabilityDO row = enabledCapability();
        row.setPaymentMethod("PAYPAL");
        return row;
    }

    private ChannelCapabilityCardBrandDO cardBrand(String brand, int sortOrder) {
        ChannelCapabilityCardBrandDO row = new ChannelCapabilityCardBrandDO();
        row.setCardBrand(brand);
        row.setSortOrder(sortOrder);
        return row;
    }

    private ChannelLimitRuleDO existingLimit(String limitType, String amount) {
        ChannelLimitRuleDO row = new ChannelLimitRuleDO();
        row.setId(301L);
        row.setChannelId(1L);
        row.setChannelCode("TEST_CHANNEL");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethod("BANK_CARD");
        row.setCardBrand("VISA");
        row.setLimitType(limitType);
        row.setLimitCurrency("USD");
        row.setLimitAmount(new BigDecimal(amount));
        row.setRuleStatus(1);
        row.setDeleted(0L);
        return row;
    }
}

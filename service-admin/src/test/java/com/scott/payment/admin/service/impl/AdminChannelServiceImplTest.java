package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelAccessConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.mapper.ChannelAccessConfigMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelLimitRuleMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 管理后台渠道服务测试，覆盖渠道能力卡品牌绑定和接入配置敏感字段脱敏规则。
 */
@ExtendWith(MockitoExtension.class)
class AdminChannelServiceImplTest {

    @Mock
    private ChannelInfoMapper channelInfoMapper;
    @Mock
    private ChannelPaymentCapabilityMapper capabilityMapper;
    @Mock
    private ChannelCapabilityCurrencyMapper capabilityCurrencyMapper;
    @Mock
    private ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    @Mock
    private ChannelLimitRuleMapper limitRuleMapper;
    @Mock
    private ChannelAccessConfigMapper accessConfigMapper;
    @Mock
    private SysDictDataMapper dictDataMapper;

    private AdminChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminChannelServiceImpl(
                channelInfoMapper,
                capabilityMapper,
                capabilityCurrencyMapper,
                capabilityCardBrandMapper,
                limitRuleMapper,
                accessConfigMapper,
                dictDataMapper
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
    void shouldReturnOnlyMaskedSensitiveValuesForAccessConfig() {
        when(channelInfoMapper.selectOne(any())).thenReturn(enabledChannel());
        when(accessConfigMapper.selectCount(any())).thenReturn(0L);
        when(accessConfigMapper.insert(any(ChannelAccessConfigDO.class))).thenAnswer(invocation -> {
            ChannelAccessConfigDO row = invocation.getArgument(0);
            row.setId(201L);
            return 1;
        });

        AccessResponse response = service.createAccessConfig(accessRequest());

        assertThat(response.getApiKeyMasked()).isEqualTo("key_********1234");
        assertThat(response.getApiSecretMasked()).isEqualTo("secr********alue");
        assertThat(response.getClientCertPasswordMasked()).isEqualTo("cert********word");
        assertThat(response).hasNoNullFieldsOrPropertiesExcept(
                "callbackUrl",
                "serverCertPath",
                "remark",
                "createTime",
                "updateTime"
        );
    }

    private CapabilitySaveRequest bankCardCapabilityRequest(List<String> cardBrands) {
        CapabilitySaveRequest request = new CapabilitySaveRequest();
        request.setChannelId(1L);
        request.setBusinessType("acquiring");
        request.setPaymentMethod("bank_card");
        request.setTransactionType("payment");
        request.setCurrencyCodes(List.of("usd"));
        request.setCardBrands(cardBrands);
        request.setSupport3ds(1);
        request.setSupportIncrementalAuthorization(0);
        request.setCapabilityStatus(1);
        request.setSortOrder(1);
        return request;
    }

    private AccessSaveRequest accessRequest() {
        AccessSaveRequest request = new AccessSaveRequest();
        request.setChannelId(1L);
        request.setEnvMode("test");
        request.setBaseUrl("https://api.channel.test");
        request.setInteractionMode("api_rest");
        request.setChannelMerchantNo("MID123");
        request.setApiKey("key_abcd1234");
        request.setApiSecret("secret-value");
        request.setClientCertPath("/certs/client.p12");
        request.setClientCertPassword("cert-password");
        request.setExtraConfigJson("{\"timeoutSeconds\":30}");
        request.setConfigStatus(1);
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

    private ChannelCapabilityCurrencyDO currency(String code) {
        ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
        row.setCurrencyCode(code);
        return row;
    }

    private ChannelCapabilityCardBrandDO cardBrand(String brand, int sortOrder) {
        ChannelCapabilityCardBrandDO row = new ChannelCapabilityCardBrandDO();
        row.setCardBrand(brand);
        row.setSortOrder(sortOrder);
        return row;
    }
}

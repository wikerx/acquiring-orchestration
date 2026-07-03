package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoServiceImplTest
 * @date : 2026-06-27 20:02
 * @email : scott_x@163.com
 * @description : 管理后台商户资料服务测试，覆盖商户新增和编辑表单基础选项组装规则
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class AdminMerchantInfoServiceImplTest {

    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;
    @Mock
    private BaseMerchantJwtKeyMapper jwtKeyMapper;
    @Mock
    private BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    @Mock
    private BaseMerchantResponseKeyMapper responseKeyMapper;
    @Mock
    private BaseMccLevel1Mapper mccLevel1Mapper;
    @Mock
    private BaseMccLevel2Mapper mccLevel2Mapper;
    @Mock
    private BaseMccCodeMapper mccCodeMapper;
    @Mock
    private IsoCountryMapper isoCountryMapper;
    @Mock
    private IsoCurrencyMapper isoCurrencyMapper;
    @Mock
    private OpenApiKeyMaterialFactory keyMaterialFactory;

    private AdminMerchantInfoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminMerchantInfoServiceImpl(
                merchantInfoMapper,
                jwtKeyMapper,
                platformPayloadKeyMapper,
                responseKeyMapper,
                mccLevel1Mapper,
                mccLevel2Mapper,
                mccCodeMapper,
                isoCountryMapper,
                isoCurrencyMapper,
                keyMaterialFactory
        );
    }

    @Test
    void shouldReturnMerchantFormOptionsFromBaseData() {
        when(mccLevel1Mapper.selectList(any())).thenReturn(List.of(level1()));
        when(mccLevel2Mapper.selectList(any())).thenReturn(List.of(level2()));
        when(mccCodeMapper.selectList(any())).thenReturn(List.of(mccCode()));
        when(isoCountryMapper.selectList(any())).thenReturn(List.of(country()));
        when(isoCurrencyMapper.selectList(any())).thenReturn(List.of(currency()));

        AdminMerchantFormOptionsDTO options = service.getFormOptions();

        assertThat(options.getMccOptions()).hasSize(1);
        AdminMerchantFormOptionsDTO.OptionNode level1 = options.getMccOptions().get(0);
        assertThat(level1.getValue()).isEqualTo("L1:1");
        assertThat(level1.getChildren()).hasSize(1);
        AdminMerchantFormOptionsDTO.OptionNode level2 = level1.getChildren().get(0);
        assertThat(level2.getValue()).isEqualTo("L2:11");
        assertThat(level2.getChildren()).singleElement()
                .satisfies(leaf -> {
                    assertThat(leaf.getValue()).isEqualTo("5411");
                    assertThat(leaf.getLabel()).contains("5411");
                    assertThat(leaf.getNameCn()).isEqualTo("食品杂货店");
                    assertThat(leaf.getNameEn()).isEqualTo("Grocery Stores");
                });
        assertThat(options.getCountries()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getValue()).isEqualTo("USA");
                    assertThat(item.getLabel()).contains("USA").contains("美国");
                    assertThat(item.getNameCn()).isEqualTo("美国");
                    assertThat(item.getNameEn()).isEqualTo("United States");
                });
        assertThat(options.getCurrencies()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getValue()).isEqualTo("USD");
                    assertThat(item.getLabel()).contains("USD").contains("美元").contains("2").contains("0.01");
                    assertThat(item.getNameCn()).isEqualTo("美元");
                    assertThat(item.getNameEn()).isEqualTo("US Dollar");
                    assertThat(item.getFractionDigits()).isEqualTo(2);
                    assertThat(item.getMinimumAmount()).isEqualByComparingTo("0.01");
                });
    }

    @Test
    void shouldGenerateMerchantIdWhenCreatingMerchant() {
        when(merchantInfoMapper.selectOne(any())).thenReturn(null);
        AdminMerchantSaveRequest request = new AdminMerchantSaveRequest();
        request.setMerchantId("MANUAL-ID");
        request.setMerchantName("Codex Test Merchant");
        request.setMerchantShortName("Codex");
        request.setMerchantCategoryCode("5411");
        request.setCountryCode("usa");
        request.setSettlementCurrency("usd");
        request.setTimezone("Asia/Shanghai");
        request.setMerchantStatus(1);
        request.setRiskLevel(2);
        request.setContactEmail("merchant@example.com");

        AdminMerchantInfoDTO result = service.createMerchant(request);

        assertThat(result.getMerchantId()).startsWith("M");
        assertThat(result.getMerchantId()).isNotEqualTo("MANUAL-ID");
        assertThat(result.getCountryCode()).isEqualTo("USA");
        assertThat(result.getSettlementCurrency()).isEqualTo("USD");
        verify(merchantInfoMapper).insert(argThat((BaseMerchantInfoDO row) ->
                row != null
                        && row.getMerchantId().equals(result.getMerchantId())
                        && "Codex".equals(row.getMerchantShortName())
                        && "merchant@example.com".equals(row.getContactEmail())
        ));
    }

    private MccEntities.BaseMccLevel1DO level1() {
        MccEntities.BaseMccLevel1DO row = new MccEntities.BaseMccLevel1DO();
        row.setId(1L);
        row.setLevel1Code("RETAIL");
        row.setNameCn("零售");
        row.setNameEn("Retail");
        row.setDeleted(0L);
        row.setStatus(1);
        return row;
    }

    private MccEntities.BaseMccLevel2DO level2() {
        MccEntities.BaseMccLevel2DO row = new MccEntities.BaseMccLevel2DO();
        row.setId(11L);
        row.setLevel1Id(1L);
        row.setLevel2Code("GROCERY");
        row.setNameCn("食品杂货");
        row.setNameEn("Grocery");
        row.setDeleted(0L);
        row.setStatus(1);
        return row;
    }

    private MccEntities.BaseMccCodeDO mccCode() {
        MccEntities.BaseMccCodeDO row = new MccEntities.BaseMccCodeDO();
        row.setId(111L);
        row.setLevel1Id(1L);
        row.setLevel2Id(11L);
        row.setMccCode("5411");
        row.setNameCn("食品杂货店");
        row.setNameEn("Grocery Stores");
        row.setDeleted(0L);
        row.setStatus(1);
        return row;
    }

    private IsoCountryDO country() {
        IsoCountryDO row = new IsoCountryDO();
        row.setId(1L);
        row.setAlpha3Code("USA");
        row.setChineseName("美国");
        row.setEnglishName("United States");
        row.setStatus(1);
        row.setDeleted(0);
        return row;
    }

    private IsoCurrencyDO currency() {
        IsoCurrencyDO row = new IsoCurrencyDO();
        row.setId(1L);
        row.setAlpha3Code("USD");
        row.setChineseName("美元");
        row.setEnglishName("US Dollar");
        row.setFractionDigits(2);
        row.setMinimumAmount(new BigDecimal("0.01"));
        row.setStatus(1);
        row.setDeleted(0);
        return row;
    }
}

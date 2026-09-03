package com.scott.payment.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.mcc.model.MccOptionSnapshot;
import com.scott.payment.component.db.mcc.service.MccOptionCacheReader;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
@Slf4j
class AdminMerchantInfoServiceImplTest {

    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @Mock
    private BaseMerchantJwtKeyMapper jwtKeyMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @Mock
    private BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
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
    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @Mock
    private IsoCurrencyMapper isoCurrencyMapper;

    /** 公共 MCC 三级选项缓存读取器。 */
    @Mock
    private MccOptionCacheReader mccOptionCacheReader;

    /** 公共 ISO 国家和币种缓存服务。 */
    @Mock
    private IsoDictionaryService isoDictionaryService;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @Mock
    private OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * 验证商户安全材料变更后是否登记事务型缓存失效意图的协调器替身。
     */
    @Mock
    private MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService;

    /** 密钥元数据永久缓存可靠失效协调器。 */
    @Mock
    private ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /** 商户资金账户开户及结算币种一致性服务。 */
    @Mock
    private AdminMerchantFundAccountProvisioningService fundAccountProvisioningService;

    /** 商户详情登录状态查询组件。 */
    @Mock
    private SysAccountMapper sysAccountMapper;

    /** 商户详情资金账户查询组件。 */
    @Mock
    private MerchantFundAccountMapper fundAccountMapper;

    /** 商户详情当前费率查询组件。 */
    @Mock
    private FeePlanMapper feePlanMapper;

    private AdminMerchantInfoServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
        TableInfoHelper.initTableInfo(assistant, BaseMerchantJwtKeyDO.class);
        TableInfoHelper.initTableInfo(assistant, BasePlatformPayloadKeyDO.class);
        TableInfoHelper.initTableInfo(assistant, BaseMerchantResponseKeyDO.class);
        service = new AdminMerchantInfoServiceImpl(
                merchantInfoMapper,
                jwtKeyMapper,
                platformPayloadKeyMapper,
                responseKeyMapper,
                mccOptionCacheReader,
                isoDictionaryService,
                keyMaterialFactory,
                merchantRuntimeProfileCacheService,
                cacheInvalidationCoordinator,
                mock(AdminMerchantPrimaryAccountProvisioningService.class),
                fundAccountProvisioningService,
                sysAccountMapper,
                fundAccountMapper,
                feePlanMapper,
                mock(com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService.class),
                mock(AdminMerchantSecurityNotificationService.class),
                mock(AdminMerchantStatusLifecycleService.class)
        );
    }

    @Test
    void shouldReturnMerchantFormOptionsFromBaseData() {
        when(mccOptionCacheReader.listOptions()).thenReturn(List.of(mccOptionTree()));
        when(isoDictionaryService.listCountries()).thenReturn(List.of(countryInfo()));
        when(isoDictionaryService.listCurrencies()).thenReturn(List.of(currencyInfo()));

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
        log.info("测试管理端新增商户缓存一致性，关键输入: 系统生成商户号");
        AdminMerchantSaveRequest request = validRequest("MANUAL-ID");

        AdminMerchantInfoDTO result = service.createMerchant(request);

        assertThat(result.getMerchantId()).startsWith("M");
        assertThat(result.getMerchantId()).isNotEqualTo("MANUAL-ID");
        assertThat(result.getCountryCode()).isEqualTo("USA");
        assertThat(result.getSettlementCurrency()).isEqualTo("USD");
        verify(merchantInfoMapper).insert(argThat((BaseMerchantInfoDO row) ->
                row != null
                        && row.getMerchantId().equals(result.getMerchantId())
                        && "CODEX TEST".equals(row.getBillingDescriptor())
                        && "Codex".equals(row.getMerchantShortName())
                        && "merchant@example.com".equals(row.getContactEmail())
        ));
        verify(merchantRuntimeProfileCacheService).putRuntimeProfile(argThat(profile ->
                profile != null
                        && result.getMerchantId().equals(profile.getMerchantId())
                        && "Codex Test Merchant".equals(profile.getMerchantName())
                        && "merchant@example.com".equals(profile.getContactEmail())
        ));
        InOrder order = inOrder(cacheInvalidationCoordinator, merchantInfoMapper,
                merchantRuntimeProfileCacheService);
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                result.getMerchantId());
        order.verify(merchantInfoMapper).insert(any(BaseMerchantInfoDO.class));
        order.verify(merchantRuntimeProfileCacheService).putRuntimeProfile(any());
        log.info("管理端新增商户缓存一致性完成，结果: 先登记可靠失效再写库与提交新缓存");
    }

    /** 管理端编辑商户应在主库写入前登记永久资料缓存失效。 */
    @Test
    void shouldPrepareInvalidationBeforeUpdatingMerchantProfile() {
        log.info("测试管理端编辑商户缓存一致性，关键输入: merchantId=200045");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());

        service.updateMerchant(1L, validRequest("200045"));

        InOrder order = inOrder(cacheInvalidationCoordinator, merchantInfoMapper,
                merchantRuntimeProfileCacheService);
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045");
        order.verify(merchantInfoMapper).updateById(any(BaseMerchantInfoDO.class));
        order.verify(merchantRuntimeProfileCacheService).putRuntimeProfile(argThat(profile ->
                profile != null && "200045".equals(profile.getMerchantId())));
        log.info("管理端编辑商户缓存一致性完成，结果: 失效意图早于主表更新");
    }

    /** 商户结算币种变化时，应在商户资料落库前同步资金账户币种。 */
    @Test
    void shouldSynchronizeFundAccountBeforeUpdatingSettlementCurrency() {
        log.info("测试商户结算币种同步，关键输入: merchantId=200045, USD->EUR");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        AdminMerchantSaveRequest request = validRequest("200045");
        request.setSettlementCurrency("eur");

        service.updateMerchant(1L, request);

        InOrder order = inOrder(fundAccountProvisioningService, merchantInfoMapper);
        order.verify(fundAccountProvisioningService)
                .synchronizeSettlementCurrency("200045", "eur");
        order.verify(merchantInfoMapper).updateById(argThat((BaseMerchantInfoDO row) ->
                row != null && "EUR".equals(row.getSettlementCurrency())));
        log.info("商户结算币种同步完成，结果: 资金账户先同步，商户资料后落库");
    }

    /** 管理端停用商户应先阻止交易服务继续命中旧状态。 */
    @Test
    void shouldPrepareInvalidationBeforeUpdatingMerchantStatus() {
        log.info("测试管理端商户状态缓存一致性，关键输入: status=2(冻结)");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());

        service.updateStatus(1L, 2);

        InOrder order = inOrder(cacheInvalidationCoordinator, merchantInfoMapper,
                merchantRuntimeProfileCacheService);
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045");
        order.verify(merchantInfoMapper).updateById(
                org.mockito.ArgumentMatchers.<BaseMerchantInfoDO>argThat(row ->
                row != null && Integer.valueOf(2).equals(row.getMerchantStatus())));
        order.verify(merchantRuntimeProfileCacheService).putRuntimeProfile(argThat(profile ->
                profile != null && Integer.valueOf(2).equals(profile.getMerchantStatus())));
        log.info("管理端商户状态缓存一致性完成，结果: 旧启用状态受门禁保护");
    }

    @Test
    void shouldRejectRepeatedOrClosedMerchantStatusTransitions() {
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());

        assertThatThrownBy(() -> service.updateStatus(1L, 1))
                .hasMessageContaining("状态");

        BaseMerchantInfoDO closed = existingMerchant();
        closed.setMerchantStatus(3);
        when(merchantInfoMapper.selectOne(any())).thenReturn(closed);
        assertThatThrownBy(() -> service.updateStatus(1L, 2))
                .hasMessageContaining("状态");
    }

    /** 删除商户应同时登记全部商户维度永久缓存失效。 */
    @Test
    void shouldPrepareAllPersistentCacheInvalidationsBeforeDeletingMerchant() {
        log.info("测试管理端删除商户缓存一致性，关键输入: merchantId=200045");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());

        service.deleteMerchant(1L);

        InOrder order = inOrder(cacheInvalidationCoordinator, merchantInfoMapper,
                merchantRuntimeProfileCacheService);
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045");
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_KEY_METADATA,
                "200045");
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_ROUTE,
                "200045");
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045");
        order.verify(cacheInvalidationCoordinator).prepare(
                com.scott.payment.component.core.cache.PaymentCacheNames.MERCHANT_ACTIVE_FEE,
                "200045");
        order.verify(merchantInfoMapper).update(eq(null), any());
        order.verify(merchantRuntimeProfileCacheService).evictRuntimeProfile("200045");
        log.info("管理端删除商户缓存一致性完成，结果: 五类商户维度永久缓存均已登记可靠失效");
    }

    @Test
    void shouldSerializeMerchantPrimaryKeyAsString() throws Exception {
        AdminMerchantInfoDTO dto = new AdminMerchantInfoDTO();
        dto.setId(2076595876878270466L);

        JsonNode jsonNode = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));

        assertThat(jsonNode.get("id").isTextual()).isTrue();
        assertThat(jsonNode.get("id").asText()).isEqualTo("2076595876878270466");
    }

    /** 历史商户没有登录账号、资金账户或费率时，详情必须明确返回未初始化状态。 */
    @Test
    void shouldExposeUninitializedOperationalFoundationForHistoricalMerchant() {
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        when(sysAccountMapper.selectCount(any())).thenReturn(0L);

        AdminMerchantInfoDTO result = service.getMerchant(1L);

        assertThat(result.getLoginInitialized()).isFalse();
        assertThat(result.getFundAccountNo()).isNull();
        assertThat(result.getFundAccountStatus()).isNull();
        assertThat(result.getCurrentFeeVersionNo()).isNull();
    }

    /** 已初始化商户详情应返回当前单结算币种账户和已生效费率版本。 */
    @Test
    void shouldExposeFundAccountAndCurrentFeeVersionInMerchantDetail() {
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        when(sysAccountMapper.selectCount(any())).thenReturn(1L);
        MerchantFundAccountDO fundAccount = new MerchantFundAccountDO();
        fundAccount.setAccountNo("FA200045USD");
        fundAccount.setAccountStatus("NORMAL");
        when(fundAccountMapper.selectOne(any())).thenReturn(fundAccount);
        FeePlanDO feePlan = new FeePlanDO();
        feePlan.setCurrentVersionNo(3);
        when(feePlanMapper.selectOne(any())).thenReturn(feePlan);

        AdminMerchantInfoDTO result = service.getMerchant(1L);

        assertThat(result.getLoginInitialized()).isTrue();
        assertThat(result.getFundAccountNo()).isEqualTo("FA200045USD");
        assertThat(result.getFundAccountStatus()).isEqualTo("NORMAL");
        assertThat(result.getCurrentFeeVersionNo()).isEqualTo(3);
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

    /** 构造公共 MCC 缓存返回的三级选项树。 */
    private MccOptionSnapshot mccOptionTree() {
        MccOptionSnapshot leaf = new MccOptionSnapshot();
        leaf.setValue("5411");
        leaf.setLabel("5411 — 食品杂货店 / Grocery Stores");
        leaf.setNameCn("食品杂货店");
        leaf.setNameEn("Grocery Stores");
        MccOptionSnapshot level2 = new MccOptionSnapshot();
        level2.setValue("L2:11");
        level2.setLabel("GROCERY — 食品杂货 / Grocery");
        level2.setNameCn("食品杂货");
        level2.setNameEn("Grocery");
        level2.setChildren(List.of(leaf));
        MccOptionSnapshot level1 = new MccOptionSnapshot();
        level1.setValue("L1:1");
        level1.setLabel("RETAIL — 零售 / Retail");
        level1.setNameCn("零售");
        level1.setNameEn("Retail");
        level1.setChildren(List.of(level2));
        return level1;
    }

    /** 构造公共 ISO 国家缓存结果。 */
    private IsoCountryInfo countryInfo() {
        return new IsoCountryInfo(
                "US", "USA", "840", "United States", "United States", "美国",
                "NA", "北美洲", null, "en", "English", "英语", "USD");
    }

    /** 构造公共 ISO 币种缓存结果。 */
    private IsoCurrencyInfo currencyInfo() {
        return new IsoCurrencyInfo(
                "USD", "840", "US Dollar", "美元", 2, 100L,
                new BigDecimal("0.01"), "$");
    }

    /** 构造管理端新增或编辑商户的合法请求。 */
    private AdminMerchantSaveRequest validRequest(String merchantId) {
        AdminMerchantSaveRequest request = new AdminMerchantSaveRequest();
        request.setMerchantId(merchantId);
        request.setMerchantName("Codex Test Merchant");
        request.setBillingDescriptor("CODEX TEST");
        request.setMerchantShortName("Codex");
        request.setMerchantCategoryCode("5411");
        request.setCountryCode("usa");
        request.setSettlementCurrency("usd");
        request.setTimezone("Asia/Shanghai");
        request.setMerchantStatus(1);
        request.setRiskLevel(2);
        request.setContactEmail("merchant@example.com");
        return request;
    }

    /** 构造管理端写操作定位的未删除商户记录。 */
    private BaseMerchantInfoDO existingMerchant() {
        BaseMerchantInfoDO row = new BaseMerchantInfoDO();
        row.setId(1L);
        row.setMerchantId("200045");
        row.setMerchantName("Existing Merchant");
        row.setBillingDescriptor("EXISTING");
        row.setMerchantShortName("Existing");
        row.setMerchantStatus(1);
        row.setMerchantCategoryCode("5411");
        row.setCountryCode("USA");
        row.setSettlementCurrency("USD");
        row.setTimezone("Asia/Shanghai");
        row.setRiskLevel(2);
        row.setContactEmail("merchant@example.com");
        row.setDeleted(0);
        return row;
    }
}

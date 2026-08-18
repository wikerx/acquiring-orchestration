package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileServiceImplTests
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户主体资料共享缓存读取与 MASTER 更新后完整缓存刷新行为测试
 * @status : create
 */
@Slf4j
class MerchantProfileServiceImplTests {

    /** 初始化 MyBatis-Plus Lambda 字段元数据，使纯单元测试可构造类型安全查询条件。 */
    @BeforeEach
    void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
    }

    /**
     * 查询命中共享 merchant:info 后应直接返回完整资料，不再访问数据库。
     */
    @Test
    void shouldReadCompleteProfileFromSharedCacheWithoutDatabaseQuery() {
        log.info("测试商户主体资料共享读取，关键输入: 商户号 200045");
        BaseMerchantInfoMapper mapper = mock(BaseMerchantInfoMapper.class);
        MerchantRuntimeProfileCacheService cacheService =
                mock(MerchantRuntimeProfileCacheService.class);
        ManagedCacheInvalidationCoordinator coordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        when(cacheService.findRuntimeProfile("200045")).thenReturn(runtimeProfile());

        MerchantProfileResponse result = service(mapper, cacheService, coordinator)
                .getProfile("200045");

        assertThat(result.getMerchantName()).isEqualTo("Codex Store");
        assertThat(result.getContactEmail()).isEqualTo("ops@example.com");
        assertThat(result.getAddressLine()).isEqualTo("1 Market Street");
        verify(cacheService).findRuntimeProfile("200045");
        verifyNoInteractions(mapper);
        log.info("商户主体资料共享读取完成，结果: 完整资料由缓存返回且数据库无访问");
    }

    /**
     * 商户自助更新必须写主库，并把更新后的完整资料提交给共享 merchant:info 缓存。
     */
    @Test
    void shouldUpdateAllowedFieldsAndPutCompleteSharedProfile() {
        log.info("测试商户主体资料自助更新，关键输入: 只修改允许字段");
        BaseMerchantInfoMapper mapper = mock(BaseMerchantInfoMapper.class);
        MerchantRuntimeProfileCacheService cacheService =
                mock(MerchantRuntimeProfileCacheService.class);
        ManagedCacheInvalidationCoordinator coordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        when(mapper.selectOne(any())).thenReturn(sensitiveFields());
        when(mapper.update(eq(null), any())).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(fullRow());
        MerchantProfileUpdateRequest request = new MerchantProfileUpdateRequest();
        request.setBillingDescriptor("CODEX ONLINE");
        request.setMerchantShortName("Codex");
        request.setRegionCode("CA");
        request.setCity("San Francisco");
        request.setAddressLine("2 Market Street");
        request.setPostalCode("94105");
        request.setContactName("Operations");
        request.setContactEmail("ops@example.com");
        request.setContactPhone("+1-555-0100");
        request.setTimezone("America/Los_Angeles");

        MerchantProfileResponse result = service(mapper, cacheService, coordinator)
                .updateProfile("200045", request);

        assertThat(result.getMerchantId()).isEqualTo("200045");
        verify(cacheService).putRuntimeProfile(org.mockito.ArgumentMatchers.argThat(profile ->
                profile != null
                        && "200045".equals(profile.getMerchantId())
                        && "ops@example.com".equals(profile.getContactEmail())
                        && "1 Market Street".equals(profile.getAddressLine())
        ));
        verify(mapper).update(eq(null), any());
        org.mockito.InOrder order = inOrder(coordinator, mapper, cacheService);
        order.verify(coordinator).prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, "200045");
        order.verify(mapper).update(eq(null), any());
        order.verify(cacheService).putRuntimeProfile(any());
        log.info("商户主体资料自助更新完成，结果: 主库更新后提交完整共享缓存资料");
    }

    /** 创建服务测试实例。 */
    private MerchantProfileServiceImpl service(BaseMerchantInfoMapper mapper,
                                               MerchantRuntimeProfileCacheService cacheService,
                                               ManagedCacheInvalidationCoordinator coordinator) {
        return new MerchantProfileServiceImpl(mapper, cacheService, coordinator);
    }

    /** 构造共享缓存中的非敏感商户资料。 */
    private MerchantRuntimeProfile runtimeProfile() {
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setId(1L);
        profile.setMerchantId("200045");
        profile.setMerchantName("Codex Store");
        profile.setBillingDescriptor("CODEX STORE");
        profile.setMerchantShortName("Codex");
        profile.setMerchantStatus(1);
        profile.setMerchantCategoryCode("5311");
        profile.setCountryCode("USA");
        profile.setRegionCode("CA");
        profile.setCity("San Francisco");
        profile.setAddressLine("1 Market Street");
        profile.setPostalCode("94105");
        profile.setContactName("Operations");
        profile.setContactEmail("ops@example.com");
        profile.setContactPhone("+1-555-0100");
        profile.setSettlementCurrency("USD");
        profile.setTimezone("America/Los_Angeles");
        profile.setRiskLevel(2);
        profile.setGmtCreate(LocalDateTime.of(2026, 7, 1, 9, 0));
        profile.setGmtModified(LocalDateTime.of(2026, 8, 1, 12, 0));
        return profile;
    }

    /** 构造写事务更新前读取的完整商户主库记录。 */
    private BaseMerchantInfoDO sensitiveFields() {
        BaseMerchantInfoDO row = new BaseMerchantInfoDO();
        row.setId(1L);
        row.setMerchantId("200045");
        row.setAddressLine("1 Market Street");
        row.setContactName("Operations");
        row.setContactEmail("ops@example.com");
        row.setContactPhone("+1-555-0100");
        row.setGmtCreate(LocalDateTime.of(2026, 7, 1, 9, 0));
        return row;
    }

    /** 构造更新后从主库读取的完整商户资料。 */
    private BaseMerchantInfoDO fullRow() {
        BaseMerchantInfoDO row = sensitiveFields();
        MerchantRuntimeProfile profile = runtimeProfile();
        row.setMerchantName(profile.getMerchantName());
        row.setBillingDescriptor("CODEX ONLINE");
        row.setMerchantShortName(profile.getMerchantShortName());
        row.setMerchantStatus(profile.getMerchantStatus());
        row.setMerchantCategoryCode(profile.getMerchantCategoryCode());
        row.setCountryCode(profile.getCountryCode());
        row.setRegionCode(profile.getRegionCode());
        row.setCity(profile.getCity());
        row.setPostalCode(profile.getPostalCode());
        row.setSettlementCurrency(profile.getSettlementCurrency());
        row.setTimezone(profile.getTimezone());
        row.setRiskLevel(profile.getRiskLevel());
        row.setGmtModified(LocalDateTime.of(2026, 8, 1, 12, 5));
        return row;
    }
}

package com.scott.payment.settlement.application;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileCreateRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileVersionCreateRequest;
import com.scott.payment.settlement.entity.MerchantSettlementAccountDO;
import com.scott.payment.settlement.entity.MerchantSettlementProfileCommandAuditDO;
import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证结算档案创建、版本切换、持久化幂等和最终开通 readiness。
 */
class SettlementProfileApplicationServiceTest {

    @Test
    void createShouldResolveIsoExponentAndPersistActiveProfileWithAudit() {
        MerchantSettlementProfileMapper mapper = mock(MerchantSettlementProfileMapper.class);
        when(mapper.insertCommandAuditIfAbsent(any())).thenReturn(1);
        when(mapper.selectAccountForUpdate(9L)).thenReturn(account());
        when(mapper.selectCurrencyExponent("USD")).thenReturn(2);
        when(mapper.insertProfile(any())).thenReturn(1);
        SettlementProfileApplicationService service = new SettlementProfileApplicationService(mapper);

        var response = service.create(createRequest());

        assertThat(response.getSettlementProfileNo()).startsWith("MSP-");
        assertThat(response.getMerchantId()).isEqualTo("M1001");
        assertThat(response.getTargetCurrency()).isEqualTo("USD");
        assertThat(response.getTargetCurrencyExponent()).isEqualTo(2);
        assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
        assertThat(response.getReused()).isFalse();
        ArgumentCaptor<MerchantSettlementProfileDO> profile =
                ArgumentCaptor.forClass(MerchantSettlementProfileDO.class);
        verify(mapper).insertProfile(profile.capture());
        assertThat(profile.getValue().getActiveSlot()).isEqualTo(1);
        assertThat(profile.getValue().getVersion()).isZero();
        ArgumentCaptor<MerchantSettlementProfileCommandAuditDO> audit =
                ArgumentCaptor.forClass(MerchantSettlementProfileCommandAuditDO.class);
        verify(mapper).insertCommandAuditIfAbsent(audit.capture());
        assertThat(audit.getValue().getRequestKey()).isEqualTo("REQ-PROFILE-1");
        assertThat(audit.getValue().getRequestFingerprint()).hasSize(64);
        assertThat(audit.getValue().getReason()).isEqualTo("initial settlement setup");
    }

    @Test
    void createVersionShouldRetireOldProfileAndPreserveAccountAndCurrency() {
        MerchantSettlementProfileMapper mapper = mock(MerchantSettlementProfileMapper.class);
        MerchantSettlementProfileDO source = activeProfile("MSP-OLD", 3L);
        when(mapper.selectByProfileNoForUpdate("MSP-OLD")).thenReturn(source);
        when(mapper.insertCommandAuditIfAbsent(any())).thenReturn(1);
        when(mapper.selectAccountForUpdate(9L)).thenReturn(account());
        when(mapper.selectCurrencyExponent("USD")).thenReturn(2);
        when(mapper.retireActiveProfile(anyString(), any(), any(), any())).thenReturn(1);
        when(mapper.insertProfile(any())).thenReturn(1);
        SettlementProfileApplicationService service = new SettlementProfileApplicationService(mapper);

        ProfileVersionCreateRequest request = versionRequest();
        var response = service.createVersion("MSP-OLD", request);

        assertThat(response.getSettlementProfileNo()).isNotEqualTo("MSP-OLD");
        assertThat(response.getSettlementAccountId()).isEqualTo(9L);
        assertThat(response.getTargetCurrency()).isEqualTo("USD");
        assertThat(response.getVersion()).isEqualTo(4L);
        verify(mapper).retireActiveProfile(
                org.mockito.ArgumentMatchers.eq("MSP-OLD"),
                org.mockito.ArgumentMatchers.eq(3L), any(), any());
        ArgumentCaptor<MerchantSettlementProfileDO> inserted =
                ArgumentCaptor.forClass(MerchantSettlementProfileDO.class);
        verify(mapper).insertProfile(inserted.capture());
        assertThat(inserted.getValue().getSettlementAccountId()).isEqualTo(source.getSettlementAccountId());
        assertThat(inserted.getValue().getTargetCurrencyExponent()).isEqualTo(source.getTargetCurrencyExponent());
        assertThat(inserted.getValue().getBusinessTimeZone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void duplicateCreateShouldReturnPersistedResultAndMarkResponseReused() {
        MerchantSettlementProfileMapper mapper = mock(MerchantSettlementProfileMapper.class);
        AtomicReference<MerchantSettlementProfileCommandAuditDO> attempted = new AtomicReference<>();
        when(mapper.insertCommandAuditIfAbsent(any())).thenAnswer(invocation -> {
            attempted.set(invocation.getArgument(0));
            return 0;
        });
        when(mapper.selectCommandAuditForUpdate("REQ-PROFILE-1")).thenAnswer(invocation -> attempted.get());
        when(mapper.selectByProfileNo(anyString())).thenReturn(activeProfile("MSP-REPLAY", 0L));
        SettlementProfileApplicationService service = new SettlementProfileApplicationService(mapper);

        var response = service.create(createRequest());

        assertThat(response.getSettlementProfileNo()).isEqualTo("MSP-REPLAY");
        assertThat(response.getReused()).isTrue();
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertProfile(any());
    }

    @Test
    void readinessShouldRejectProfileWhenAccountCurrencyDiffers() {
        MerchantSettlementProfileMapper mapper = mock(MerchantSettlementProfileMapper.class);
        when(mapper.selectActiveByMerchant("M1001")).thenReturn(activeProfile("MSP-ACTIVE", 0L));
        MerchantSettlementAccountDO account = account();
        account.setSettlementCurrency("EUR");
        when(mapper.selectAccount(9L)).thenReturn(account);
        when(mapper.selectCurrencyExponent("USD")).thenReturn(2);
        SettlementProfileApplicationService service = new SettlementProfileApplicationService(mapper);

        var readiness = service.readiness("M1001");

        assertThat(readiness.getReady()).isFalse();
        assertThat(readiness.getIssues()).containsExactly("SETTLEMENT_ACCOUNT_CURRENCY_MISMATCH");
    }

    @Test
    void createShouldRejectAccountOwnedByAnotherMerchant() {
        MerchantSettlementProfileMapper mapper = mock(MerchantSettlementProfileMapper.class);
        when(mapper.insertCommandAuditIfAbsent(any())).thenReturn(1);
        MerchantSettlementAccountDO account = account();
        account.setMerchantId("M2002");
        when(mapper.selectAccountForUpdate(9L)).thenReturn(account);
        SettlementProfileApplicationService service = new SettlementProfileApplicationService(mapper);

        assertThatThrownBy(() -> service.create(createRequest()))
                .hasMessageContaining("settlement account does not exist");
    }

    private ProfileCreateRequest createRequest() {
        ProfileCreateRequest request = new ProfileCreateRequest();
        request.setRequestKey("REQ-PROFILE-1");
        request.setMerchantId("M1001");
        request.setSettlementAccountId(9L);
        request.setTargetCurrency("usd");
        request.setBusinessTimeZone("Asia/Shanghai");
        request.setDailyCutoffTime(LocalTime.MIDNIGHT);
        request.setProcessingMode("auto_post");
        request.setReason("initial settlement setup");
        request.setOperatorId(88L);
        request.setOperatorName("Settlement Operator");
        request.setRoleSnapshot("SETTLEMENT_OPERATOR");
        request.setClientIp("10.0.0.8");
        request.setUserAgent("JUnit Admin");
        request.setOperationTime(LocalDateTime.of(2026, 11, 4, 18, 0));
        return request;
    }

    private ProfileVersionCreateRequest versionRequest() {
        ProfileVersionCreateRequest request = new ProfileVersionCreateRequest();
        request.setRequestKey("REQ-PROFILE-V2");
        request.setExpectedVersion(3L);
        request.setBusinessTimeZone("Asia/Tokyo");
        request.setDailyCutoffTime(LocalTime.of(23, 30));
        request.setProcessingMode("AUTO_REVIEW");
        request.setReason("change settlement calendar");
        request.setOperatorId(88L);
        request.setOperatorName("Settlement Operator");
        request.setRoleSnapshot("SETTLEMENT_OPERATOR");
        request.setClientIp("10.0.0.8");
        request.setUserAgent("JUnit Admin");
        request.setOperationTime(LocalDateTime.of(2026, 11, 4, 18, 30));
        return request;
    }

    private MerchantSettlementAccountDO account() {
        MerchantSettlementAccountDO account = new MerchantSettlementAccountDO();
        account.setId(9L);
        account.setAccountNo("FA-TEST-USD");
        account.setMerchantId("M1001");
        account.setSettlementCurrency("USD");
        account.setAccountStatus("NORMAL");
        return account;
    }

    private MerchantSettlementProfileDO activeProfile(String profileNo, long version) {
        MerchantSettlementProfileDO profile = new MerchantSettlementProfileDO();
        profile.setId(17L);
        profile.setSettlementProfileNo(profileNo);
        profile.setMerchantId("M1001");
        profile.setSettlementAccountId(9L);
        profile.setTargetCurrency("USD");
        profile.setTargetCurrencyExponent(2);
        profile.setBusinessTimeZone("Asia/Shanghai");
        profile.setDailyCutoffTime(LocalTime.MIDNIGHT);
        profile.setProcessingMode("AUTO_POST");
        profile.setProfileStatus("ACTIVE");
        profile.setActiveSlot(1);
        profile.setVersion(version);
        return profile;
    }
}

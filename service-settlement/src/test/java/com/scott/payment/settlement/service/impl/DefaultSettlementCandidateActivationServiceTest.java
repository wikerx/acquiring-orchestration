package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.entity.SettlementCandidateActivationDO;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateActivationServiceTest
 * @date : 2026-08-26 22:00
 * @email : scott_x@163.com
 * @description : 验证影子候选只有在结算档案、资金账户、目标币种和 ISO exponent 全部一致时才可批量激活。
 * @status : create
 */
class DefaultSettlementCandidateActivationServiceTest {

    private SettlementCandidateMapper candidateMapper;
    private DefaultSettlementCandidateActivationService service;

    @BeforeEach
    void setUp() {
        candidateMapper = mock(SettlementCandidateMapper.class);
        service = new DefaultSettlementCandidateActivationService(candidateMapper);
    }

    /** 合法档案应以一次批量 CAS 冻结 profile 并切换 shadow_mode。 */
    @Test
    void shouldActivateCandidatesWithMatchingProfileAndAccount() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        SettlementCandidateActivationDO row = activation("USD", 2, "USD", 2);
        when(candidateMapper.selectActivatableForUpdate(200)).thenReturn(List.of(row));
        when(candidateMapper.activateBatch(List.of(row), now)).thenReturn(1);

        int activated = service.activateEligibleCandidates(200, now);

        assertThat(activated).isEqualTo(1);
        verify(candidateMapper).activateBatch(List.of(row), now);
    }

    /** 即使查询投影异常，币种不一致也必须在写入前失败。 */
    @Test
    void shouldRejectCurrencyMismatchBeforeActivation() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        SettlementCandidateActivationDO row = activation("EUR", 2, "USD", 2);
        when(candidateMapper.selectActivatableForUpdate(200)).thenReturn(List.of(row));

        assertThatThrownBy(() -> service.activateEligibleCandidates(200, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target currency");
        verify(candidateMapper).selectActivatableForUpdate(200);
        verifyNoMoreInteractions(candidateMapper);
    }

    private SettlementCandidateActivationDO activation(String candidateCurrency,
                                                        int candidateExponent,
                                                        String profileCurrency,
                                                        int profileExponent) {
        SettlementCandidateActivationDO row = new SettlementCandidateActivationDO();
        row.setCandidateId(101L);
        row.setCandidateVersion(0L);
        row.setMerchantId("240001");
        row.setCandidateTargetCurrency(candidateCurrency);
        row.setCandidateTargetCurrencyExponent(candidateExponent);
        row.setSettlementEligibleDate(LocalDate.of(2026, 8, 26));
        row.setSettlementProfileId(11L);
        row.setSettlementAccountId(21L);
        row.setProfileTargetCurrency(profileCurrency);
        row.setProfileTargetCurrencyExponent(profileExponent);
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setDailyCutoffTime(LocalTime.of(0, 0));
        return row;
    }
}

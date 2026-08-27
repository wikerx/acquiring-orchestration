package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.entity.ClearingFeeVersionSnapshotRowDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingFeeVersionSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFeeVersionQueryServiceTest
 * @date : 2026-08-26 16:30
 * @email : scott_x@163.com
 * @description : 验证清分只读费用版本聚合对不可变版本损坏进行稳定分类，同时保留数据库技术异常的重试语义。
 * @status : create
 */
class DefaultFeeVersionQueryServiceTest {

    @Test
    void findVersionShouldRejectRowsWithMixedVersionIdentity() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO first = standardRule(101L);
        ClearingFeeVersionSnapshotRowDO mixed = standardRule(102L);
        mixed.setFeePlanVersionId(12L);
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(first, mixed));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE));
    }

    @Test
    void findVersionShouldClassifyMissingRuleAsNotConfigured() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO row = standardRule(101L);
        row.setFeeRuleId(null);
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(row));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED));
    }

    @Test
    void findVersionShouldRejectAmbiguousBusinessDimensions() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        when(mapper.selectVersionRows("M-1", 10L, 11L))
                .thenReturn(List.of(standardRule(101L), standardRule(102L)));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_RULE_AMBIGUOUS));
    }

    @Test
    void findVersionShouldClassifyMalformedRuleStructure() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO row = standardRule(101L);
        row.setFeeMode("UNKNOWN");
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(row));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE));
    }

    @Test
    void findVersionShouldRejectRowsWithMixedVersionTerms() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO first = standardRule(101L);
        ClearingFeeVersionSnapshotRowDO mixed = standardRule(102L);
        mixed.setFeeCategory("RISK_FEE");
        mixed.setRiskServiceType("INTERNAL");
        mixed.setReserveRate(new BigDecimal("12"));
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(first, mixed));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE));
    }

    @Test
    void findVersionShouldClassifyMalformedVersionHeader() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO row = standardRule(101L);
        row.setFeePlanVersionNo(0);
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(row));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE));
    }

    @Test
    void findVersionShouldPropagateMapperTechnicalFailure() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenThrow(databaseFailure);

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isSameAs(databaseFailure);
    }

    @Test
    void findVersionShouldPreserveLabelPercentageAndUsdFixedTerms() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO row = standardRule(101L);
        row.setMinimumAmountUsd(new BigDecimal("0.50"));
        row.setMaximumAmountUsd(new BigDecimal("20.00"));
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(row));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        var configuration = service.findVersionFromMaster("M-1", 10L, 11L);
        var rule = configuration.rules().get(0).calculationRule();

        assertThat(rule.percentageRate()).isEqualByComparingTo("2.3");
        assertThat(rule.fixedFeeUsd().currency()).isEqualTo("USD");
        assertThat(rule.fixedFeeUsd().amount()).isEqualByComparingTo("0.30");
        assertThat(rule.minimumFeeUsd().amount()).isEqualByComparingTo("0.50");
        assertThat(rule.maximumFeeUsd().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void findVersionShouldRejectConflictingRowsForSameRule() {
        ClearingFeeVersionSnapshotMapper mapper = mock(ClearingFeeVersionSnapshotMapper.class);
        ClearingFeeVersionSnapshotRowDO first = standardRule(101L);
        ClearingFeeVersionSnapshotRowDO conflicting = standardRule(101L);
        conflicting.setPercentageRate(new BigDecimal("9.9"));
        when(mapper.selectVersionRows("M-1", 10L, 11L)).thenReturn(List.of(first, conflicting));

        DefaultFeeVersionQueryService service = new DefaultFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("M-1", 10L, 11L))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE));
    }

    private ClearingFeeVersionSnapshotRowDO standardRule(Long ruleId) {
        ClearingFeeVersionSnapshotRowDO row = new ClearingFeeVersionSnapshotRowDO();
        row.setMerchantId("M-1");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setFeePlanVersionNo(2);
        row.setSettlementCurrency("USD");
        row.setReserveRate(new BigDecimal("10"));
        row.setReserveDelayUnit("D");
        row.setReserveDelayDays(180);
        row.setFeeRuleId(ruleId);
        row.setFeeCategory("TRANSACTION_FEE");
        row.setTransactionType("PAYMENT");
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setRiskServiceType("NONE");
        row.setChargeTrigger("NOT_APPLICABLE");
        row.setFeeMode("STANDARD");
        row.setPercentageRate(new BigDecimal("2.3"));
        row.setFixedAmountUsd(new BigDecimal("0.30"));
        return row;
    }
}

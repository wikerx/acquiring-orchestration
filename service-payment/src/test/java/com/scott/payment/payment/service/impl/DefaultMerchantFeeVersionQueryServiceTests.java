package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.payment.entity.MerchantFeeVersionSnapshotRowDO;
import com.scott.payment.payment.mapper.MerchantFeeVersionSnapshotMapper;
import com.scott.payment.payment.service.dto.MerchantFeeVersionConfigurationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Payment merchant fee version read-boundary tests. */
class DefaultMerchantFeeVersionQueryServiceTests {

    @Test
    void shouldAggregateRulesAndTiersWithoutChangingUsdAmountSemantics() {
        MerchantFeeVersionSnapshotMapper mapper = mock(MerchantFeeVersionSnapshotMapper.class);
        when(mapper.selectVersionRows("200045", 1001L, 1008L)).thenReturn(List.of(
                standardRule(2001L), tierRule(2002L, 3001L, "0", "1000"),
                tierRule(2002L, 3002L, "1000", null)));
        DefaultMerchantFeeVersionQueryService service = new DefaultMerchantFeeVersionQueryService(mapper);

        MerchantFeeVersionConfigurationDTO configuration =
                service.findVersionFromSlave("200045", 1001L, 1008L);

        assertThat(configuration.merchantId()).isEqualTo("200045");
        assertThat(configuration.feePlanVersionNo()).isEqualTo(8);
        assertThat(configuration.rules()).hasSize(2);
        assertThat(configuration.rules().get(0).calculationRule().feeMode()).isEqualTo(FeeMode.STANDARD);
        assertThat(configuration.rules().get(0).calculationRule().percentageRate())
                .isEqualByComparingTo("2.30000000");
        assertThat(configuration.rules().get(0).calculationRule().fixedFeeUsd().currency())
                .isEqualTo("USD");
        assertThat(configuration.rules().get(0).calculationRule().fixedFeeUsd().amount())
                .isEqualByComparingTo("0.30000000");
        assertThat(configuration.rules().get(0).calculationRule().minimumFeeUsd().amount())
                .isEqualByComparingTo("0.50000000");
        assertThat(configuration.rules().get(0).calculationRule().maximumFeeUsd().amount())
                .isEqualByComparingTo("5.00000000");
        assertThat(configuration.rules().get(1).calculationRule().tierMetric()).isEqualTo(TierMetric.AMOUNT);
        assertThat(configuration.rules().get(1).tiers()).hasSize(2);
        assertThat(configuration.rules().get(1).tiers().get(1).upperBound()).isNull();
    }

    @Test
    void shouldRejectMixedMerchantRows() {
        MerchantFeeVersionSnapshotMapper mapper = mock(MerchantFeeVersionSnapshotMapper.class);
        MerchantFeeVersionSnapshotRowDO first = standardRule(2001L);
        MerchantFeeVersionSnapshotRowDO second = standardRule(2002L);
        second.setMerchantId("OTHER");
        when(mapper.selectVersionRows("200045", 1001L, 1008L)).thenReturn(List.of(first, second));
        DefaultMerchantFeeVersionQueryService service = new DefaultMerchantFeeVersionQueryService(mapper);

        assertThatThrownBy(() -> service.findVersionFromMaster("200045", 1001L, 1008L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Merchant active fee configuration is incomplete");
    }

    @Test
    void shouldSuspendTransactionShardBeforeReadingFeeMasterOrSlave() throws Exception {
        assertReadBoundary("findActivePointerFromMaster", DataSourceName.MASTER, String.class);
        assertReadBoundary("findVersionFromSlave", DataSourceName.SLAVE,
                String.class, Long.class, Long.class);
        assertReadBoundary("findVersionFromMaster", DataSourceName.MASTER,
                String.class, Long.class, Long.class);
    }

    private void assertReadBoundary(String methodName,
                                    String expectedDataSource,
                                    Class<?>... parameterTypes) throws Exception {
        Method method = DefaultMerchantFeeVersionQueryService.class
                .getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(DS.class).value()).isEqualTo(expectedDataSource);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private MerchantFeeVersionSnapshotRowDO standardRule(Long ruleId) {
        MerchantFeeVersionSnapshotRowDO row = baseRule(ruleId);
        row.setFeeMode("STANDARD");
        row.setPercentageRate(new BigDecimal("2.30000000"));
        row.setFixedAmountUsd(new BigDecimal("0.30000000"));
        row.setMinimumAmountUsd(new BigDecimal("0.50000000"));
        row.setMaximumAmountUsd(new BigDecimal("5.00000000"));
        return row;
    }

    private MerchantFeeVersionSnapshotRowDO tierRule(Long ruleId,
                                                     Long tierId,
                                                     String lowerBound,
                                                     String upperBound) {
        MerchantFeeVersionSnapshotRowDO row = baseRule(ruleId);
        row.setFeeMode("TIER");
        row.setTierMetric("AMOUNT");
        row.setPercentageRate(BigDecimal.ZERO);
        row.setFixedAmountUsd(BigDecimal.ZERO);
        row.setFeeTierId(tierId);
        row.setTierLowerBound(new BigDecimal(lowerBound));
        row.setTierUpperBound(upperBound == null ? null : new BigDecimal(upperBound));
        row.setTierPercentageRate(new BigDecimal("1.50000000"));
        row.setTierFixedAmountUsd(new BigDecimal("0.20000000"));
        return row;
    }

    private MerchantFeeVersionSnapshotRowDO baseRule(Long ruleId) {
        MerchantFeeVersionSnapshotRowDO row = new MerchantFeeVersionSnapshotRowDO();
        row.setMerchantId("200045");
        row.setFeePlanId(1001L);
        row.setFeePlanVersionId(1008L);
        row.setFeePlanVersionNo(8);
        row.setSettlementCurrency("USD");
        row.setReserveRate(new BigDecimal("10.00000000"));
        row.setReserveDelayUnit("D");
        row.setReserveDelayDays(180);
        row.setFeeRuleId(ruleId);
        row.setFeeCategory("TRANSACTION_FEE");
        row.setTransactionType("PAYMENT");
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setRiskServiceType("NONE");
        row.setChargeTrigger("SUCCESS");
        return row;
    }
}

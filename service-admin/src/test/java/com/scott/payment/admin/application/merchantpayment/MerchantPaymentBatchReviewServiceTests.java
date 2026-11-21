package com.scott.payment.admin.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentScope;

import com.scott.payment.capability.governance.MerchantPaymentMethodReadiness;

import com.scott.payment.capability.governance.MerchantPaymentRepository;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentModels.BatchDecision;
import com.scott.payment.capability.governance.MerchantPaymentModels.Decision;
import com.scott.payment.capability.governance.MerchantPaymentModels.DecisionItem;
import com.scott.payment.capability.governance.MerchantPaymentModels.DecisionOutcome;
import com.scott.payment.capability.governance.MerchantPaymentModels.ItemReceipt;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantPaymentBatchReviewServiceTests {
    private final MerchantPaymentGovernanceService authority = mock(MerchantPaymentGovernanceService.class);
    private final MerchantPaymentBatchReviewService batch = new MerchantPaymentBatchReviewService(authority);
    private final Scope scope = new Scope(MerchantPaymentScope.all(),
            new MerchantPaymentRepository.Actor("ADMIN", 16L, 17L, "checker"));

    @Test
    void continuesAfterDefiniteRejectionAndStopsAfterAnUncertainResult() {
        Decision first = new Decision("request-1", "1", "APPROVE", null);
        Decision second = new Decision("request-2", "1", "APPROVE", null);
        Decision third = new Decision("request-3", "1", "APPROVE", null);
        Decision fourth = new Decision("request-4", "1", "APPROVE", null);
        when(authority.decide(scope, 1L, first)).thenReturn(
                new ItemReceipt("batch", "1", "CARD_BRAND:VISA", "APPROVED", "2", "1", "117"));
        when(authority.decide(scope, 2L, second)).thenThrow(new ServiceException(ApiResultEnum.FORBIDDEN));
        when(authority.decide(scope, 3L, third)).thenThrow(new IllegalStateException("private-problem"));

        var result = batch.decide(scope, new BatchDecision(List.of(
                new DecisionItem("1", first), new DecisionItem("2", second),
                new DecisionItem("3", third), new DecisionItem("4", fourth))));

        assertThat(result.results()).extracting(DecisionOutcome::outcome)
                .containsExactly("SUCCEEDED", "FAILED", "UNKNOWN", "NOT_PROCESSED");
        assertThat(result.results().get(2).message()).doesNotContain("private-problem");
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.unknown()).isEqualTo(1);
        assertThat(result.unprocessed()).isEqualTo(1);
        verify(authority, never()).decide(scope, 4L, fourth);
    }

    @Test
    void repeatedItemIsRejectedBeforeAnyBusinessTransaction() {
        var item = new DecisionItem("1", new Decision("request-1", "1", "APPROVE", null));
        assertThatThrownBy(() -> batch.decide(scope, new BatchDecision(List.of(item, item))))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(authority);
    }
}

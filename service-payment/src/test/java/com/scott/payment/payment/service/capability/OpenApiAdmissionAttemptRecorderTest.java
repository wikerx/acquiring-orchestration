package com.scott.payment.payment.service.capability;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.service.impl.DefaultTransactionIdempotencyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenApiAdmissionAttemptRecorderTest {
    private static final String FIRST_ACTION = "200045:ACTION-A:PAYMENT";
    private static final String SECOND_ACTION = "200045:ACTION-B:PAYMENT";
    private static final LocalDateTime FIRST_INGRESS = LocalDateTime.parse("2026-09-30T23:59:59.987543");
    private static final LocalDateTime CROSS_SHARD_REPLAY = LocalDateTime.parse("2026-10-01T00:00:00.003123");

    private final TransactionIdempotencyMapper idempotencyMapper = mock(TransactionIdempotencyMapper.class);
    private final TransactionMerchantApiInteractionLogMapper interactionMapper =
            mock(TransactionMerchantApiInteractionLogMapper.class);
    private final OpenApiAdmissionAttemptRecorder recorder = new OpenApiAdmissionAttemptRecorder(
            new DefaultTransactionIdempotencyService(idempotencyMapper), idempotencyMapper, interactionMapper);

    @Test
    void independentlySubmittedActionsEachKeepARejectedFactWithoutAnyPreallocatedOrder() {
        when(idempotencyMapper.insert(any(TransactionIdempotencyDO.class))).thenReturn(1);
        when(interactionMapper.insertAdmissionRejection(any())).thenReturn(1);

        var first = recorder.recordKnownRejection(command("ACTION-A", FIRST_INGRESS), FIRST_ACTION, rejection());
        var second = recorder.recordKnownRejection(command("ACTION-B", CROSS_SHARD_REPLAY), SECOND_ACTION, rejection());
        assertThat(first.getTransactionId()).isNull();
        assertThat(second.getOperationId()).isNull();
        assertThat(first.getExpireTime()).isAfter(first.getCreateTime().plusDays(29));
        assertThat(first.getTransactionDateTime()).isEqualTo("2026-09-30T23:59:59.987");

        var logs = ArgumentCaptor.forClass(TransactionMerchantApiInteractionLogDO.class);
        verify(interactionMapper, times(2)).insertAdmissionRejection(logs.capture());
        assertThat(logs.getAllValues()).extracting(TransactionMerchantApiInteractionLogDO::getApiLogId)
                .doesNotHaveDuplicates();
        assertThat(logs.getAllValues()).allSatisfy(log -> {
            assertThat(log.getTransactionId()).isNull();
            assertThat(log.getOperationId()).isNull();
            assertThat(log.getRequestPlainJsonMasked()).isNull();
            assertThat(log.getMerchantResponseCode()).isEqualTo("F409001");
            assertThat(log.getAdmissionContextJson()).contains("AUTHORIZATION_UNAVAILABLE", "CARD_BRAND:VISA")
                    .doesNotContain("premature-order-id", "untrusted-request-payload");
        });
    }

    @Test
    void reusesFirstRejectionAndShardAcrossTheQuarterBoundary() {
        when(idempotencyMapper.insert(any(TransactionIdempotencyDO.class))).thenReturn(1)
                .thenThrow(new DuplicateKeyException("same action"));
        when(interactionMapper.insertAdmissionRejection(any())).thenReturn(1);
        var first = recorder.recordKnownRejection(command("ACTION-A", FIRST_INGRESS), FIRST_ACTION, rejection());
        when(idempotencyMapper.selectOne(any())).thenReturn(first);

        var replay = recorder.recordKnownRejection(command("ACTION-A", CROSS_SHARD_REPLAY), FIRST_ACTION, rejection());

        assertThat(replay).isSameAs(first);
        assertThat(replay.getTransactionDateTime()).isEqualTo("2026-09-30T23:59:59.987");
        assertThatThrownBy(() -> OpenApiAdmissionAttemptRecorder.rethrowIfRejected(replay, "canonical-fingerprint"))
                .isInstanceOfSatisfying(PaymentAdmissionRejectedException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("F409001");
                    assertThat(failure.isReplay()).isTrue();
                    assertThat(failure.authorizationRevision()).isEqualTo(42L);
                });
        assertThatThrownBy(() -> OpenApiAdmissionAttemptRecorder.rethrowIfRejected(replay, "changed-content"))
                .isInstanceOfSatisfying(ServiceException.class, failure ->
                        assertThat(failure.getMessage()).contains("different payment request"));
        verify(interactionMapper).insertAdmissionRejection(any());
    }

    @Test
    void losingAgainstAnExistingRealOrderDoesNotCreateASecondFact() {
        when(idempotencyMapper.insert(any(TransactionIdempotencyDO.class)))
                .thenThrow(new DuplicateKeyException("same action"));
        var realOrder = new TransactionIdempotencyDO();
        realOrder.setTransactionId("real-order-id");
        realOrder.setOperationId("real-operation-id");
        when(idempotencyMapper.selectOne(any())).thenReturn(realOrder);

        var winner = recorder.recordKnownRejection(command("ACTION-A", FIRST_INGRESS), FIRST_ACTION, rejection());

        assertThat(winner).isSameAs(realOrder);
        OpenApiAdmissionAttemptRecorder.rethrowIfRejected(winner, "canonical-fingerprint");
        verify(interactionMapper, never()).insertAdmissionRejection(any());
    }

    @Test
    void requestsWithoutTrustedSourceNeverAccessStorage() {
        var command = command("ACTION-A", FIRST_INGRESS);
        command.setTrustedOpenApiAdmissionRequest(false);

        assertThatThrownBy(() -> recorder.recordKnownRejection(command, FIRST_ACTION, rejection()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(idempotencyMapper, interactionMapper);
    }

    private PaymentCreateCommandDTO command(String actionId, LocalDateTime requestTime) {
        var command = new PaymentCreateCommandDTO();
        command.setTrustedOpenApiAdmissionRequest(true);
        command.setMerchantId("200045");
        command.setMerchantOrderNo("same-merchant-order");
        command.setMerchantOrderId(actionId);
        command.setRequestId(actionId);
        command.setTransactionId("premature-order-id");
        command.setTransactionType("PAYMENT");
        command.setAmount(new BigDecimal("12.34"));
        command.setCurrency("USD");
        command.setOpenApiRequestPath("/v1/payment");
        command.setOpenApiRequestTime(requestTime);
        command.setMerchantRequestPlainJsonMasked("untrusted-request-payload");
        command.setRequestFingerprint("canonical-fingerprint");
        return command;
    }

    private PaymentAdmissionRejectedException rejection() {
        return new PaymentAdmissionRejectedException("CARD_BRAND:VISA",
                PaymentAdmissionRejectedException.Reason.AUTHORIZATION_UNAVAILABLE, 42L);
    }
}

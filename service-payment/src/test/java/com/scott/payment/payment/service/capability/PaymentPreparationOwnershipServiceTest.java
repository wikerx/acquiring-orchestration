package com.scott.payment.payment.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.LuhnMod10Utils;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CancellationDecision;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CheckoutRecoveryState;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.Lease;
import com.scott.payment.payment.service.impl.DefaultTransactionIdempotencyService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPreparationOwnershipServiceTest {

    private static final String MERCHANT = "FIXTURE_MERCHANT";
    private static final String ORDER_NO = "FIXTURE_ORDER_NO";
    private static final String ATTEMPT = "FIXTURE_ATTEMPT";
    private static final String TYPE = "PAYMENT";
    private static final String ZONE = "Asia/Shanghai";
    private static final String ACTION = MERCHANT + ":" + ATTEMPT + ":" + TYPE;
    private static final String FIRST_CANDIDATE = "FIXTURE_CANDIDATE_1";
    private static final String NEW_CANDIDATE_BODY = "261001000300000000002";
    private static final String NEW_CANDIDATE = NEW_CANDIDATE_BODY + LuhnMod10Utils.calculateCheckDigit(NEW_CANDIDATE_BODY);
    private static final String FINGERPRINT = "fixture_fingerprint";
    private static final LocalDateTime ORDER_TIME = LocalDateTime.parse("2026-09-30T23:59:59.123");
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-01T00:03:00");

    private final TransactionIdempotencyMapper mapper = mock(TransactionIdempotencyMapper.class);
    private final GlobalIdGenerator ids = mock(GlobalIdGenerator.class);
    private final PaymentPreparationOwnershipService ownership =
            new PaymentPreparationOwnershipService(mapper, new DefaultTransactionIdempotencyService(mapper), ids);
    private String ownershipKey;

    @BeforeEach
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), TransactionIdempotencyDO.class);
        ownershipKey = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ACTION.getBytes(StandardCharsets.UTF_8)));
        when(mapper.currentDatabaseTime()).thenReturn(NOW);
        when(mapper.insert(any(TransactionIdempotencyDO.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
    }

    @Test
    void acquiresHeadAndCandidateWithOriginalPartitionTimeBeforeRisk() {
        PaymentCreateCommandDTO command = command();
        command.setTransactionDateTime(ORDER_TIME.plusNanos(45));

        Lease lease = ownership.acquire(command, ACTION, TYPE, ZONE, NEW_CANDIDATE).lease();

        assertThat(lease.key()).isEqualTo(ownershipKey);
        assertThat(lease.generation()).isEqualTo(1);
        assertThat(lease.candidateTransactionId()).hasSize(22).isEqualTo(NEW_CANDIDATE);
        assertThat(lease.transactionDateTime()).isEqualTo(ORDER_TIME);
        var rows = ArgumentCaptor.forClass(TransactionIdempotencyDO.class);
        verify(mapper, times(2)).insert(rows.capture());
        assertThat(rows.getAllValues()).extracting(TransactionIdempotencyDO::getIdempotencyScope)
                .containsExactly("PAYMENT_PREPARATION_CANDIDATE", "PAYMENT_PREPARATION");
        assertThat(rows.getAllValues().get(0).getOperationId()).isEqualTo(ownershipKey);
        assertThat(rows.getAllValues().get(rows.getAllValues().size() - 1).getOperationId()).isEqualTo(FIRST_CANDIDATE);
        assertThat(rows.getAllValues().get(rows.getAllValues().size() - 1).getExpireTime()).isEqualTo(NOW.plusMinutes(2));
    }

    @Test
    void rejectsACandidateCollisionWithoutCreatingASecondHeadOrRetryingUnderLock() {
        when(mapper.insert(any(TransactionIdempotencyDO.class)))
                .thenThrow(new DuplicateKeyException("candidate occupied"));

        assertThatThrownBy(() -> ownership.acquire(command(), ACTION, TYPE, ZONE, NEW_CANDIDATE))
                .isInstanceOf(ServiceException.class);

        verify(mapper, times(1)).insert(any(TransactionIdempotencyDO.class));
        org.mockito.Mockito.verifyNoInteractions(ids);
    }

    @Test
    void usesOnlyValidGlobalIdsAndAllocatesWithoutTouchingTheDatabase() {
        when(ids.nextId()).thenReturn(NEW_CANDIDATE, "202610010003000000001");

        assertThat(ownership.nextCandidate()).isEqualTo(NEW_CANDIDATE);
        assertThatThrownBy(ownership::nextCandidate).isInstanceOf(ServiceException.class);
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void disabledPathReusesOriginalOutcomeAndNeverCrossesAnExistingProtectedHead() {
        TransactionIdempotencyDO head = row("PREPARING", 1, FIRST_CANDIDATE);
        TransactionIdempotencyDO completed = row("PROCESSING", 1, FIRST_CANDIDATE);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, completed,
                head, null, head);

        assertThat(ownership.readBeforeLegacyPreparation(ACTION)).isSameAs(completed);
        assertThatThrownBy(() -> ownership.readBeforeLegacyPreparation(ACTION)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> ownership.requireLegacyPreparationAllowed(ACTION)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).insert(any(TransactionIdempotencyDO.class));
    }

    @Test
    void takesOverExpiredGenerationAndRejectsLateOwnerWithItsOldPartitionTime() {
        TransactionIdempotencyDO old = row("PREPARING", 1, FIRST_CANDIDATE);
        old.setExpireTime(NOW.minusSeconds(1));
        old.setOperationId(FIRST_CANDIDATE);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(old, (TransactionIdempotencyDO) null);
        PaymentCreateCommandDTO retry = command();
        retry.setTransactionDateTime(NOW);

        Lease next = ownership.acquire(retry, ACTION, TYPE, ZONE, NEW_CANDIDATE).lease();

        assertThat(next.generation()).isEqualTo(2);
        assertThat(next.candidateTransactionId()).isNotEqualTo(FIRST_CANDIDATE);
        assertThat(next.transactionDateTime()).isEqualTo(ORDER_TIME);
        verify(mapper, times(3)).update(isNull(), any());
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("PREPARING", 2, next.candidateTransactionId()));
        assertThatThrownBy(() -> ownership.requireCurrent(lease(1, FIRST_CANDIDATE)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void staleRiskReleaseRequiresTheExactTerminatedCandidateProof() {
        TransactionIdempotencyDO current = row("PREPARING", 2, "FIXTURE_CANDIDATE_2");
        TransactionIdempotencyDO receipt = row("TERMINATED", 1, FIRST_CANDIDATE);
        receipt.setOperationId(ownershipKey);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current, receipt);

        assertThat(ownership.reconcileCancellation(MERCHANT, ownershipKey, 1, FIRST_CANDIDATE, ORDER_TIME))
                .isEqualTo(CancellationDecision.RELEASE_ALLOWED);

        receipt.setOperationId("unmatched-ownership-key");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current, receipt);
        assertThat(ownership.reconcileCancellation(MERCHANT, ownershipKey, 1, FIRST_CANDIDATE, ORDER_TIME))
                .isEqualTo(CancellationDecision.DEFER);
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void retainsAttachedAndUnexpiredGenerations() {
        TransactionIdempotencyDO attached = row("ATTACHED", 1, FIRST_CANDIDATE);
        TransactionIdempotencyDO active = row("PREPARING", 1, FIRST_CANDIDATE);
        active.setExpireTime(NOW.plusSeconds(1));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(attached, active);

        assertThat(ownership.terminateUncommitted(lease(1, FIRST_CANDIDATE))).isEqualTo(CancellationDecision.RETAIN);
        assertThat(ownership.reconcileCancellation(MERCHANT, ownershipKey, 1, FIRST_CANDIDATE, ORDER_TIME))
                .isEqualTo(CancellationDecision.RETAIN);
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void confirmsRollbackBeforeAllowingRiskReleaseAndDefersWhenEvidenceMissing() {
        TransactionIdempotencyDO active = row("PREPARING", 1, FIRST_CANDIDATE);
        active.setExpireTime(NOW.plusMinutes(1));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(active, (TransactionIdempotencyDO) null);

        assertThat(ownership.terminateUncommitted(lease(1, FIRST_CANDIDATE)))
                .isEqualTo(CancellationDecision.RELEASE_ALLOWED);
        verify(mapper, times(2)).update(isNull(), any());

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn((TransactionIdempotencyDO) null);
        assertThat(ownership.reconcileCancellation(MERCHANT, ownershipKey, 1, FIRST_CANDIDATE, ORDER_TIME))
                .isEqualTo(CancellationDecision.DEFER);
    }

    @Test
    void checkoutOnlyRecoversTheCommittedOriginalAttemptDespiteChangedCandidate() {
        TransactionIdempotencyDO head = row("ATTACHED", 2, "FIXTURE_CANDIDATE_2");
        head.setOperationId(FIRST_CANDIDATE);
        TransactionIdempotencyDO operation = row("FAILED", 2, "FIXTURE_CANDIDATE_2");
        operation.setOperationId("FIXTURE_COMMITTED_OPERATION");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, operation);

        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "provisional-operation", ORDER_TIME, false).operation()).isSameAs(operation);

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, operation);
        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                "unrelated-checkout-attempt", "different-operation", ORDER_TIME, false).state())
                .isEqualTo(CheckoutRecoveryState.ACTIVE);

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, operation);
        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "provisional-operation", NOW, false).state()).isEqualTo(CheckoutRecoveryState.ACTIVE);
    }

    @Test
    void checkoutOnlyConfirmsTerminationWithOriginalAnchorAndNoCommittedOperation() {
        TransactionIdempotencyDO head = row("PREPARING", 1, FIRST_CANDIDATE);
        head.setOperationId(FIRST_CANDIDATE);
        head.setExpireTime(NOW.minusSeconds(1));
        TransactionIdempotencyDO accepted = row("PROCESSING", 1, FIRST_CANDIDATE);
        accepted.setOperationId("OP-COMMITTED");

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, accepted);
        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "OP-PROVISIONAL", ORDER_TIME, true).state()).isEqualTo(CheckoutRecoveryState.ACTIVE);
        verify(mapper, never()).update(isNull(), any());

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, (TransactionIdempotencyDO) null);
        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "OP-PROVISIONAL", ORDER_TIME, true).state()).isEqualTo(CheckoutRecoveryState.TERMINATED);
        verify(mapper, times(2)).update(isNull(), any());
    }

    @Test
    void aHardInterruptionDoesNotTakeTheExistingAttachedContextBeforeLeaseExpiry() {
        TransactionIdempotencyDO head = row("ATTACHED", 1, FIRST_CANDIDATE);
        head.setOperationId(FIRST_CANDIDATE);
        head.setExpireTime(NOW.plusSeconds(1));
        TransactionIdempotencyDO operation = row("PROCESSING", 1, FIRST_CANDIDATE);
        operation.setOperationId("OP-COMMITTED");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, operation);

        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "OP-PROVISIONAL", ORDER_TIME, true).state()).isEqualTo(CheckoutRecoveryState.ACTIVE);
        verify(mapper, times(1)).selectOne(any(LambdaQueryWrapper.class));

        head.setExpireTime(NOW);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(head, operation);
        var result = ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "OP-PROVISIONAL", ORDER_TIME, true);
        assertThat(result.state()).isEqualTo(CheckoutRecoveryState.ATTACHED);
        assertThat(result.operation()).isSameAs(operation);
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void missingOwnershipIsNotAnAuthorityToTerminateAfterAHardInterruption() {
        assertThat(ownership.recoverCheckoutPreparation(MERCHANT, ORDER_NO, ATTEMPT, TYPE,
                FIRST_CANDIDATE, "OP-PROVISIONAL", ORDER_TIME, true).state()).isEqualTo(CheckoutRecoveryState.ACTIVE);

        verify(mapper, never()).currentDatabaseTime();
        verify(mapper, never()).insert(any(TransactionIdempotencyDO.class));
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void anotherEntryCannotTakeOverAnExpiredCheckoutAttempt() {
        TransactionIdempotencyDO pending = row("PREPARING", 1, FIRST_CANDIDATE);
        pending.setOperationId(FIRST_CANDIDATE);
        pending.setExpireTime(NOW.minusMinutes(1));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending, (TransactionIdempotencyDO) null);
        PaymentCreateCommandDTO direct = command();
        direct.setRequestSource("OPENAPI");

        assertThatThrownBy(() -> ownership.acquire(direct, ACTION, TYPE, ZONE, NEW_CANDIDATE)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).update(isNull(), any());
    }

    private PaymentCreateCommandDTO command() {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId(MERCHANT);
        command.setMerchantOrderNo(ORDER_NO);
        command.setMerchantOrderId(ATTEMPT);
        command.setTransactionDateTime(ORDER_TIME);
        command.setTransactionId(FIRST_CANDIDATE);
        command.setRequestSource("HOSTED_CHECKOUT");
        command.setRequestFingerprint(FINGERPRINT);
        return command;
    }

    private Lease lease(int generation, String candidate) {
        return new Lease(ownershipKey, ACTION, MERCHANT, generation, candidate, ORDER_TIME, FINGERPRINT);
    }

    private TransactionIdempotencyDO row(String status, int generation, String candidate) {
        TransactionIdempotencyDO row = new TransactionIdempotencyDO();
        row.setId(101L);
        row.setIdempotencyKey(ownershipKey);
        row.setMerchantId(MERCHANT);
        row.setMerchantOrderNo(ORDER_NO);
        row.setMerchantOrderId(ATTEMPT);
        row.setTransactionType(TYPE);
        row.setTransactionStatus(status);
        row.setVersion(generation);
        row.setTransactionId(candidate);
        row.setTransactionDateTime(ORDER_TIME);
        row.setTransactionTimeZone(ZONE);
        row.setRequestFingerprint(FINGERPRINT);
        return row;
    }
}

package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository.Actor;
import com.scott.payment.capability.governance.MerchantPaymentRepository.ReadVersion;
import com.scott.payment.capability.governance.MerchantPaymentScope;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.merchant.application.merchantpayment.MerchantPaymentReadDataSources.ReplicaResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantPaymentReadsTests {
    private static final String MERCHANT = "200046";
    private static final ReadVersion VERSION = new ReadVersion(2L, 101L, "ENFORCED", null);
    private final MerchantPaymentReadDataSources sources = mock(MerchantPaymentReadDataSources.class);
    private final MerchantPaymentReads reads = new MerchantPaymentReads(sources);

    @BeforeEach
    void authenticate() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setMerchantId(MERCHANT);
        account.setAccountId(7L);
        account.setUserId(9L);
        InternalAuthContextHolder.set(account);
        when(sources.verifyMerchant(MERCHANT, 7L, 9L)).thenReturn(
                new Scope(MerchantPaymentScope.limited(Set.of(MERCHANT)), new Actor("MERCHANT", 7L, 9L, null)));
        when(sources.committedVersion(MERCHANT)).thenReturn(VERSION);
    }

    @AfterEach
    void clearSession() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void staleReplicaReadsApprovedStateFromPrimary() {
        when(sources.readReplica(eq(MERCHANT), eq(VERSION), any())).thenReturn(new ReplicaResult<>(false, null));
        when(sources.readMaster(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());

        assertThat(reads.<String>read(MERCHANT, scope -> {
            assertThat(scope.merchants().merchantIds()).containsExactly(MERCHANT);
            return "APPROVED";
        })).isEqualTo("APPROVED");
    }

    @Test
    void caughtUpReplicaDoesNotRunMainBusinessQueryOnPrimary() {
        when(sources.readReplica(eq(MERCHANT), eq(VERSION), any())).thenAnswer(call ->
                new ReplicaResult<>(true, ((Supplier<?>) call.getArgument(2)).get()));

        assertThat(reads.<String>read(MERCHANT, scope -> "APPROVED")).isEqualTo("APPROVED");
        verify(sources, never()).readMaster(any());
    }

    @Test
    void unavailableReplicaFallsBackToPrimaryForOnlyThisRequest() {
        when(sources.readReplica(eq(MERCHANT), eq(VERSION), any()))
                .thenThrow(new DataAccessResourceFailureException("replica unavailable"));
        when(sources.readMaster(any())).thenReturn("APPROVED");

        assertThat(reads.<String>read(MERCHANT, scope -> "unused")).isEqualTo("APPROVED");
    }
}

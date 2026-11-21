package com.scott.payment.admin.application.merchantpayment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.Apply;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.AuthorizationCoverage;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.BaselineApprove;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.BaselineSubmit;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.Evidence;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.Change;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.Decision;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.scott.payment.admin.application.merchantpayment.MerchantPaymentRepository.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantPaymentGovernanceServiceTests {
    private static final String MERCHANT = "200045";
    private static final String VISA = "CARD_BRAND:VISA";
    private final MerchantPaymentRepository repository = mock(MerchantPaymentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MerchantPaymentGovernanceService governance = new MerchantPaymentGovernanceService(
            repository, mock(AdminMerchantDataScopeResolver.class), () -> "2600000000000000000001",
            new ObjectMapper().findAndRegisterModules(), events, mock(MerchantPaymentMethodReadiness.class));

    @Test
    void merchantLookupBindsTheAdministratorsScopeAndStopsWhenItIsEmpty() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var lookup = new MerchantPaymentRepository(jdbc);
        assertThat(lookup.merchantOptions(AdminMerchantDataScope.limited(Set.of()), "Example")).isEmpty();
        verifyNoInteractions(jdbc);

        lookup.merchantOptions(AdminMerchantDataScope.limited(Set.of(MERCHANT)), " Example ");

        verify(jdbc).query(contains("AND merchant_id IN (:scopeMerchants)"),
                argThat((MapSqlParameterSource parameters) -> "Example".equals(parameters.getValue("keyword"))
                        && Set.of(MERCHANT).equals(parameters.getValue("scopeMerchants"))),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any());
    }

    @Test
    void feeAuthorizationKeepsFormalAndRestrictedFactsWhileHistoryIsPending() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.limited(Set.of(MERCHANT)),
                new Actor("MERCHANT", 19, 8, null));
        LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime future = LocalDateTime.of(2099, 1, 1, 0, 0);
        when(repository.legacyMerchant(MERCHANT)).thenReturn(Optional.of(new LegacyMerchant(null, null, past)));
        when(repository.directory(false)).thenReturn(List.of(
                new DirectoryEntry("acquiring_payment_method", "BANK_CARD", "Card", "en-US", true),
                new DirectoryEntry("card_brand", "VISA", "Visa", "en-US", true),
                new DirectoryEntry("card_brand", "MASTERCARD", "Mastercard", "en-US", true)));
        when(repository.grants(MERCHANT)).thenReturn(List.of(
                new Grant(1, "CARD_BRAND:MASTERCARD", "ACTIVE", 1, past, null, "APPLICATION"),
                new Grant(2, "METHOD:APPLE_PAY", "ACTIVE", 1, past, null, "HISTORICAL_BASELINE"),
                new Grant(3, "METHOD:GOOGLE_PAY", "SUSPENDED", 1, past, null, "HISTORICAL_BASELINE"),
                new Grant(4, "METHOD:PAYPAL", "ACTIVE", 1, future, null, "APPLICATION"),
                new Grant(5, "METHOD:ACHPAY", "ACTIVE", 1, past, past.plusDays(1), "APPLICATION")));

        var result = governance.feeAuthorizations(scope, MERCHANT);

        assertThat(result.assessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(result.methods()).extracting(AuthorizationCoverage::itemKey, AuthorizationCoverage::authorizationStatus)
                .containsExactly(
                        tuple("CARD_BRAND:MASTERCARD", "ACTIVE"),
                        tuple(VISA, "NOT_AUTHORIZED"),
                        tuple("METHOD:ACHPAY", "EXPIRED"),
                        tuple("METHOD:APPLE_PAY", "NOT_AUTHORIZED"),
                        tuple("METHOD:GOOGLE_PAY", "SUSPENDED"),
                        tuple("METHOD:PAYPAL", "NOT_YET_EFFECTIVE"));
        verify(repository, never()).frozenMethods(MERCHANT);
        assertThat(governance.accessSummary(scope, MERCHANT).assessmentStatus()).isEqualTo("HISTORY_PENDING");
    }

    @Test
    void applyNormalizesSetAndReplaysOriginalBatchBeforeReevaluatingDirectory() {
        var actor = new Actor("MERCHANT", 19, 8, null);
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.limited(Set.of(MERCHANT)), actor);
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.lockGrant(MERCHANT, VISA)).thenReturn(Optional.empty());
        when(repository.directory(true)).thenReturn(cardDirectory());
        when(repository.insertItem(eq(MERCHANT), eq(VISA), anyString(), eq("request-a"), isNull(), eq("MERCHANT"),
                eq("APPLY"), eq(0L), eq(actor), any())).thenReturn(117L);
        AtomicReference<Audit> saved = new AtomicReference<>();
        when(repository.review(any(), eq(actor))).thenAnswer(invocation -> {
            Audit audit = invocation.getArgument(0);
            if (audit.command() != null) {
                saved.set(audit);
            }
            return 122L;
        });
        var first = governance.apply(scope, MERCHANT, new Apply("request-a", List.of(VISA, "card_brand:visa"), null));
        when(repository.replay(MERCHANT, "request-a"))
                .thenReturn(Optional.of(new Review(122, saved.get().hash(), saved.get().result())));
        when(repository.directory(true)).thenReturn(List.of());

        var replay = governance.apply(scope, MERCHANT, new Apply("request-a", List.of(VISA), null));

        assertThat(replay).isEqualTo(first);
        assertThat(replay.itemCount()).isEqualTo(1);
        assertThatThrownBy(() -> governance.apply(scope, MERCHANT,
                new Apply("request-a", List.of("METHOD:APPLE_PAY"), null)))
                .isInstanceOfSatisfying(ServiceException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode()));
        verify(repository, times(1)).directory(true);
        verify(events, times(1)).publishEvent(any(MerchantPaymentGovernanceEvent.class));
    }

    @Test
    void knownAdminCannotReviewUsingAnotherAccountOfSameNaturalPerson() {
        var maker = new Actor("ADMIN", 201, 8, "maker");
        var samePerson = new Actor("ADMIN", 202, 8, "another account");
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(), samePerson);
        Item item = item();
        when(repository.findItem(5L)).thenReturn(Optional.of(item));
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.lockItem(MERCHANT, 5L)).thenReturn(Optional.of(item));
        when(repository.submittedThisRound(item, samePerson.user())).thenReturn(true);

        assertThat(maker.account()).isNotEqualTo(samePerson.account());
        assertThat(new MerchantPaymentRepository(mock(NamedParameterJdbcTemplate.class))
                .submittedThisRound(item, samePerson.user())).isTrue();
        assertThatThrownBy(() -> governance.decide(scope, 5L, new Decision("cmd", "2", "APPROVE", null)))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verify(repository, never()).review(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void approvalKeepsActorBoundReceiptAndAdvancesRevisionOnce() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(),
                new Actor("ADMIN", 202, 9, "checker"));
        Item item = item();
        when(repository.findItem(5L)).thenReturn(Optional.of(item));
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.lockItem(MERCHANT, 5L)).thenReturn(Optional.of(item));
        when(repository.lockGrant(MERCHANT, VISA)).thenReturn(Optional.empty());
        when(repository.directory(true)).thenReturn(cardDirectory());
        when(repository.transitionItem(eq(item), eq("APPROVED"), any())).thenReturn(1);
        when(repository.advanceRevision(MERCHANT, 1L)).thenReturn(2L);
        when(repository.review(any(), any())).thenReturn(87L);

        var receipt = governance.decide(scope, 5L, new Decision("approve-request", "2", "APPROVE", null));

        assertThat(receipt.reviewRecordId()).isEqualTo("87");
        assertThat(receipt.authorizationVersion()).isEqualTo("1");
        assertThat(receipt.version()).isEqualTo("3");
        var order = inOrder(repository, events);
        order.verify(repository).lockMerchant(MERCHANT);
        order.verify(repository).lockItem(MERCHANT, 5L);
        order.verify(repository).review(any(), eq(scope.actor()));
        order.verify(repository).approve(eq(item), isNull(), eq(scope.actor()), eq(87L), any());
        order.verify(repository).advanceRevision(MERCHANT, 1L);
        order.verify(repository).transitionItem(eq(item), eq("APPROVED"), any());
        order.verify(events).publishEvent(argThat((Object value) ->
                value instanceof MerchantPaymentGovernanceEvent event && event.authorizationRevision() == 2L));
    }

    @Test
    void crossScopeFailsBeforeLockAndCannotReadRequestedMerchant() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.limited(Set.of("200046")),
                new Actor("ADMIN", 202, 9, "checker"));
        when(repository.findItem(5L)).thenReturn(Optional.of(item()));
        assertThatThrownBy(() -> governance.decide(scope, 5L, new Decision("cmd", "2", "APPROVE", null)))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verify(repository, never()).lockMerchant(anyString());
    }

    @Test
    void timeBoundaryAndTerminalLifecycleAreIndependent() {
        LocalDateTime effective = LocalDateTime.of(2026, 11, 20, 12, 0);
        LocalDateTime expiry = effective.plusHours(1);
        assertThat(MerchantPaymentGovernanceService.effectiveStatus("ACTIVE", effective, expiry, effective.minusNanos(1)))
                .isEqualTo("NOT_YET_EFFECTIVE");
        assertThat(MerchantPaymentGovernanceService.effectiveStatus("ACTIVE", effective, expiry, effective)).isEqualTo("ACTIVE");
        assertThat(MerchantPaymentGovernanceService.effectiveStatus("ACTIVE", effective, expiry, expiry)).isEqualTo("EXPIRED");
        assertThat(MerchantPaymentGovernanceService.effectiveStatus("SUSPENDED", effective, expiry, expiry)).isEqualTo("SUSPENDED");
        assertThat(MerchantPaymentGovernanceService.nextGrantStatus("SUSPENDED", "VALIDITY")).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> MerchantPaymentGovernanceService.nextGrantStatus("TERMINATED", "RESTORE"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void returnedApplicationFromDisabledMethodDoesNotEnterReviewAgain() {
        Item item = new Item(5, "application", MERCHANT, VISA, "APPLY", "RETURNED", 1, "MERCHANT", 7, 7,
                null, 0, 2, LocalDateTime.of(2026, 11, 20, 12, 0));
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.limited(Set.of(MERCHANT)),
                new Actor("MERCHANT", 26, 7, null));
        when(repository.findItem(5L)).thenReturn(Optional.of(item));
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.lockItem(MERCHANT, 5L)).thenReturn(Optional.of(item));
        when(repository.directory(true)).thenReturn(List.of(
                new DirectoryEntry("acquiring_payment_method", "BANK_CARD", "Card", "en-US", true),
                new DirectoryEntry("card_brand", "VISA", "Visa", "en-US", true),
                new DirectoryEntry("card_brand", "VISA", "Visa", "zh-CN", false)));

        assertThatThrownBy(() -> governance.changeApplication(scope, 5L,
                new Change("resubmit", "2", "已补充", null, null), true)).isInstanceOf(ServiceException.class);
        verify(repository, never()).resubmit(any(), any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void baselineMakerCannotReviewFromAnotherAccount() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(),
                new Actor("ADMIN", 204, 8, "second account"));
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.pendingBaseline(MERCHANT, "batch")).thenReturn(Optional.of(new ReviewedBaseline(8L, "{}")));

        assertThatThrownBy(() -> governance.approveBaseline(scope, MERCHANT, "batch",
                new BaselineApprove("approval", List.of(VISA), "证据已核对")))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verify(repository, never()).bindLegacyBaseline(anyString(), anyString(), anyLong(), any());
        verifyNoInteractions(events);
    }

    @Test
    void historicApprovalFreezesOnlyVerifiedScope() throws Exception {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(),
                new Actor("ADMIN", 204, 9, "independent checker"));
        var observed = LocalDateTime.of(2026, 11, 15, 11, 0);
        var json = new ObjectMapper().findAndRegisterModules();
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.legacyMerchant(MERCHANT)).thenReturn(Optional.of(
                new LegacyMerchant(null, null, observed.minusDays(1))));
        when(repository.pendingBaseline(MERCHANT, "batch")).thenReturn(Optional.of(new ReviewedBaseline(8L, "{}")));
        when(repository.candidateEvidence(MERCHANT, "batch")).thenReturn(List.of(
                json.writeValueAsString(new Evidence(VISA, "SUCCESSFUL_PAYMENT", "payment:1234", observed)),
                json.writeValueAsString(new Evidence("METHOD:APPLE_PAY", "HISTORICAL_CONFIG", "evidence:456", observed))));
        when(repository.review(eq("LEGACY_SCOPE"), any(), eq(scope.actor()))).thenReturn(85L, 86L);

        var approval = governance.approveBaseline(scope, MERCHANT, "batch",
                new BaselineApprove("approval", List.of(VISA), "仅核实 Visa"));

        assertThat(approval.itemCount()).isEqualTo(1);
        assertThat(approval.unresolvedCount()).isEqualTo(1);
        verify(repository).bindLegacyBaseline(eq(MERCHANT), eq("batch"), eq(1L), any());
        verify(repository).review(eq("LEGACY_SCOPE"),
                argThat(audit -> "BASELINE_APPROVE".equals(audit.action()) && VISA.equals(audit.targetKey())),
                eq(scope.actor()));
        verify(repository, never()).review(eq("LEGACY_SCOPE"),
                argThat(audit -> "BASELINE_APPROVE".equals(audit.action()) && "METHOD:APPLE_PAY".equals(audit.targetKey())),
                eq(scope.actor()));
    }

    @Test
    void frozenScopeCanBeSuspendedWithoutFabricatingAnApproval() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(),
                new Actor("ADMIN", 204, 9, "authorized operator"));
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(2L));
        when(repository.legacyMerchant(MERCHANT)).thenReturn(Optional.of(new LegacyMerchant(
                "LEGACY_OBSERVE", "batch", LocalDateTime.of(2026, 11, 20, 12, 0))));
        when(repository.frozenMethods(MERCHANT)).thenReturn(Set.of(VISA));
        when(repository.review(any(), eq(scope.actor()))).thenReturn(17L);
        when(repository.advanceRevision(MERCHANT, 2L)).thenReturn(3L);

        var result = governance.changeGrant(scope, MERCHANT, VISA, "SUSPEND",
                new Change("cmd-suspend", "0", "停止新收款", null, null));

        assertThat(result.status()).isEqualTo("SUSPENDED");
        assertThat(result.authorizationVersion()).isEqualTo("1");
        assertThat(result.reviewRecordId()).isEqualTo("17");
        verify(repository).restrictLegacy(eq(MERCHANT), eq(VISA), eq("batch"), eq("SUSPENDED"), eq(17L), any());
        verify(repository, never()).approve(any(), any(), any(), anyLong(), any());
        assertThatThrownBy(() -> governance.changeGrant(scope, MERCHANT, "METHOD:APPLE_PAY", "SUSPEND",
                new Change("cmd-other", "0", "停止新收款", null, null))).isInstanceOf(ServiceException.class);
    }

    @Test
    void historicalEvidenceOutsideTheClaimedCutoffCannotCreateABatch() {
        var scope = new MerchantPaymentGovernanceService.Scope(AdminMerchantDataScope.all(),
                new Actor("ADMIN", 204, 9, "maker"));
        var cutoff = LocalDateTime.of(2026, 11, 15, 11, 0);
        when(repository.lockMerchant(MERCHANT)).thenReturn(Optional.of(1L));
        when(repository.legacyMerchant(MERCHANT)).thenReturn(Optional.of(
                new LegacyMerchant(null, null, cutoff.minusDays(1))));

        assertThatThrownBy(() -> governance.submitBaseline(scope, MERCHANT, new BaselineSubmit(
                "request", cutoff, List.of(new Evidence(VISA, "HISTORICAL_CONFIG", "config:17", cutoff.plusDays(1))),
                "提交镜像")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("冻结截止时间");
        verify(repository, never()).review(anyString(), any(), any());
    }

    private static Item item() {
        return new Item(5, "application", MERCHANT, VISA, "APPLY", "PENDING_REVIEW", 1, "ADMIN_PROXY",
                8, 8, "reason", 0, 2, LocalDateTime.of(2026, 11, 20, 12, 0));
    }

    private static List<DirectoryEntry> cardDirectory() {
        return List.of(new DirectoryEntry("acquiring_payment_method", "BANK_CARD", "Card", "en-US", true),
                new DirectoryEntry("card_brand", "VISA", "Visa", "en-US", true));
    }
}

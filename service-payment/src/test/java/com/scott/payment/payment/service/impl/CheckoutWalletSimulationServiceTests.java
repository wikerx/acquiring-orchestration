package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.api.HostedWalletSimulator;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.config.MerchantNotificationProperties;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.PaymentCheckoutAttemptMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheckoutWalletSimulationServiceTests {
    private static final String ATTEMPT_ID = "2611221906121070000011";
    private static final String CHANNEL_ORDER = "DEV-IW-" + ATTEMPT_ID;
    private static final String CHANNEL_TRANSACTION = "CMT20261122190612115010008WPLF";
    private static final String MERCHANT = "200047";
    private static final String MID = "VEXRA-HOSTED-TEST-200047";
    private static final String TRANSACTION_ID = "2611241101233460000021";
    private static final String OPERATION_KEY = "2611241101233460000022";
    private static final String OPERATION_ID = "OP" + OPERATION_KEY;

    private final HostedWalletSimulator simulator = mock(HostedWalletSimulator.class);
    private final PaymentChannelRouteService routes = mock(PaymentChannelRouteService.class);
    private final PaymentExchangeRateService exchangeRates = mock(PaymentExchangeRateService.class);
    private final IsoDictionaryService isoDictionary = mock(IsoDictionaryService.class);
    private final PaymentCheckoutAttemptMapper attempts = mock(PaymentCheckoutAttemptMapper.class);
    private final PaymentCheckoutSessionMapper sessions = mock(PaymentCheckoutSessionMapper.class);
    private final GlobalIdGenerator ids = mock(GlobalIdGenerator.class);
    private final TransactionMerchantNotificationMapper notifications = mock(TransactionMerchantNotificationMapper.class);
    private final MerchantNotificationInitialDeliveryService delivery = mock(MerchantNotificationInitialDeliveryService.class);
    private final TransactionRecordService records = mock(TransactionRecordService.class);
    private final TransactionLifecycleEventService lifecycleEvents = mock(TransactionLifecycleEventService.class);
    private final TransactionIdempotencyService idempotency = mock(TransactionIdempotencyService.class);
    private final TransactionChannelInteractionLogMapper interactionLogs = mock(TransactionChannelInteractionLogMapper.class);
    private final PaymentCheckoutProperties properties = new PaymentCheckoutProperties();
    private final SystemConfigReadService configs = mock(SystemConfigReadService.class);
    private final com.scott.payment.payment.service.link.PaymentLinkCheckoutGuard linkGuard =
            mock(com.scott.payment.payment.service.link.PaymentLinkCheckoutGuard.class);
    private final CheckoutWalletSimulationService service = new CheckoutWalletSimulationService(
            routes, exchangeRates, isoDictionary, Optional.of(simulator), attempts, sessions, ids,
            properties, configs,
            TransactionOperations.withoutTransaction(), notifications, delivery,
            new MerchantNotificationProperties(), new CheckoutWalletTransactionLedger(ids, records, idempotency,
                    interactionLogs, lifecycleEvents), linkGuard);

    @BeforeEach
    void setUpCurrencyDictionary() {
        when(isoDictionary.getCurrency(anyString())).thenAnswer(invocation -> currency(invocation.getArgument(0)));
        when(isoDictionary.isCurrencyFractionValid(any(), anyString())).thenAnswer(invocation -> {
            BigDecimal amount = invocation.getArgument(0);
            return currency(invocation.getArgument(1))
                    .map(value -> amount.stripTrailingZeros().scale() <= value.defaultFractionDigits())
                    .orElse(false);
        });
    }

    @Test
    void sendsTwoDecimalUsdAmountWithoutChangingItsValue() {
        var route = new PaymentRouteResultDTO();
        route.setRouted(true);
        route.setChannelCode("SCOTT_CHANNEL_A");
        route.setRequestUrl("http://127.0.0.1:58090");
        route.setMidNo(MID);
        route.setMidConfigId(17L);
        route.setMetadataValues(Map.of("apiKey", "test-only"));
        when(routes.route(any())).thenReturn(route);
        when(simulator.supportsRoute(route.getChannelCode(), route.getRequestUrl(), MID)).thenReturn(true);
        when(simulator.prepare(any(), anyString(), anyString())).thenReturn(
                new HostedWalletSimulator.Result(HostedWalletSimulator.State.READY, CHANNEL_TRANSACTION,
                        "http://127.0.0.1:58090/simulate/hosted-payins/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", null));

        var session = session(MERCHANT, "7.000000");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(ids.nextId()).thenReturn(ATTEMPT_ID);
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenReturn(attempt(MERCHANT, null));

        assertThat(service.execute(session, "APPLE_PAY", "PREPARE")).containsEntry("state", "READY");
        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        var returnUrl = ArgumentCaptor.forClass(String.class);
        var routeCommand = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        verify(simulator).prepare(order.capture(), returnUrl.capture(), anyString());
        verify(routes).route(routeCommand.capture());
        assertThat(order.getValue().amount()).isEqualTo(new BigDecimal("7.00"));
        assertThat(order.getValue().currency()).isEqualTo("USD");
        assertThat(order.getValue().channelMerchantId()).isEqualTo(MID);
        assertThat(order.getValue().number()).isEqualTo(CHANNEL_ORDER);
        assertThat(routeCommand.getValue().getMerchantId()).isEqualTo(MERCHANT);
        assertThat(routeCommand.getValue().getCurrency()).isEqualTo("USD");
        assertThat(routeCommand.getValue().getBillingCardHolderInfo().getCountry()).isEqualTo("USA");
        assertThat(returnUrl.getValue())
                .startsWith("http://127.0.0.1:8000/checkout/api/v1/wallet/authorization/return?returnToken=")
                .doesNotContain("wallet-return");
    }

    @Test
    void sendsDirectEurWithoutUnnecessaryConversion() {
        PaymentRouteResultDTO route = route(MID, "EUR", "EUR", false);
        stubPreparationRoute(route);
        PaymentCheckoutSessionDO session = session(MERCHANT, "7.25", "EUR");
        AtomicReference<PaymentCheckoutAttemptDO> inserted = stubInsertedAttempt(session);

        assertThat(service.execute(session, "APPLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator).prepare(order.capture(), anyString(), anyString());
        assertThat(order.getValue().amount()).isEqualByComparingTo("7.25");
        assertThat(order.getValue().currency()).isEqualTo("EUR");
        assertThat(inserted.get().getLabelCurrency()).isEqualTo("EUR");
        assertThat(inserted.get().getChannelRequestCurrency()).isEqualTo("EUR");
        verifyNoInteractions(exchangeRates);
    }

    @Test
    void linkWalletOpenStoresBillingOnceAndPassesItToTheTransaction() {
        var session = session(MERCHANT, "7.25");
        var issuedAttempt = stubInsertedAttempt(session);
        stubCoreActivation(session, issuedAttempt);
        stubPreparationRoute(route(MID, "USD", "USD", false));
        stubRestoredRoute();
        when(sessions.saveWalletBilling(eq(session.getCheckoutSessionId()), anyString(), eq(0), any()))
                .thenReturn(1);
        var billing = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        billing.setFirstName("QA");
        billing.setLastName("Payer");
        billing.setEmail("payer@example.test");
        billing.setCountry("CAN");

        service.execute(session, "GOOGLE_PAY", "PREPARE", true, billing);
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "GOOGLE_PAY"))
                .thenReturn(issuedAttempt.get());
        service.execute(session, "GOOGLE_PAY", "OPEN", true, billing);
        service.execute(session, "GOOGLE_PAY", "OPEN", true, billing);

        var recorded = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        verify(records).recordInitialTransaction(recorded.capture(), any(), any(), any(), eq(null), eq(2));
        assertThat(recorded.getValue().getBillingCardHolderInfo().getCountry()).isEqualTo("CAN");
        assertThat(recorded.getValue().getBillingCardHolderInfo().getEmail()).isEqualTo("payer@example.test");
        verify(sessions).saveWalletBilling(eq(session.getCheckoutSessionId()), anyString(), eq(0), any());
    }

    @Test
    void stoppedLinkCannotPrepareANewWalletAttempt() {
        stubPreparationRoute(route(MID, "USD", "USD", false));
        var session = session(MERCHANT, "7.25");
        doThrow(new com.scott.payment.component.core.exception.ServiceException(
                com.scott.payment.component.core.enums.ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED))
                .when(linkGuard).requireNewSubmissionAllowed(eq(session.getCheckoutSessionId()), any());

        assertThatThrownBy(() -> service.execute(session, "APPLE_PAY", "PREPARE", true, null))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class);
        verifyNoInteractions(attempts, sessions);
        verify(simulator, never()).prepare(any(), anyString(), anyString());
    }

    @Test
    void sendsDirectThreeDecimalCurrencyUsingItsIsoExponent() {
        PaymentRouteResultDTO route = route(MID, "KWD", "KWD", false);
        stubPreparationRoute(route);
        PaymentCheckoutSessionDO session = session(MERCHANT, "2.345000", "KWD");
        AtomicReference<PaymentCheckoutAttemptDO> inserted = stubInsertedAttempt(session);

        assertThat(service.execute(session, "GOOGLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator).prepare(order.capture(), anyString(), anyString());
        assertThat(order.getValue().amount()).isEqualTo(new BigDecimal("2.345"));
        assertThat(order.getValue().currency()).isEqualTo("KWD");
        assertThat(inserted.get().getChannelRequestAmount()).isEqualTo(new BigDecimal("2.345"));
        verifyNoInteractions(exchangeRates);
    }

    @Test
    void convertsEurToUsdThroughEdcAndFreezesTheChannelAmountOnTheAttempt() {
        PaymentRouteResultDTO route = route(MID, "EUR", "USD", true);
        stubPreparationRoute(route);
        PaymentCheckoutSessionDO session = session(MERCHANT, "10.00", "EUR");
        AtomicReference<PaymentCheckoutAttemptDO> inserted = stubInsertedAttempt(session);
        when(exchangeRates.findTransactionRate(eq("EUR"), eq("USD"), any()))
                .thenReturn(Optional.of(rate("EUR", "USD", "0.75")));

        assertThat(service.execute(session, "GOOGLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator).prepare(order.capture(), anyString(), anyString());
        assertThat(order.getValue().amount()).isEqualByComparingTo("7.50");
        assertThat(order.getValue().currency()).isEqualTo("USD");
        assertThat(inserted.get().getLabelAmount()).isEqualByComparingTo("10.00");
        assertThat(inserted.get().getLabelCurrency()).isEqualTo("EUR");
        assertThat(inserted.get().getChannelRequestAmount()).isEqualByComparingTo("7.50");
        assertThat(inserted.get().getChannelRequestCurrency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @CsvSource({
            "GOOGLE_PAY, CAD, 101.71, USD, 0.71231, 72.45",
            "APPLE_PAY, HKD, 100.00, EUR, 0.12, 12.00",
            "APPLE_PAY, USD, 7.00, USD, , 7.00",
            "GOOGLE_PAY, JPY, 151, JPY, , 151",
            "APPLE_PAY, KWD, 2.345, KWD, , 2.345",
            "GOOGLE_PAY, CLF, 1.2345, CLF, , 1.2345"
    })
    void opensAndReopensTheSameWalletWithItsFrozenAmountAfterDatabaseRead(
            String method, String labelCurrency, String labelAmount, String channelCurrency,
            String exchangeRate, String channelAmount) {
        boolean edc = !labelCurrency.equals(channelCurrency);
        PaymentRouteResultDTO route = route(MID, labelCurrency, channelCurrency, edc);
        stubPreparationRoute(route);
        when(routes.restore("SCOTT_CHANNEL_A", null, 17L, null)).thenReturn(route(MID));
        PaymentCheckoutSessionDO session = session(MERCHANT, labelAmount, labelCurrency);
        AtomicReference<PaymentCheckoutAttemptDO> inserted = stubInsertedAttempt(session);
        if (edc) {
            when(exchangeRates.findTransactionRate(eq(labelCurrency), eq(channelCurrency), any()))
                    .thenReturn(Optional.of(rate(labelCurrency, channelCurrency, exchangeRate)));
        }
        stubCoreActivation(session, inserted);

        Map<String, String> prepared = service.execute(session, method, "PREPARE");
        assertThat(prepared).containsKey("redirectUrl").containsEntry("state", "READY");
        verifyNoInteractions(records);
        PaymentCheckoutAttemptDO persisted = inserted.get();
        // 回读 DECIMAL(20,6) 时，JDBC 会带回数据库补齐的尾随零。
        persisted.setChannelRequestAmount(persisted.getChannelRequestAmount().setScale(6));
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), method)).thenReturn(persisted);

        assertThat(service.execute(session, method, "OPEN")).isEqualTo(prepared);
        assertThat(service.execute(session, method, "OPEN")).isEqualTo(prepared);

        var orders = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator, times(3)).prepare(orders.capture(), anyString(), anyString());
        HostedWalletSimulator.Order original = orders.getAllValues().get(0);
        assertThat(original.amount().toPlainString()).isEqualTo(channelAmount);
        assertThat(original.currency()).isEqualTo(channelCurrency);
        assertThat(original.number()).isEqualTo(CHANNEL_ORDER);
        assertThat(orders.getAllValues()).containsExactly(original, original, original);
        assertThat(persisted.getChannelTransactionId()).isEqualTo(CHANNEL_TRANSACTION);
        assertThat(persisted.getTransactionId()).isEqualTo(TRANSACTION_ID);
        var coreCommand = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        var coreRoute = ArgumentCaptor.forClass(PaymentRouteResultDTO.class);
        var coreResult = ArgumentCaptor.forClass(PaymentCreateResultDTO.class);
        var evidence = ArgumentCaptor.forClass(PaymentChannelInvokeResultDTO.class);
        verify(records).recordInitialTransaction(coreCommand.capture(), coreRoute.capture(), evidence.capture(),
                coreResult.capture(), eq(null), eq(new BigDecimal(channelAmount).scale()));
        assertThat(coreResult.getValue().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(coreResult.getValue().getStatus()).isEqualTo("PROCESSING");
        assertThat(coreRoute.getValue().getMetadataValues()).isEmpty();
        assertThat(evidence.getValue().getChannelRequest().getChannelTransactionId()).isEqualTo(CHANNEL_TRANSACTION);
        assertThat(evidence.getValue().getChannelResponse()).isNull();
        assertThat(evidence.getValue().getRequestStatus()).isEqualTo("SENT");
        assertThat(coreCommand.getValue().getLabelAmount()).isEqualByComparingTo(labelAmount);
        assertThat(coreCommand.getValue().getTransactionAmount()).isEqualByComparingTo(channelAmount);
        verify(attempts).insert(any(PaymentCheckoutAttemptDO.class));
        verify(sessions).markSubmittedCas(eq(session.getCheckoutSessionId()), eq("PROCESSING"), eq("WAITING_CHANNEL"),
                eq(TRANSACTION_ID), eq(OPERATION_ID), any(), eq(ATTEMPT_ID), eq(0), any());
        if (edc) {
            verify(exchangeRates).findTransactionRate(eq(labelCurrency), eq(channelCurrency), any());
        } else {
            verifyNoInteractions(exchangeRates);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "succeeded, , SUCCESS, SUCCEEDED",
            "failed, PAYMENT_DECLINED, FAILED, FAILED",
            "failed, PAYMENT_EXPIRED, EXPIRED, FAILED",
            "canceled, , CANCELLED, ABANDONED"
    })
    void verifiedWalletCallbackFinalizesTheSameCorePaymentExactlyOnce(
            String callbackStatus, String responseCode, String walletState, String attemptStatus) {
        var session = session(MERCHANT, "7.00");
        session.setMerchantNotifyUrl("http://127.0.0.1:58091/merchant/notify");
        var inserted = stubInsertedAttempt(session);
        stubCoreActivation(session, inserted);
        stubRestoredRoute();
        stubPreparationRoute(route(MID));
        service.execute(session, "APPLE_PAY", "PREPARE");
        PaymentCheckoutAttemptDO persisted = inserted.get();
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "APPLE_PAY")).thenReturn(persisted);
        service.execute(session, "APPLE_PAY", "OPEN");
        when(records.completeInitialChannelResultAndReport(any(), any(), any(), any(), eq(null), eq(2))).thenReturn(true);
        when(attempts.markResultCas(eq(ATTEMPT_ID), eq(attemptStatus), eq("RESULT_RENDERED"),
                eq(walletState), any(), any(), any(), any(), any(), any(), eq(0), any())).thenAnswer(invocation -> {
                    persisted.setChannelStatus(walletState);
                    return 1;
                });
        boolean success = "SUCCESS".equals(walletState);
        if (success) {
            when(sessions.markSucceededCas(eq(session.getCheckoutSessionId()), eq("RESULT_RENDERED"),
                    eq(ATTEMPT_ID), eq(TRANSACTION_ID), eq(OPERATION_ID), eq(persisted.getTransactionDateTime()),
                    eq(0), any())).thenReturn(1);
        } else {
            when(sessions.markFailedCas(eq(session.getCheckoutSessionId()), eq("FAILED"), eq("RESULT_RENDERED"),
                    eq(0), any())).thenReturn(1);
        }

        var callback = callback(callbackStatus, MID);
        callback.setChannelResponseCode(responseCode);
        assertThat(service.acceptCallback(callback)).isTrue();
        assertThat(service.acceptCallback(callback)).isTrue();
        assertThat(service.acceptCallback(callback(success ? "failed" : "succeeded", MID))).isTrue();

        var command = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        var outcome = ArgumentCaptor.forClass(PaymentCreateResultDTO.class);
        verify(records).completeInitialChannelResultAndReport(command.capture(), any(), any(), outcome.capture(),
                eq(null), eq(2));
        assertThat(command.getValue().getCallbackUrl()).isEqualTo(session.getMerchantNotifyUrl());
        assertThat(outcome.getValue().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(outcome.getValue().getStatus()).isEqualTo(success ? "SUCCESS" : "FAILED");
        verify(lifecycleEvents).saveStatusChanged(eq(TRANSACTION_ID), eq(persisted.getOperationId()), eq(MERCHANT),
                eq(persisted.getMerchantOrderNo()), eq("PAYMENT"), eq(success ? "SUCCESS" : "FAILED"),
                eq(persisted.getTransactionDateTime()));
        verifyNoInteractions(notifications, delivery);
    }

    @Test
    void channelQueryFailureKeepsTheBoundWalletPaymentProcessing() {
        var session = session(MERCHANT, "7.00");
        var inserted = stubInsertedAttempt(session);
        stubCoreActivation(session, inserted);
        stubRestoredRoute();
        stubPreparationRoute(route(MID));
        service.execute(session, "GOOGLE_PAY", "PREPARE");
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "GOOGLE_PAY"))
                .thenReturn(inserted.get());
        service.execute(session, "GOOGLE_PAY", "OPEN");
        when(simulator.query(any())).thenThrow(new IllegalStateException("simulated query timeout"));

        assertThat(service.execute(session, "GOOGLE_PAY", "STATUS")).containsEntry("state", "PROCESSING");
        assertThat(inserted.get().getTransactionId()).isEqualTo(TRANSACTION_ID);
        verify(records, never()).completeInitialChannelResultAndReport(any(), any(), any(), any(), any(), eq(2));
        verify(simulator, never()).submitApproved(any());
        verifyNoInteractions(notifications, delivery);
    }

    @Test
    void walletCannotCreateACoreOrderWhileAnotherPaymentOwnsTheMerchantOrder() {
        var session = session(MERCHANT, "7.00");
        var inserted = stubInsertedAttempt(session);
        stubCoreActivation(session, inserted);
        var flow = new TransactionIdempotencyDO();
        flow.setTransactionStatus("SUCCESS");
        when(idempotency.tryBegin(any())).thenReturn(false);
        when(idempotency.buildMerchantOrderFlowKey(MERCHANT, session.getMerchantOrderNo())).thenReturn("order-flow");
        when(idempotency.find("MERCHANT_ORDER_FLOW", "order-flow")).thenReturn(Optional.of(flow));
        stubRestoredRoute();
        stubPreparationRoute(route(MID));
        service.execute(session, "GOOGLE_PAY", "PREPARE");
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "GOOGLE_PAY"))
                .thenReturn(inserted.get());

        assertThatThrownBy(() -> service.execute(session, "GOOGLE_PAY", "OPEN"))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(records, notifications, delivery);
        assertThat(session.getCheckoutStatus()).isEqualTo("PENDING");
    }

    private void stubCoreActivation(PaymentCheckoutSessionDO session, AtomicReference<PaymentCheckoutAttemptDO> inserted) {
        when(ids.nextId()).thenReturn(ATTEMPT_ID, TRANSACTION_ID, OPERATION_KEY);
        when(idempotency.tryBegin(any())).thenReturn(true);
        when(attempts.bindWalletCoreIdentity(eq(ATTEMPT_ID), eq(TRANSACTION_ID), eq(OPERATION_ID),
                anyString(), eq(0), any())).thenAnswer(invocation -> {
                    var attempt = inserted.get();
                    attempt.setTransactionId(TRANSACTION_ID);
                    attempt.setOperationId(OPERATION_ID);
                    attempt.setChannelRequestId(invocation.getArgument(3));
                    return 1;
                });
        when(sessions.markSubmittedCas(eq(session.getCheckoutSessionId()), eq("PROCESSING"), eq("WAITING_CHANNEL"),
                eq(TRANSACTION_ID), eq(OPERATION_ID), any(), eq(ATTEMPT_ID), eq(0), any())).thenAnswer(invocation -> {
                    session.setLastAttemptId(ATTEMPT_ID);
                    session.setCheckoutStatus("PROCESSING");
                    return 1;
                });
    }

    @Test
    void rejectsStoredAmountWithNonZeroExcessPrecisionWithoutSendingItToTheChannel() {
        PaymentCheckoutSessionDO session = session(MERCHANT, "7.00");
        PaymentCheckoutAttemptDO persisted = attempt(MERCHANT, null);
        persisted.setChannelRequestAmount(new BigDecimal("7.001000"));
        stubRestoredRoute();
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "APPLE_PAY")).thenReturn(persisted);

        assertThatThrownBy(() -> service.execute(session, "APPLE_PAY", "OPEN"))
                .isInstanceOf(ServiceException.class);
        verify(simulator, never()).prepare(any(), anyString(), anyString());
    }

    @Test
    void roundsEdcOnlyOnceAtTheTargetCurrencyExponent() {
        PaymentRouteResultDTO route = route(MID, "EUR", "JPY", true);
        stubPreparationRoute(route);
        PaymentCheckoutSessionDO session = session(MERCHANT, "1.50", "EUR");
        stubInsertedAttempt(session);
        when(exchangeRates.findTransactionRate(eq("EUR"), eq("JPY"), any()))
                .thenReturn(Optional.of(rate("EUR", "JPY", "100.5")));

        assertThat(service.execute(session, "APPLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator).prepare(order.capture(), anyString(), anyString());
        assertThat(order.getValue().amount()).isEqualByComparingTo("151");
        assertThat(order.getValue().currency()).isEqualTo("JPY");
    }

    @Test
    void appliesTheSimulatorLimitAfterEdcInsteadOfRejectingTheLabelCurrencyAmount() {
        PaymentRouteResultDTO route = route(MID, "JPY", "USD", true);
        stubPreparationRoute(route);
        PaymentCheckoutSessionDO session = session(MERCHANT, "50000", "JPY");
        stubInsertedAttempt(session);
        when(exchangeRates.findTransactionRate(eq("JPY"), eq("USD"), any()))
                .thenReturn(Optional.of(rate("JPY", "USD", "0.0067")));

        assertThat(service.supports(session, "GOOGLE_PAY")).isTrue();
        assertThat(service.execute(session, "GOOGLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var order = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        verify(simulator).prepare(order.capture(), anyString(), anyString());
        assertThat(order.getValue().amount()).isEqualTo(new BigDecimal("335.00"));
        assertThat(order.getValue().currency()).isEqualTo("USD");
    }

    @Test
    void missingEdcRateFailsClosedBeforeCreatingAnAttemptOrCallingTheChannel() {
        PaymentRouteResultDTO route = route(MID, "EUR", "USD", true);
        when(routes.route(any())).thenReturn(route);
        when(simulator.supportsRoute(route.getChannelCode(), route.getRequestUrl(), MID)).thenReturn(true);
        PaymentCheckoutSessionDO session = session(MERCHANT, "10.00", "EUR");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(exchangeRates.findTransactionRate(eq("EUR"), eq("USD"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(session, "APPLE_PAY", "PREPARE"))
                .isInstanceOf(ServiceException.class);

        verify(attempts, never()).insert(any(PaymentCheckoutAttemptDO.class));
        verify(simulator, never()).prepare(any(), anyString(), anyString());
    }

    @Test
    void returnsFromChannelThroughTheSamePrivateLanHostAsCheckout() {
        var route = route(MID);
        when(routes.route(any())).thenReturn(route);
        when(simulator.supportsRoute(route.getChannelCode(), route.getRequestUrl(), MID)).thenReturn(true);
        when(simulator.prepare(any(), anyString(), anyString())).thenReturn(
                new HostedWalletSimulator.Result(HostedWalletSimulator.State.READY, CHANNEL_TRANSACTION,
                        "http://127.0.0.1:58090/simulate/hosted-payins/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", null));

        var session = session(MERCHANT, "7.00");
        session.setCheckoutDomain("http://192.168.0.110:5175");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(ids.nextId()).thenReturn(ATTEMPT_ID);
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenReturn(attempt(MERCHANT, null));

        assertThat(service.execute(session, "APPLE_PAY", "PREPARE")).containsEntry("state", "READY");

        var returnUrl = ArgumentCaptor.forClass(String.class);
        verify(simulator).prepare(any(), returnUrl.capture(), anyString());
        assertThat(returnUrl.getValue())
                .startsWith("http://192.168.0.110:8000/checkout/api/v1/wallet/authorization/return?returnToken=");
    }

    @Test
    void remoteWalletFreezesChannelAndGatewayAddressesAcrossRetries() {
        var route = route(MID);
        route.setRequestUrl("https://channel.example.test:8443/mock");
        when(configs.findEnabledValue("platform.gateway.base-url"))
                .thenReturn(Optional.of("https://callback.example.test/vexra/"));
        properties.getWalletSimulation().setBrowserReturnBaseUrl("https://browser.example.test/gateway/");
        var session = session(MERCHANT, "7.00");
        var inserted = stubInsertedAttempt(session);
        stubCoreActivation(session, inserted);
        stubPreparationRoute(route);

        service.execute(session, "GOOGLE_PAY", "PREPARE");
        when(attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), "GOOGLE_PAY"))
                .thenReturn(inserted.get());
        var replacement = route(MID);
        replacement.setRequestUrl("https://other-channel.example.test");
        when(routes.restore("SCOTT_CHANNEL_A", null, 17L, null)).thenReturn(replacement);
        when(simulator.supportsRoute("SCOTT_CHANNEL_A", replacement.getRequestUrl(), MID)).thenReturn(true);
        when(configs.findEnabledValue("platform.gateway.base-url"))
                .thenReturn(Optional.of("https://different-gateway.example.test"));
        properties.getWalletSimulation().setBrowserReturnBaseUrl(null);
        service.execute(session, "GOOGLE_PAY", "OPEN");

        var orders = ArgumentCaptor.forClass(HostedWalletSimulator.Order.class);
        var returnUrls = ArgumentCaptor.forClass(String.class);
        var callbackUrls = ArgumentCaptor.forClass(String.class);
        verify(simulator, times(2)).prepare(orders.capture(), returnUrls.capture(), callbackUrls.capture());
        assertThat(orders.getAllValues()).extracting(HostedWalletSimulator.Order::channelBaseUrl)
                .containsExactly(route.getRequestUrl(), route.getRequestUrl());
        assertThat(returnUrls.getAllValues()).containsOnly(returnUrls.getValue());
        assertThat(returnUrls.getValue())
                .startsWith("https://browser.example.test/gateway/checkout/api/v1/wallet/authorization/return?returnToken=");
        assertThat(callbackUrls.getAllValues()).containsOnly(
                "https://callback.example.test/vexra/channel/v1/callbacks/SCOTT_CHANNEL_A");
    }

    @Test
    void remoteWalletRejectsLoopbackCallbackBeforeCreatingAttempt() {
        var route = route(MID);
        route.setRequestUrl("https://channel.example.test");
        var session = session(MERCHANT, "7.00");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        stubPreparationRoute(route);

        assertThatThrownBy(() -> service.execute(session, "GOOGLE_PAY", "PREPARE"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("reachable platform gateway base URL");
        verify(attempts, never()).insert(any(PaymentCheckoutAttemptDO.class));
        verify(simulator, never()).prepare(any(), anyString(), anyString());
    }

    @Test
    void doesNotRouteUnsupportedFractionalCents() {
        assertThatThrownBy(() -> service.execute(session(MERCHANT, "7.000001"), "GOOGLE_PAY", "PREPARE"))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(routes, simulator, attempts);
    }

    @Test
    void allowsPrivateLanDevelopmentOriginButRejectsPublicOrWrongPort() {
        PaymentCheckoutSessionDO session = session(MERCHANT, "7.00");

        session.setCheckoutDomain("http://192.168.0.110:5175");
        assertThat(service.supports(session, "APPLE_PAY")).isTrue();

        session.setCheckoutDomain("http://10.20.30.40:5175/");
        assertThat(service.supports(session, "GOOGLE_PAY")).isTrue();

        session.setCheckoutDomain("http://192.168.0.110:5174");
        assertThat(service.supports(session, "APPLE_PAY")).isFalse();

        session.setCheckoutDomain("http://8.8.8.8:5175");
        assertThat(service.supports(session, "APPLE_PAY")).isFalse();
    }

    @Test
    void verifiedScottCallbackWithoutChannelEventTypeAcceptsExistingTerminalWithoutReenqueueing() {
        var session = session(MERCHANT, "7.00");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenReturn(attempt(MERCHANT, "SUCCESS"));
        stubRestoredRoute();

        var command = new TransactionChannelCallbackCommandDTO();
        command.setChannelCode("SCOTT_CHANNEL_A");
        command.setChannelCredentialHint(MID);
        command.setSignatureValid(true);
        command.setIpAllowed(true);
        command.setCallbackKind("FINANCIAL_TRANSACTION");
        command.setExtension(Map.of("merchantOrderNo", CHANNEL_ORDER, "paymentMethod", "APPLE_PAY"));
        command.setChannelTransactionId(CHANNEL_TRANSACTION);
        command.setCurrency("USD");
        command.setAmount(new BigDecimal("7.00"));
        command.setRawChannelStatus("succeeded");

        assertThat(service.acceptCallback(command)).isTrue();
        verifyNoInteractions(notifications);

        command.setSignatureValid(false);
        assertThatThrownBy(() -> service.acceptCallback(command)).isInstanceOf(ServiceException.class);
        command.setSignatureValid(true);
        command.setChannelCredentialHint("ORDINARY-MID");
        assertThatThrownBy(() -> service.acceptCallback(command)).isInstanceOf(ServiceException.class);
        command.setExtension(Map.of("merchantOrderNo", "UNRELATED", "paymentMethod", "APPLE_PAY"));
        assertThat(service.acceptCallback(command)).isFalse();
    }

    @Test
    void successfulConfiguredMerchantCallbackUpdatesCheckoutAndUsesItsNotificationUrl() {
        var session = session(MERCHANT, "7.00");
        session.setLastAttemptId(ATTEMPT_ID);
        session.setMerchantNotifyUrl("http://127.0.0.1:58091/merchant-200047/notify?token=masked");
        PaymentCheckoutAttemptDO attempt = attempt(MERCHANT, null);
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenReturn(attempt);
        when(attempts.markResultCas(eq(ATTEMPT_ID), eq("SUCCEEDED"), eq("RESULT_RENDERED"),
                eq("SUCCESS"), any(), any(), any(), any(), any(), any(), eq(0), any())).thenReturn(1);
        when(sessions.markSucceededCas(eq(session.getCheckoutSessionId()), eq("RESULT_RENDERED"),
                eq(ATTEMPT_ID), any(), any(), eq(attempt.getTransactionDateTime()), eq(0), any())).thenReturn(1);
        when(notifications.insertLogical(any())).thenReturn(1);
        stubRestoredRoute();

        assertThat(service.acceptCallback(callback("succeeded", MID))).isTrue();

        ArgumentCaptor<TransactionMerchantNotificationDO> notification =
                ArgumentCaptor.forClass(TransactionMerchantNotificationDO.class);
        verify(notifications).insertLogical(notification.capture());
        assertThat(notification.getValue().getMerchantId()).isEqualTo(MERCHANT);
        assertThat(notification.getValue().getCallbackUrl()).isEqualTo(session.getMerchantNotifyUrl());
        assertThat(notification.getValue().getTargetUrlMasked())
                .isEqualTo("http://127.0.0.1:58091/merchant-200047/notify?***");
        assertThat(notification.getValue().getPayloadJson()).contains(MERCHANT, "APPLE_PAY", "SUCCESS");
        verify(delivery).schedule(eq(notification.getValue()), eq(0), any());
    }

    @Test
    void edcCallbackMatchesTheFrozenChannelAmountButNotifiesTheMerchantWithTheLabelAmount() {
        PaymentCheckoutSessionDO session = session(MERCHANT, "10.00", "EUR");
        session.setLastAttemptId(ATTEMPT_ID);
        session.setMerchantNotifyUrl("http://127.0.0.1:58091/merchant-200047/notify");
        PaymentCheckoutAttemptDO attempt = attempt(MERCHANT, null);
        attempt.setLabelAmount(new BigDecimal("10.00"));
        attempt.setLabelCurrency("EUR");
        attempt.setChannelRequestAmount(new BigDecimal("7.50"));
        attempt.setChannelRequestCurrency("USD");
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenReturn(attempt);
        when(attempts.markResultCas(eq(ATTEMPT_ID), eq("SUCCEEDED"), eq("RESULT_RENDERED"),
                eq("SUCCESS"), any(), any(), any(), any(), any(), any(), eq(0), any())).thenReturn(1);
        when(sessions.markSucceededCas(eq(session.getCheckoutSessionId()), eq("RESULT_RENDERED"),
                eq(ATTEMPT_ID), any(), any(), eq(attempt.getTransactionDateTime()), eq(0), any())).thenReturn(1);
        when(notifications.insertLogical(any())).thenReturn(1);
        stubRestoredRoute();

        TransactionChannelCallbackCommandDTO wrongCurrency = callback("succeeded", MID);
        wrongCurrency.setCurrency("EUR");
        wrongCurrency.setAmount(new BigDecimal("10.00"));
        assertThatThrownBy(() -> service.acceptCallback(wrongCurrency)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(notifications);

        TransactionChannelCallbackCommandDTO channelCallback = callback("succeeded", MID);
        channelCallback.setCurrency("USD");
        channelCallback.setAmount(new BigDecimal("7.50"));
        assertThat(service.acceptCallback(channelCallback)).isTrue();

        ArgumentCaptor<TransactionMerchantNotificationDO> notification =
                ArgumentCaptor.forClass(TransactionMerchantNotificationDO.class);
        verify(notifications).insertLogical(notification.capture());
        assertThat(notification.getValue().getPayloadJson())
                .contains("\"amount\":10.00", "\"currency\":\"EUR\"");
    }

    private void stubRestoredRoute() {
        PaymentRouteResultDTO route = route(MID);
        when(routes.restore("SCOTT_CHANNEL_A", null, 17L, null)).thenReturn(route);
        when(simulator.supportsRoute(route.getChannelCode(), route.getRequestUrl(), MID)).thenReturn(true);
    }

    private static TransactionChannelCallbackCommandDTO callback(String status, String mid) {
        var command = new TransactionChannelCallbackCommandDTO();
        command.setChannelCode("SCOTT_CHANNEL_A");
        command.setChannelCredentialHint(mid);
        command.setSignatureValid(true);
        command.setIpAllowed(true);
        command.setCallbackKind("FINANCIAL_TRANSACTION");
        command.setExtension(Map.of("merchantOrderNo", CHANNEL_ORDER, "paymentMethod", "APPLE_PAY"));
        command.setChannelTransactionId(CHANNEL_TRANSACTION);
        command.setCurrency("USD");
        command.setAmount(new BigDecimal("7.00"));
        command.setRawChannelStatus(status);
        return command;
    }

    private void stubPreparationRoute(PaymentRouteResultDTO route) {
        when(routes.route(any())).thenReturn(route);
        when(simulator.supportsRoute(route.getChannelCode(), route.getRequestUrl(), route.getMidNo())).thenReturn(true);
        when(simulator.prepare(any(), anyString(), anyString())).thenReturn(
                new HostedWalletSimulator.Result(HostedWalletSimulator.State.READY, CHANNEL_TRANSACTION,
                        "http://127.0.0.1:58090/simulate/hosted-payins/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", null));
    }

    private AtomicReference<PaymentCheckoutAttemptDO> stubInsertedAttempt(PaymentCheckoutSessionDO session) {
        AtomicReference<PaymentCheckoutAttemptDO> inserted = new AtomicReference<>();
        when(sessions.selectByCheckoutSessionIdForUpdate(session.getCheckoutSessionId())).thenReturn(session);
        when(ids.nextId()).thenReturn(ATTEMPT_ID);
        when(attempts.insert(any(PaymentCheckoutAttemptDO.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(attempts.selectByCheckoutAttemptId(ATTEMPT_ID)).thenAnswer(invocation -> {
            PaymentCheckoutAttemptDO attempt = inserted.get();
            if (attempt != null) {
                attempt.setChannelTransactionId(CHANNEL_TRANSACTION);
            }
            return attempt;
        });
        return inserted;
    }

    private static PaymentExchangeRateDTO rate(String base, String quote, String value) {
        var rate = new PaymentExchangeRateDTO();
        rate.setRateId(31L);
        rate.setBaseCurrency(base);
        rate.setQuoteCurrency(quote);
        rate.setFinalRate(new BigDecimal(value));
        rate.setSourceCode("TEST_RATE");
        rate.setEffectiveTime(LocalDateTime.of(2026, 11, 23, 12, 0));
        return rate;
    }

    private static Optional<IsoCurrencyInfo> currency(String code) {
        return switch (code) {
            case "USD" -> Optional.of(new IsoCurrencyInfo("USD", "840", "US Dollar", "美元", 2, 100,
                    new BigDecimal("0.01"), "$"));
            case "EUR" -> Optional.of(new IsoCurrencyInfo("EUR", "978", "Euro", "欧元", 2, 100,
                    new BigDecimal("0.01"), "€"));
            case "JPY" -> Optional.of(new IsoCurrencyInfo("JPY", "392", "Yen", "日元", 0, 1,
                    BigDecimal.ONE, "¥"));
            case "KWD" -> Optional.of(new IsoCurrencyInfo("KWD", "414", "Kuwaiti Dinar", "科威特第纳尔", 3, 1000,
                    new BigDecimal("0.001"), "د.ك"));
            case "CLF" -> Optional.of(new IsoCurrencyInfo("CLF", "990", "Unidad de Fomento", "智利发展单位", 4, 10000,
                    new BigDecimal("0.0001"), "UF"));
            case "CAD", "HKD" -> Optional.of(new IsoCurrencyInfo(code, "CAD".equals(code) ? "124" : "344",
                    code, code, 2, 100, new BigDecimal("0.01"), "$"));
            default -> Optional.empty();
        };
    }

    private static PaymentRouteResultDTO route(String mid) {
        return route(mid, "USD", "USD", false);
    }

    private static PaymentRouteResultDTO route(String mid, String requestedCurrency, String routedCurrency,
                                               boolean edcRequired) {
        var route = new PaymentRouteResultDTO();
        route.setRouted(true);
        route.setChannelCode("SCOTT_CHANNEL_A");
        route.setRequestUrl("http://127.0.0.1:58090");
        route.setMidNo(mid);
        route.setMidConfigId(17L);
        route.setMetadataValues(Map.of("apiKey", "test-only"));
        route.setRequestedCurrency(requestedCurrency);
        route.setRoutedCurrency(routedCurrency);
        route.setEdcRequired(edcRequired);
        return route;
    }

    private static PaymentCheckoutAttemptDO attempt(String merchantId, String status) {
        var attempt = new PaymentCheckoutAttemptDO();
        attempt.setCheckoutAttemptId(ATTEMPT_ID);
        attempt.setCheckoutSessionId("QA001");
        attempt.setMerchantId(merchantId);
        attempt.setChannelCode("SCOTT_CHANNEL_A");
        attempt.setChannelMidConfigId(17L);
        attempt.setPaymentMethod("APPLE_PAY");
        attempt.setAttemptRequestId("DEV-INTERACTIVE-APPLE_PAY");
        attempt.setChannelOrderNo(CHANNEL_ORDER);
        attempt.setChannelTransactionId(CHANNEL_TRANSACTION);
        attempt.setLabelAmount(new BigDecimal("7.00"));
        attempt.setLabelCurrency("USD");
        attempt.setChannelRequestAmount(new BigDecimal("7.00"));
        attempt.setChannelRequestCurrency("USD");
        attempt.setChannelStatus(status);
        attempt.setTransactionDateTime(LocalDateTime.of(2026, 11, 22, 19, 6, 12));
        attempt.setVersion(0);
        return attempt;
    }

    private static PaymentCheckoutSessionDO session(String merchantId, String amount) {
        return session(merchantId, amount, "USD");
    }

    private static PaymentCheckoutSessionDO session(String merchantId, String amount, String currency) {
        var session = new PaymentCheckoutSessionDO();
        session.setCheckoutSessionId("QA001");
        session.setMerchantId(merchantId);
        session.setMerchantOrderNo("QA-WALLET");
        session.setLabelAmount(new BigDecimal(amount));
        session.setLabelCurrency(currency);
        session.setPaymentAction("PAYMENT");
        session.setIntegrationType("HOSTED_CHECKOUT");
        session.setCheckoutDomain("http://localhost:5175");
        session.setPayerCountry("USA");
        session.setCheckoutStatus("PENDING");
        session.setProcessStage("WAITING_PAYER");
        session.setExpireTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(1));
        session.setVersion(0);
        return session;
    }
}

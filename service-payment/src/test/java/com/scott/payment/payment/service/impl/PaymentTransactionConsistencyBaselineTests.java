package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.domain.state.DefaultTransactionStateMachineService;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelInvokeService.PaymentChannelInvokeException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionConsistencyBaselineTests
 * @date : 2026-07-23 11:20
 * @description : BUG-VERIFY-001-001 修复前一致性基线测试，记录当前渠道调用、幂等、交易事实和恢复能力的 P0 缺口。
 * @status : create
 */
class PaymentTransactionConsistencyBaselineTests {

    private static final String MERCHANT_ID = "200001";

    private static final String MERCHANT_ORDER_NO = "M202607230001";

    private static final String MERCHANT_ORDER_ID = "REQ202607230001";

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 7, 23, 11, 20);

    /**
     * T-P0-01：渠道调用前，本地交易事实、幂等完成结果、Outbox 和渠道请求事实应已经可查询。
     */
    @Test
    void shouldHaveCommittedLocalFactsBeforeInvokingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CommittedFactsView committedFactsView = new CommittedFactsView(idempotencyService, recordService, outboxService);
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING),
                () -> {
                    CommittedFactsSnapshot snapshot = committedFactsView.readUsingIndependentTransaction();
                    assertThat(snapshot.initialRecordCount())
                            .as("T-P0-01 independent transaction must see local transaction/order/operation fact before MPGS call")
                            .isGreaterThan(0);
                    assertThat(snapshot.outboxSavedCount())
                            .as("T-P0-01 independent transaction must see outbox fact before MPGS call")
                            .isGreaterThan(0);
                    assertThat(snapshot.idempotencyCompletedCount())
                            .as("T-P0-01 independent transaction must see idempotency result before MPGS call")
                            .isGreaterThan(0);
                    assertThat(snapshot.channelRequestCount())
                            .as("T-P0-01 independent transaction must see channel request INIT before MPGS call")
                            .isGreaterThan(0);
                });
        DefaultPaymentTransactionPreparationService preparationService = preparationService(
                idempotencyService, recordService, outboxService, committedFactsView::commit);

        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService, preparationService);

        service.createPayment(baseCommand());
    }

    /**
     * T-P0-03：相同 merchantOrderNo 和相同业务参数顺序重复请求，不应再次调用 Payment。
     */
    @Test
    void shouldReturnOriginalTransactionForSequentialDuplicateSameOrderAndSameParams() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        PaymentCreateCommandDTO duplicate = baseCommand();
        duplicate.setMerchantOrderId("REQ202607230002");
        PaymentCreateResultDTO second = service.createPayment(duplicate);

        assertThat(second.getTransactionId())
                .as("T-P0-03 duplicate merchantOrderNo must return original transaction")
                .isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-03 duplicate merchantOrderNo must not call MPGS Payment again")
                .isEqualTo(1);
        assertThat(recordService.initialRecordCount)
                .as("T-P0-03 duplicate merchantOrderNo must not create second initial transaction fact")
                .isEqualTo(1);
    }

    /**
     * T-P0-02：同一 merchantOrderNo 并发请求必须由持久幂等兜底，不能因 Redis 锁缺失而产生第二次渠道 Payment。
     */
    @Test
    void shouldCreateOnlyOnePaymentForConcurrentDuplicateSameOrder() throws InterruptedException {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        channelService.blockFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<PaymentCreateResultDTO> first = new AtomicReference<>();
        AtomicReference<PaymentCreateResultDTO> second = new AtomicReference<>();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread firstThread = new Thread(() -> runAfterStart(ready, start, () -> first.set(service.createPayment(baseCommand())), firstError),
                "baseline-payment-first");
        Thread secondThread = new Thread(() -> runAfterStart(ready, start, () -> {
            PaymentCreateCommandDTO duplicate = baseCommand();
            duplicate.setMerchantOrderId("REQ202607230005");
            second.set(service.createPayment(duplicate));
        }, secondError), "baseline-payment-second");
        firstThread.start();
        secondThread.start();
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        firstThread.join(3000);
        secondThread.join(3000);

        assertThat(firstError.get()).as("T-P0-02 first request must not fail").isNull();
        assertThat(secondError.get()).as("T-P0-02 duplicate request should return original/current transaction instead of failing").isNull();
        assertThat(second.get().getTransactionId())
                .as("T-P0-02 concurrent duplicate merchantOrderNo must return original transaction")
                .isEqualTo(first.get().getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-02 concurrent duplicate merchantOrderNo must not call MPGS Payment twice")
                .isEqualTo(1);
        assertThat(recordService.initialRecordCount)
                .as("T-P0-02 concurrent duplicate merchantOrderNo must not create two initial facts")
                .isEqualTo(1);
    }

    /**
     * T-P0-04：相同 merchantOrderNo 但金额、币种或关键支付参数不同，必须在渠道调用前拒绝。
     */
    @Test
    void shouldRejectSameOrderNoWithDifferentAmountBeforeChannelInvocation() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        service.createPayment(baseCommand());
        PaymentCreateCommandDTO changed = baseCommand();
        changed.setMerchantOrderId("REQ202607230003");
        changed.setAmount(new BigDecimal("99.99"));

        assertThatThrownBy(() -> service.createPayment(changed))
                .as("T-P0-04 same merchantOrderNo with different amount must be rejected before MPGS")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-04 conflicting duplicate must not call MPGS Payment again")
                .isEqualTo(1);
    }

    /**
     * T-P0-06：渠道 Payment 超时后，重复请求不得再次调用 Payment，应保留原渠道请求等待查询恢复。
     */
    @Test
    void shouldNotReinvokePaymentAfterTimeoutForSameMerchantOrderNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(null);
        channelService.timeoutOnFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        PaymentCreateCommandDTO duplicate = baseCommand();
        duplicate.setMerchantOrderId("REQ202607230004");
        PaymentCreateResultDTO second = service.createPayment(duplicate);

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(first.getProcessStage()).isEqualTo(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        assertThat(second.getTransactionId())
                .as("T-P0-06 timeout duplicate must return original transaction")
                .isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-06 timeout duplicate must not call MPGS Payment again")
                .isEqualTo(1);
    }

    /**
     * T-P0-07：Payment 超时后主动查询若返回成功，应使用原渠道标识恢复成功，且不再次发起 Payment。
     */
    @Test
    void shouldRecoverTimeoutPaymentAsSuccessByQueryingOriginalChannelRequest() {
        RecoverableTransactionRecordService recordService = new RecoverableTransactionRecordService(PaymentTransactionStatusEnum.SUCCESS.getCode());
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        QueryOnlyPaymentChannelInvokeService channelService = new QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus.SUCCESS);
        TransactionChannelMatchService matchService = new DefaultTransactionChannelMatchService(
                recordService,
                channelService,
                restoreRouteService(),
                new DefaultChannelTransactionStatusResolver());

        TransactionChannelMatchResultDTO resultDTO = matchService.matchDue(matchCommand());

        assertThat(resultDTO.getScannedCount()).isEqualTo(1);
        assertThat(resultDTO.getMatchedCount()).isEqualTo(1);
        assertThat(recordService.completedStatus).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.channelOrderNo)
                .as("T-P0-07 query recovery must reuse original channelOrderNo")
                .isEqualTo(recordService.operation.getChannelOrderNo());
        assertThat(channelService.transactionId)
                .as("T-P0-07 query recovery must use original platform transaction id")
                .isEqualTo(recordService.operation.getTransactionId());
        assertThat(channelService.invokeCount.get())
                .as("T-P0-07 recovery query must not issue an extra Payment call")
                .isEqualTo(1);
    }

    /**
     * T-P0-08：Payment 超时后主动查询若返回失败，应恢复为合法失败，且不覆盖已有成功终态。
     */
    @Test
    void shouldRecoverTimeoutPaymentAsFailedByQueryingOriginalChannelRequest() {
        RecoverableTransactionRecordService recordService = new RecoverableTransactionRecordService(PaymentTransactionStatusEnum.FAILED.getCode());
        QueryOnlyPaymentChannelInvokeService channelService = new QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus.FAILED);
        TransactionChannelMatchService matchService = new DefaultTransactionChannelMatchService(
                recordService,
                channelService,
                restoreRouteService(),
                new DefaultChannelTransactionStatusResolver());

        TransactionChannelMatchResultDTO resultDTO = matchService.matchDue(matchCommand());

        assertThat(resultDTO.getScannedCount()).isEqualTo(1);
        assertThat(resultDTO.getMatchedCount()).isEqualTo(1);
        assertThat(recordService.completedStatus).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(recordService.completedFailReason).isEqualTo("CHANNEL_REQUEST_FAILED");
        assertThat(channelService.invokeCount.get()).isEqualTo(1);
    }

    /**
     * T-P0-11：待 3DS 交易的重复请求必须返回原交易和可用跳转信息，不得创建第二笔 Payment。
     */
    @Test
    void shouldReturnOriginal3dsTransactionForDuplicateSameOrder() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.NEED_REDIRECT));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        PaymentCreateCommandDTO duplicate = baseCommand();
        duplicate.setMerchantOrderId("REQ202607230006");
        PaymentCreateResultDTO second = service.createPayment(duplicate);

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(first.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_3DS.getCode());
        assertThat(second.getTransactionId())
                .as("T-P0-11 duplicate 3DS request must return original transaction")
                .isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-11 duplicate 3DS request must not call MPGS Payment again")
                .isEqualTo(1);
    }

    /**
     * T-P0-12：授权成功后一次 Capture 明确失败，不应终结授权生命周期，新的商户动作号可再次发起 Capture。
     */
    @Test
    void shouldAllowNewCaptureAfterPreviousCaptureFailedWithDifferentMerchantActionNo() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.FAILED));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO failedCapture = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));
        channelService.response = channelResponse(ChannelTradeStatus.SUCCESS);
        PaymentCreateResultDTO secondCapture = service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("5.00")));

        assertThat(failedCapture.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(secondCapture.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(recordService.followUpRecordCount).isEqualTo(2);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(2);
    }

    /**
     * T-P0-13：Capture 处于 timeout/unknown 且尚未恢复时，应阻止新的 Capture 进入渠道。
     */
    @Test
    void shouldBlockNewCaptureWhenPreviousCaptureStillUnknown() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        recordService.pendingCaptureExists = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("5.00"))))
                .as("T-P0-13 new capture must be blocked until unknown capture is recovered")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-13 blocked capture must not call channel")
                .isZero();
    }

    /**
     * S4-02：Capture 已创建且处于渠道请求处理中时，新的动作号不得再次发起 Capture。
     */
    @Test
    void shouldBlockNewCaptureWhenPreviousCaptureStillProcessing() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        recordService.pendingCaptureExists = true;
        recordService.pendingCaptureStage = PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("5.00"))))
                .as("S4-02 processing capture must block later capture action")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * S4-04：相同 Capture 动作号重复请求返回原动作快照，不创建第二笔请款，不再次调用渠道。
     */
    @Test
    void shouldReturnOriginalCaptureForDuplicateSameMerchantActionNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));
        PaymentCreateResultDTO duplicate = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * S4-05：相同 Capture 动作号但金额不同必须拒绝，不得再次调用渠道。
     */
    @Test
    void shouldRejectSameCaptureActionNoWithDifferentAmountBeforeChannelInvocation() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("6.00"))))
                .as("S4-05 same capture action no with different amount must be rejected")
                .isInstanceOf(RuntimeException.class);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * S4-06：Unknown Capture 在恢复前暂占可请款路径，不能通过剩余余额校验绕过再次请款。
     */
    @Test
    void shouldTreatUnknownCaptureAsOccupyingAvailableCaptureUntilRecovered() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("7.34");
        recordService.pendingCaptureExists = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("7.34"))))
                .as("S4-06 unknown capture must occupy the capture path even when order available amount looks enough")
                .isInstanceOf(RuntimeException.class);

        recordService.pendingCaptureExists = false;
        PaymentCreateResultDTO recoveredThenNewCapture = service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("7.34")));

        assertThat(recoveredThenNewCapture.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * S4-07：并发不同动作号 Capture 针对同一授权时，行锁和持久化未终态查询应阻止第二个渠道请求。
     */
    @Test
    void shouldNotCreateTwoChannelCapturesForConcurrentDifferentCaptureActionNo() throws Exception {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        recordService.markFollowUpProcessingForNewCaptures = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread first = new Thread(() -> runAfterStart(ready, start,
                () -> service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("7.00"))), firstError));
        Thread second = new Thread(() -> runAfterStart(ready, start,
                () -> service.capture(followUpCommand("CAPTURE-0002", new BigDecimal("7.00"))), secondError));
        first.start();
        second.start();
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        first.join(2000);
        second.join(2000);

        long failures = Stream.of(firstError.get(), secondError.get()).filter(Objects::nonNull).count();
        assertThat(failures).isEqualTo(1);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * T-P0-05：渠道已明确成功但本地记录失败时，交易不能丢失到只能重新发起 Payment 的状态。
     */
    @Test
    void shouldKeepRecoverableFactsWhenLocalRecordFailsAfterChannelSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        CapturingPaymentChannelResultTransactionService resultTransactionService =
                new CapturingPaymentChannelResultTransactionService(recordService);
        resultTransactionService.failResultRecord = true;
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                resultTransactionService);

        assertThatThrownBy(() -> service.createPayment(baseCommand()))
                .as("T-P0-05 bubbles result transaction failure after channel success")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(resultTransactionService.recordCount)
                .as("T-P0-05 channel result must enter the independent result transaction once")
                .isEqualTo(1);
        assertThat(idempotencyService.records)
                .as("T-P0-05 recoverable idempotency fact should remain available for query/recovery")
                .isNotEmpty();
        assertThat(recordService.initialRecordCount)
                .as("T-P0-05 local transaction fact should remain available even when final status update fails")
                .isGreaterThan(0);
        assertThat(recordService.channelRequestCount)
                .as("T-P0-05 pre-channel channel request fact should remain available for recovery")
                .isGreaterThan(0);

        PaymentCreateResultDTO duplicateResult = service.createPayment(baseCommand());

        assertThat(duplicateResult.getTransactionId()).isEqualTo(recordService.lastInitialOperation.getTransactionId());
        assertThat(duplicateResult.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-05 duplicate request after result transaction failure must not re-call Payment")
                .isEqualTo(1);
        assertThat(resultTransactionService.recordCount)
                .as("T-P0-05 duplicate request should return the prepared fact, not re-enter result persistence")
                .isEqualTo(1);
    }

    /**
     * S3-01：渠道结果保存必须经独立 Spring Bean 的 REQUIRES_NEW 事务入口，避免同类自调用导致事务失效。
     */
    @Test
    void shouldUseRequiresNewTransactionForInitialChannelResultPersistence() throws NoSuchMethodException {
        Method method = DefaultPaymentChannelResultTransactionService.class.getMethod(
                "recordInitialChannelResult",
                PaymentCreateCommandDTO.class,
                PaymentRouteResultDTO.class,
                PaymentChannelInvokeResultDTO.class,
                PaymentCreateResultDTO.class,
                PaymentRiskDecisionEnum.class,
                int.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("S3-01 channel result persistence must have a real Spring transaction boundary")
                .isNotNull();
        assertThat(transactional.propagation())
                .as("S3-01 channel result transaction must be independent from caller transactions")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor())
                .as("S3-01 any result persistence exception should roll back the whole result transaction")
                .contains(Exception.class);
    }

    private void runAfterStart(CountDownLatch ready,
                               CountDownLatch start,
                               Runnable action,
                               AtomicReference<Throwable> errorRef) {
        ready.countDown();
        try {
            if (!start.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("start latch timeout");
            }
            action.run();
        } catch (Throwable throwable) {
            errorRef.set(throwable);
        }
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId(MERCHANT_ID);
        commandDTO.setMerchantOrderNo(MERCHANT_ORDER_NO);
        commandDTO.setMerchantOrderId(MERCHANT_ORDER_ID);
        commandDTO.setPaymentMethod("BANK_CARD");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(TRANSACTION_TIME);
        commandDTO.setRequestFingerprint("fp:" + MERCHANT_ID + ":" + MERCHANT_ORDER_NO + ":12.34:USD:BANK_CARD:512345:0008");
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123456789010008");
        cardInfoDTO.setExpirationMonth("01");
        cardInfoDTO.setExpirationYear("39");
        cardInfoDTO.setSecurityCode("***");
        commandDTO.setCardInfo(cardInfoDTO);
        return commandDTO;
    }

    private PaymentCreateCommandDTO followUpCommand(String merchantActionNo, BigDecimal amount) {
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setMerchantOrderId(merchantActionNo);
        commandDTO.setAmount(amount);
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId("TX202607230001");
        commandDTO.setTransactionInfo(transactionInfoDTO);
        return commandDTO;
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService) {
        return newService(idempotencyService, recordService, outboxService, channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }));
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService,
                                                     DefaultPaymentTransactionPreparationService preparationService) {
        return newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService,
                new CapturingPaymentChannelResultTransactionService(recordService));
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService,
                                                     DefaultPaymentTransactionPreparationService preparationService,
                                                     PaymentChannelResultTransactionService resultTransactionService) {
        return new PaymentTransactionServiceImpl(
                isoDictionaryService(),
                riskDecision(PaymentRiskDecisionEnum.PASS),
                routeService(),
                channelService,
                preparationService,
                resultTransactionService,
                exchangeRateService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService(),
                new DefaultChannelTransactionStatusResolver(),
                List.of());
    }

    private DefaultPaymentTransactionPreparationService preparationService(InMemoryTransactionIdempotencyService idempotencyService,
                                                                          CapturingTransactionRecordService recordService,
                                                                          CapturingTransactionEventOutboxService outboxService,
                                                                          Runnable afterCommit) {
        return new DefaultPaymentTransactionPreparationService(
                isoDictionaryService(),
                riskDecision(PaymentRiskDecisionEnum.PASS),
                routeService(),
                exchangeRateService(),
                idempotencyService,
                outboxService,
                recordService) {
            @Override
            public PaymentInitialPreparationResultDTO prepareInitialTransaction(PaymentCreateCommandDTO commandDTO, String transactionType) {
                PaymentInitialPreparationResultDTO resultDTO = super.prepareInitialTransaction(commandDTO, transactionType);
                afterCommit.run();
                return resultDTO;
            }
        };
    }

    private PaymentRiskInvokeService riskDecision(PaymentRiskDecisionEnum decisionEnum) {
        return commandDTO -> {
            PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
            decisionDTO.setPassed(decisionEnum.isAllowProceed());
            decisionDTO.setDecision(decisionEnum.getCode());
            return decisionDTO;
        };
    }

    private PaymentChannelRouteService routeService() {
        return commandDTO -> {
            PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
            routeResultDTO.setChannelId(101L);
            routeResultDTO.setMidConfigId(1001L);
            routeResultDTO.setMidNo("TEST-MID");
            routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
            routeResultDTO.setRoutedCurrency(commandDTO.getCurrency());
            return routeResultDTO;
        };
    }

    private PaymentChannelRouteService restoreRouteService() {
        return new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                return routeService().route(commandDTO);
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode, Long channelId, Long midConfigId, String fallbackMidNo) {
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed(channelCode);
                routeResultDTO.setChannelId(channelId);
                routeResultDTO.setMidConfigId(midConfigId);
                routeResultDTO.setMidNo(fallbackMidNo);
                return routeResultDTO;
            }
        };
    }

    private PaymentExchangeRateService exchangeRateService() {
        return (baseCurrency, quoteCurrency, atTime) -> Optional.<PaymentExchangeRateDTO>empty();
    }

    private ChannelPaymentResponse channelResponse(ChannelTradeStatus tradeStatus) {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelOrderNo("CHO-" + MERCHANT_ORDER_NO);
        response.setChannelTransactionId("CHT-" + MERCHANT_ORDER_ID);
        response.setChannelTradeStatus(tradeStatus.getCode());
        response.setRawChannelStatus(tradeStatus.getCode());
        response.setChannelResponseCode(ChannelTradeStatus.SUCCESS == tradeStatus ? "00" : "05");
        response.setChannelResponseMessage(ChannelTradeStatus.SUCCESS == tradeStatus ? "Approved" : "Processing");
        if (ChannelTradeStatus.NEED_REDIRECT == tradeStatus) {
            response.setChannelResponseCode("3DS");
            response.setChannelResponseMessage("Need payer redirect");
            response.setRedirectUrl("https://acs.example/3ds/M202607230001");
        }
        return response;
    }

    private TransactionChannelMatchCommandDTO matchCommand() {
        TransactionChannelMatchCommandDTO commandDTO = new TransactionChannelMatchCommandDTO();
        commandDTO.setTransactionDateTime(TRANSACTION_TIME);
        commandDTO.setChannelCode("MPGS");
        commandDTO.setLimit(10);
        return commandDTO;
    }

    private IsoDictionaryService isoDictionaryService() {
        return new IsoDictionaryService() {
            @Override
            public List<IsoCountryInfo> listCountries() {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> searchCountries(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCountryInfo> getCountry(String value) {
                return Optional.empty();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByContinent(String continentCode) {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByCurrency(String currencyAlpha3Code) {
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> listCurrencies() {
                return List.of(usdCurrencyInfo());
            }

            @Override
            public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
                return List.of(usdCurrencyInfo());
            }

            @Override
            public Optional<IsoCurrencyInfo> getCurrency(String value) {
                return "USD".equalsIgnoreCase(value) ? Optional.of(usdCurrencyInfo()) : Optional.empty();
            }

            @Override
            public boolean isCurrencyFractionValid(BigDecimal amount, String currencyValue) {
                return true;
            }

            @Override
            public long toMinorUnit(BigDecimal amount, String currencyValue) {
                return amount.movePointRight(2).longValueExact();
            }

            private IsoCurrencyInfo usdCurrencyInfo() {
                return new IsoCurrencyInfo("USD", "840", "US Dollar", "美元", 2, 100L, new BigDecimal("0.01"), "$");
            }
        };
    }

    private static class InMemoryTransactionIdempotencyService implements TransactionIdempotencyService {

        private final Map<String, TransactionIdempotencyDO> records = new LinkedHashMap<>();

        private int completedCount;

        @Override
        public String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType) {
            return String.join(":", merchantId, merchantOrderId, transactionType);
        }

        @Override
        public String buildInitialTransactionKey(String merchantId, String merchantOrderNo) {
            return String.join(":", merchantId, merchantOrderNo, "INITIAL");
        }

        @Override
        public Optional<TransactionIdempotencyDO> find(String scope, String key) {
            return Optional.ofNullable(records.get(scope + ":" + key));
        }

        @Override
        public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
            return records.values().stream()
                    .filter(record -> transactionId.equals(record.getTransactionId()))
                    .findFirst();
        }

        @Override
        public synchronized boolean tryBegin(TransactionIdempotencyDO record) {
            String storageKey = record.getIdempotencyScope() + ":" + record.getIdempotencyKey();
            TransactionIdempotencyDO existing = records.get(storageKey);
            while (existing != null && existing.getTransactionId() == null) {
                try {
                    wait(1000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruptedException);
                }
                existing = records.get(storageKey);
            }
            if (existing != null) {
                return false;
            }
            records.put(storageKey, record);
            return true;
        }

        @Override
        public synchronized void complete(String scope,
                                          String key,
                                          String operationId,
                                          String transactionId,
                                          String transactionStatus,
                                          BigDecimal transactionAmount,
                                          String transactionCurrency,
                                          String resultSnapshot) {
            completedCount++;
            TransactionIdempotencyDO record = records.get(scope + ":" + key);
            record.setOperationId(operationId);
            record.setTransactionId(transactionId);
            record.setTransactionStatus(transactionStatus);
            record.setTransactionAmount(transactionAmount);
            record.setTransactionCurrency(transactionCurrency);
            record.setResultSnapshot(resultSnapshot);
            notifyAll();
        }

        @Override
        public TransactionIdempotencyDO newProcessingRecord(String scope,
                                                            String key,
                                                            String merchantId,
                                                            String merchantOrderNo,
                                                            String merchantOrderId,
                                                            String transactionType,
                                                            LocalDateTime transactionDateTime,
                                                            String timeZone,
                                                            String requestFingerprint,
                                                            LocalDateTime now) {
            TransactionIdempotencyDO record = new TransactionIdempotencyDO();
            record.setIdempotencyScope(scope);
            record.setIdempotencyKey(key);
            record.setMerchantId(merchantId);
            record.setMerchantOrderNo(merchantOrderNo);
            record.setMerchantOrderId(merchantOrderId);
            record.setTransactionType(transactionType);
            record.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            record.setTransactionDateTime(transactionDateTime);
            record.setTransactionTimeZone(timeZone);
            record.setTransactionUtcTime(transactionDateTime);
            record.setRequestFingerprint(requestFingerprint);
            return record;
        }
    }

    private record CommittedFactsSnapshot(int initialRecordCount,
                                          int outboxSavedCount,
                                          int idempotencyCompletedCount,
                                          int channelRequestCount) {
    }

    private static class CommittedFactsView {

        private final InMemoryTransactionIdempotencyService idempotencyService;

        private final CapturingTransactionRecordService recordService;

        private final CapturingTransactionEventOutboxService outboxService;

        private volatile CommittedFactsSnapshot committedSnapshot = new CommittedFactsSnapshot(0, 0, 0, 0);

        private CommittedFactsView(InMemoryTransactionIdempotencyService idempotencyService,
                                   CapturingTransactionRecordService recordService,
                                   CapturingTransactionEventOutboxService outboxService) {
            this.idempotencyService = idempotencyService;
            this.recordService = recordService;
            this.outboxService = outboxService;
        }

        private void commit() {
            committedSnapshot = new CommittedFactsSnapshot(
                    recordService.initialRecordCount,
                    outboxService.savedCount,
                    idempotencyService.completedCount,
                    recordService.channelRequestCount);
        }

        private CommittedFactsSnapshot readUsingIndependentTransaction() {
            return committedSnapshot;
        }
    }

    private static class CapturingTransactionRecordService implements TransactionRecordService {

        private int initialRecordCount;

        private int followUpRecordCount;

        private int channelRequestCount;

        private boolean failInitialRecord;

        private boolean pendingCaptureExists;

        private String pendingCaptureStage = PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode();

        private boolean markFollowUpProcessingForNewCaptures;

        private final ReentrantLock sourceOrderLock = new ReentrantLock();

        private TransactionOperationDO persistedNonTerminalCapture;

        private String sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();

        private String sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();

        private BigDecimal sourceAvailableCaptureAmount = new BigDecimal("12.34");

        private TransactionOperationDO lastInitialOperation;

        @Override
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
            initialRecordCount++;
            lastInitialOperation = new TransactionOperationDO();
            lastInitialOperation.setTransactionId(resultDTO.getTransactionId());
            lastInitialOperation.setOperationId(resultDTO.getOperationId());
            lastInitialOperation.setMerchantId(commandDTO.getMerchantId());
            lastInitialOperation.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
            lastInitialOperation.setMerchantOrderId(commandDTO.getMerchantOrderId());
            lastInitialOperation.setTransactionType(resultDTO.getTransactionType());
            lastInitialOperation.setTransactionStatus(resultDTO.getStatus());
            lastInitialOperation.setTransactionAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
            lastInitialOperation.setTransactionCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
            lastInitialOperation.setChannelOrderNo(channelInvokeResultDTO == null || channelInvokeResultDTO.getChannelRequest() == null
                    ? null : channelInvokeResultDTO.getChannelRequest().getChannelOrderNo());
            lastInitialOperation.setChannelTransactionId(channelInvokeResultDTO == null || channelInvokeResultDTO.getChannelRequest() == null
                    ? null : channelInvokeResultDTO.getChannelRequest().getChannelTransactionId());
            if (channelInvokeResultDTO != null && channelInvokeResultDTO.getChannelRequest() != null) {
                channelRequestCount++;
            }
            lastInitialOperation.setTransactionDateTime(commandDTO.getTransactionDateTime());
            if (failInitialRecord) {
                throw new IllegalStateException("simulated local record failure after channel success");
            }
        }

        @Override
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
            if (lastInitialOperation != null) {
                lastInitialOperation.setTransactionStatus(resultDTO.getStatus());
                lastInitialOperation.setProcessStage(resultDTO.getProcessStage());
                if (channelInvokeResultDTO != null && channelInvokeResultDTO.getChannelRequest() != null) {
                    lastInitialOperation.setChannelOrderNo(channelInvokeResultDTO.getChannelRequest().getChannelOrderNo());
                    lastInitialOperation.setChannelTransactionId(channelInvokeResultDTO.getChannelRequest().getChannelTransactionId());
                }
                if (channelInvokeResultDTO != null && channelInvokeResultDTO.getChannelResponse() != null) {
                    lastInitialOperation.setChannelStatus(channelInvokeResultDTO.getChannelResponse().getRawChannelStatus());
                }
            }
        }

        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return null;
        }

        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            if (!"TX202607230001".equals(sourceTransactionId)) {
                return null;
            }
            TransactionOrderDO orderDO = new TransactionOrderDO();
            orderDO.setOperationId("OP202607230001");
            orderDO.setRootTransactionId(sourceTransactionId);
            orderDO.setLatestTransactionId(sourceTransactionId);
            orderDO.setMerchantId(MERCHANT_ID);
            orderDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            orderDO.setMerchantOrderId(MERCHANT_ORDER_ID);
            orderDO.setTransactionType(sourceTransactionType);
            orderDO.setTransactionStatus(sourceStatus);
            orderDO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            orderDO.setTransactionAmount(new BigDecimal("12.34"));
            orderDO.setTransactionCurrency("USD");
            orderDO.setAuthorizedAmount(new BigDecimal("12.34"));
            orderDO.setCapturedAmount(BigDecimal.ZERO);
            orderDO.setAvailableCaptureAmount(sourceAvailableCaptureAmount);
            orderDO.setAvailableRefundAmount(BigDecimal.ZERO);
            orderDO.setCurrencyExponent(2);
            orderDO.setTransactionDateTime(TRANSACTION_TIME);
            orderDO.setTransactionTimeZone("Asia/Shanghai");
            orderDO.setVersion(0);
            return orderDO;
        }

        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            if (markFollowUpProcessingForNewCaptures) {
                sourceOrderLock.lock();
            }
            return findSourceOrderByTransactionId("TX202607230001");
        }

        @Override
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            if (!"TX202607230001".equals(sourceTransactionId)) {
                return null;
            }
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setOperationId("OP202607230001");
            operationDO.setTransactionId(sourceTransactionId);
            operationDO.setMerchantId(MERCHANT_ID);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId(MERCHANT_ORDER_ID);
            operationDO.setTransactionType(sourceTransactionType);
            operationDO.setTransactionStatus(sourceStatus);
            operationDO.setTransactionAmount(new BigDecimal("12.34"));
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelOrderNo(sourceTransactionId);
            operationDO.setChannelTransactionId("CH-" + sourceTransactionId);
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            return operationDO;
        }

        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                          String merchantOrderNo,
                                                                          String transactionId) {
            if (lastInitialOperation == null
                    || !lastInitialOperation.getMerchantId().equals(merchantId)
                    || !lastInitialOperation.getMerchantOrderNo().equals(merchantOrderNo)) {
                return List.of();
            }
            return List.of(lastInitialOperation);
        }

        @Override
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            if (lastInitialOperation == null
                    || !lastInitialOperation.getMerchantId().equals(merchantId)
                    || !lastInitialOperation.getMerchantOrderNo().equals(merchantOrderNo)) {
                return List.of();
            }
            return List.of(lastInitialOperation);
        }

        @Override
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            if (!MERCHANT_ID.equals(merchantId)
                    || !"OP202607230001".equals(operationId)
                    || !"TX202607230001".equals(sourceTransactionId)) {
                return List.of();
            }
            if (!pendingCaptureExists && persistedNonTerminalCapture == null) {
                return List.of();
            }
            if (persistedNonTerminalCapture != null) {
                try {
                    return List.of(persistedNonTerminalCapture);
                } finally {
                    unlockSourceOrderIfHeld();
                }
            }
            try {
                return List.of(pendingCapture());
            } finally {
                unlockSourceOrderIfHeld();
            }
        }

        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return null;
        }

        @Override
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            if (!pendingCaptureExists) {
                return List.of();
            }
            return List.of(pendingCapture());
        }

        private TransactionOperationDO pendingCapture() {
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(99L);
            operationDO.setOperationId("OP202607230001");
            operationDO.setTransactionId("TX-CAPTURE-UNKNOWN");
            operationDO.setSourceTransactionId("TX202607230001");
            operationDO.setMerchantId(MERCHANT_ID);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId("CAPTURE-0001");
            operationDO.setMerchantOperationNo("CAPTURE-0001");
            operationDO.setTransactionType(PaymentTransactionTypeEnum.CAPTURE.getCode());
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            operationDO.setProcessStage(pendingCaptureStage);
            operationDO.setTransactionAmount(new BigDecimal("5.00"));
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelOrderNo("TX202607230001");
            operationDO.setChannelTransactionId("CH-CAPTURE-UNKNOWN");
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            operationDO.setVersion(0);
            return operationDO;
        }

        private TransactionOperationDO pendingCaptureFromRecord(TransactionFollowUpRecordDTO recordDTO) {
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(100L + followUpRecordCount);
            operationDO.setOperationId(recordDTO.getSourceOrderDO().getOperationId());
            operationDO.setTransactionId(recordDTO.getResultDTO().getTransactionId());
            operationDO.setSourceTransactionId(recordDTO.getResultDTO().getSourceTransactionId());
            operationDO.setMerchantId(recordDTO.getCommandDTO().getMerchantId());
            operationDO.setMerchantOrderNo(recordDTO.getSourceOrderDO().getMerchantOrderNo());
            operationDO.setMerchantOrderId(recordDTO.getCommandDTO().getMerchantOrderId());
            operationDO.setMerchantOperationNo(recordDTO.getCommandDTO().getMerchantOrderId());
            operationDO.setTransactionType(recordDTO.getResultDTO().getTransactionType());
            operationDO.setTransactionStatus(recordDTO.getResultDTO().getStatus());
            operationDO.setProcessStage(recordDTO.getResultDTO().getProcessStage());
            operationDO.setTransactionAmount(recordDTO.getCommandDTO().getAmount());
            operationDO.setTransactionCurrency(recordDTO.getCommandDTO().getCurrency());
            operationDO.setTransactionDateTime(recordDTO.getCommandDTO().getTransactionDateTime());
            operationDO.setVersion(0);
            return operationDO;
        }

        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
            try {
                followUpRecordCount++;
                if (markFollowUpProcessingForNewCaptures
                        && PaymentTransactionTypeEnum.CAPTURE.getCode().equals(recordDTO.getResultDTO().getTransactionType())) {
                    persistedNonTerminalCapture = pendingCaptureFromRecord(recordDTO);
                    pendingCaptureExists = true;
                }
            } finally {
                unlockSourceOrderIfHeld();
            }
        }

        private void unlockSourceOrderIfHeld() {
            if (sourceOrderLock.isHeldByCurrentThread()) {
                sourceOrderLock.unlock();
            }
        }

        @Override
        public boolean completeByChannelCallback(TransactionOperationDO operationDO,
                                                 TransactionOrderDO orderDO,
                                                 String callbackId,
                                                 String targetTransactionStatus,
                                                 String failReasonCode,
                                                 String failReasonMessage,
                                                 String channelStatus,
                                                 String channelResponseCode,
                                                 String channelResponseMessage) {
            return false;
        }

        @Override
        public boolean updateChannelMatch(TransactionOperationDO operationDO,
                                          String matchStatus,
                                          String matchResult,
                                          String requestId,
                                          LocalDateTime matchTime,
                                          LocalDateTime nextMatchTime,
                                          String failReason) {
            return false;
        }

        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return false;
        }
    }

    private static class CapturingPaymentChannelResultTransactionService implements PaymentChannelResultTransactionService {

        private final TransactionRecordService recordService;

        private int recordCount;

        private boolean failResultRecord;

        private CapturingPaymentChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
        public void recordInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                               PaymentRouteResultDTO routeResultDTO,
                                               PaymentChannelInvokeResultDTO invokeResultDTO,
                                               PaymentCreateResultDTO resultDTO,
                                               PaymentRiskDecisionEnum riskDecisionEnum,
                                               int currencyExponent) {
            recordCount++;
            if (failResultRecord) {
                throw new IllegalStateException("simulated channel result transaction failure");
            }
            recordService.completeInitialChannelResult(
                    commandDTO,
                    routeResultDTO,
                    invokeResultDTO,
                    resultDTO,
                    riskDecisionEnum,
                    currencyExponent);
        }
    }

    private static class CapturingTransactionEventOutboxService implements TransactionEventOutboxService {

        private int savedCount;

        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            savedCount++;
        }

        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            return true;
        }

        @Override
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            return true;
        }
    }

    private static class InspectingPaymentChannelInvokeService implements PaymentChannelInvokeService {

        private ChannelPaymentResponse response;

        private final Runnable beforeInvokeAssertion;

        private final AtomicInteger invokeCount = new AtomicInteger();

        private boolean timeoutOnFirstPayment;

        private boolean blockFirstPayment;

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response) {
            this(response, () -> {
            });
        }

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response, Runnable beforeInvokeAssertion) {
            this.response = response;
            this.beforeInvokeAssertion = beforeInvokeAssertion;
        }

        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            beforeInvokeAssertion.run();
            invokeCount.incrementAndGet();
            if (blockFirstPayment && invokeCount.get() == 1) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruptedException);
                }
            }
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId("CR-" + transactionId);
            resultDTO.setRequestStatus("SUCCESS");
            resultDTO.setChannelRequest(new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest());
            resultDTO.getChannelRequest().setChannelCode(routeResult.getChannelCode());
            resultDTO.getChannelRequest().setOperationId(operationId);
            resultDTO.getChannelRequest().setTransactionId(transactionId);
            resultDTO.getChannelRequest().setChannelOrderNo(channelOrderNo);
            resultDTO.getChannelRequest().setChannelTransactionId("CH-" + transactionId);
            resultDTO.getChannelRequest().setAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
            resultDTO.getChannelRequest().setCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
            if (timeoutOnFirstPayment && invokeCount.get() == 1) {
                resultDTO.setRequestStatus("TIMEOUT");
                resultDTO.setExceptionType(ChannelTimeoutException.class.getSimpleName());
                resultDTO.setExceptionMessage("simulated timeout");
                throw new PaymentChannelInvokeException(resultDTO, new ChannelTimeoutException("simulated timeout"));
            }
            resultDTO.setChannelResponse(response);
            return resultDTO;
        }

        private int paymentInvokeCount() {
            return invokeCount.get();
        }
    }

    private static class RecoverableTransactionRecordService implements TransactionRecordService {

        private final TransactionOperationDO operation;

        private final TransactionOrderDO order;

        private final String expectedStatus;

        private String completedStatus;

        private String completedFailReason;

        private RecoverableTransactionRecordService(String expectedStatus) {
            this.expectedStatus = expectedStatus;
            this.operation = pendingOperation();
            this.order = pendingOrder();
        }

        @Override
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        }

        @Override
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
        }

        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            return order;
        }

        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            return operation;
        }

        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId, String merchantOrderNo, String transactionId) {
            return List.of(operation);
        }

        @Override
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            return List.of(operation);
        }

        @Override
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            return List.of();
        }

        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return operation;
        }

        @Override
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of(operation);
        }

        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        }

        @Override
        public boolean completeByChannelCallback(TransactionOperationDO operationDO,
                                                 TransactionOrderDO orderDO,
                                                 String callbackId,
                                                 String targetTransactionStatus,
                                                 String failReasonCode,
                                                 String failReasonMessage,
                                                 String channelStatus,
                                                 String channelResponseCode,
                                                 String channelResponseMessage) {
            assertThat(targetTransactionStatus).isEqualTo(expectedStatus);
            completedStatus = targetTransactionStatus;
            completedFailReason = failReasonCode;
            return true;
        }

        @Override
        public boolean updateChannelMatch(TransactionOperationDO operationDO,
                                          String matchStatus,
                                          String matchResult,
                                          String requestId,
                                          LocalDateTime matchTime,
                                          LocalDateTime nextMatchTime,
                                          String failReason) {
            return true;
        }

        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return true;
        }

        private TransactionOperationDO pendingOperation() {
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(11L);
            operationDO.setOperationId("OP202607230001");
            operationDO.setTransactionId("TX202607230001");
            operationDO.setMerchantId(MERCHANT_ID);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId(MERCHANT_ORDER_ID);
            operationDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            operationDO.setTransactionAmount(new BigDecimal("12.34"));
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelId(101L);
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelMidConfigId(1001L);
            operationDO.setChannelTerminalId("TEST-MID");
            operationDO.setChannelOrderNo("CHO-M202607230001");
            operationDO.setChannelTransactionId("CHT-REQ202607230001");
            operationDO.setChannelMatchCount(0);
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            operationDO.setVersion(0);
            return operationDO;
        }

        private TransactionOrderDO pendingOrder() {
            TransactionOrderDO orderDO = new TransactionOrderDO();
            orderDO.setOperationId("OP202607230001");
            orderDO.setRootTransactionId("TX202607230001");
            orderDO.setLatestTransactionId("TX202607230001");
            orderDO.setMerchantId(MERCHANT_ID);
            orderDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            orderDO.setMerchantOrderId(MERCHANT_ORDER_ID);
            orderDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
            orderDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            orderDO.setTransactionAmount(new BigDecimal("12.34"));
            orderDO.setTransactionCurrency("USD");
            orderDO.setCurrencyExponent(2);
            orderDO.setTransactionDateTime(TRANSACTION_TIME);
            orderDO.setVersion(0);
            return orderDO;
        }
    }

    private static class QueryOnlyPaymentChannelInvokeService implements PaymentChannelInvokeService {

        private final ChannelTradeStatus queryStatus;

        private final AtomicInteger invokeCount = new AtomicInteger();

        private String transactionId;

        private String channelOrderNo;

        private QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus queryStatus) {
            this.queryStatus = queryStatus;
        }

        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            invokeCount.incrementAndGet();
            this.transactionId = transactionId;
            this.channelOrderNo = channelOrderNo;
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId("Q-" + transactionId);
            resultDTO.setRequestStatus("SUCCESS");
            ChannelPaymentResponse response = new ChannelPaymentResponse();
            response.setChannelCode(routeResult.getChannelCode());
            response.setChannelOrderNo(channelOrderNo);
            response.setChannelTransactionId("CHT-REQ202607230001");
            response.setChannelTradeStatus(queryStatus.getCode());
            response.setRawChannelStatus(queryStatus.getCode());
            response.setChannelResponseCode(ChannelTradeStatus.SUCCESS == queryStatus ? "00" : "05");
            response.setChannelResponseMessage(ChannelTradeStatus.SUCCESS == queryStatus ? "Approved by query" : "Declined by query");
            resultDTO.setChannelResponse(response);
            return resultDTO;
        }
    }
}

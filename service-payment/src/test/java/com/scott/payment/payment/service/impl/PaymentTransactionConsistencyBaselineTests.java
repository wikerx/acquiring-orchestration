package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
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
import com.scott.payment.payment.service.CaptureChannelResultTransactionService;
import com.scott.payment.payment.service.CaptureTransactionPreparationService;
import com.scott.payment.payment.service.IncrementalAuthorizationChannelResultTransactionService;
import com.scott.payment.payment.service.IncrementalAuthorizationTransactionPreparationService;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.RefundChannelResultTransactionService;
import com.scott.payment.payment.service.RefundTransactionPreparationService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidChannelResultTransactionService;
import com.scott.payment.payment.service.VoidTransactionPreparationService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
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
 * @email : scott_x@163.com
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
     * T-P0-03：相同 orderInfo.orderId 重复请求必须返回原交易，不应再次调用 Payment。
     */
    @Test
    void shouldReturnOriginalTransactionForSequentialDuplicateSameOrderId() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        PaymentCreateCommandDTO duplicate = baseCommand();
        PaymentCreateResultDTO second = service.createPayment(duplicate);

        assertThat(second.getTransactionId())
                .as("T-P0-03 duplicate orderInfo.orderId must return original transaction")
                .isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-03 duplicate orderInfo.orderId must not call MPGS Payment again")
                .isEqualTo(1);
        assertThat(recordService.initialRecordCount)
                .as("T-P0-03 duplicate orderInfo.orderId must not create second initial transaction fact")
                .isEqualTo(1);
    }

    /**
     * T-P0-03B：同一商户订单上一笔明确失败后，新的 orderInfo.orderId 可以创建新的支付尝试。
     */
    @Test
    void shouldAllowNewOrderIdAfterPreviousMerchantOrderAttemptFailed() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.FAILED));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        PaymentCreateCommandDTO retry = baseCommand();
        retry.setMerchantOrderId("REQ202607230002");
        PaymentCreateResultDTO second = service.createPayment(retry);

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(second.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(second.getTransactionId())
                .as("T-P0-03B a new orderInfo.orderId after FAILED must create a new platform transaction")
                .isNotEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount())
                .as("T-P0-03B the new attempt must reach the payment channel once")
                .isEqualTo(2);
        assertThat(recordService.initialRecordCount)
                .as("T-P0-03B both failed attempts must remain independently auditable")
                .isEqualTo(2);
    }

    /**
     * T-P0-03C：上一笔仍在处理中时，不同 orderInfo.orderId 不得创建第二条活跃支付流。
     */
    @Test
    void shouldRejectNewOrderIdWhileMerchantOrderFlowIsProcessing() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);

        service.createPayment(baseCommand());
        PaymentCreateCommandDTO retry = baseCommand();
        retry.setMerchantOrderId("REQ202607230002");

        assertThatThrownBy(() -> service.createPayment(retry))
                .as("T-P0-03C a different orderInfo.orderId must not bypass an active merchant order flow")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.initialRecordCount).isEqualTo(1);
    }

    /**
     * T-P0-03D：同一商户订单的不同 orderInfo.orderId 并发竞争时，只允许一条支付流进入渠道。
     */
    @Test
    void shouldAllowOnlyOneActiveFlowForConcurrentDifferentOrderIds() throws InterruptedException {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        channelService.blockFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(idempotencyService, recordService, outboxService, channelService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread firstThread = new Thread(
                () -> runAfterStart(ready, start, () -> service.createPayment(baseCommand()), firstError),
                "merchant-order-flow-first");
        Thread secondThread = new Thread(() -> runAfterStart(ready, start, () -> {
            PaymentCreateCommandDTO competing = baseCommand();
            competing.setMerchantOrderId("REQ202607230002");
            service.createPayment(competing);
        }, secondError), "merchant-order-flow-second");
        firstThread.start();
        secondThread.start();
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        firstThread.join(3000);
        secondThread.join(3000);

        int successfulRequests = (firstError.get() == null ? 1 : 0) + (secondError.get() == null ? 1 : 0);
        assertThat(successfulRequests).as("only one distinct request may acquire the merchant order flow").isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.initialRecordCount).isEqualTo(1);
    }

    /** SUCCESS 已占用同一 merchantId + merchantOrderNo 时，新 orderInfo.orderId 必须被拒绝。 */
    @Test
    void shouldRejectNewOrderIdAfterMerchantOrderFlowSucceeded() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService, recordService, new CapturingTransactionEventOutboxService(), channelService);

        service.createPayment(baseCommand());
        PaymentCreateCommandDTO retry = baseCommand();
        retry.setMerchantOrderId("REQ202607230002");

        assertThatThrownBy(() -> service.createPayment(retry))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.initialRecordCount).isEqualTo(1);
    }

    /** Payment、Authorization、Pre-Authorization 必须共用同一商户订单支付流守卫。 */
    @Test
    void shouldShareMerchantOrderFlowAcrossInitialTransactionTypes() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService, recordService, new CapturingTransactionEventOutboxService(), channelService);

        service.createPayment(baseCommand());
        PaymentCreateCommandDTO authorization = baseCommand();
        authorization.setMerchantOrderId("AUTH-REQUEST-2");
        PaymentCreateCommandDTO preAuthorization = baseCommand();
        preAuthorization.setMerchantOrderId("PREAUTH-REQUEST-3");

        assertThatThrownBy(() -> service.createAuthorization(authorization))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.createPreAuthorization(preAuthorization))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.initialRecordCount).isEqualTo(1);
    }

    /** 渠道异步确认 FAILED 后，新 orderInfo.orderId 可以重新占用同一商户订单支付流。 */
    @Test
    void shouldAllowNewOrderIdAfterAsynchronousFailure() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService, recordService, new CapturingTransactionEventOutboxService(), channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        assertThat(idempotencyService.synchronizeInitialTransactionStatus(
                first.getTransactionId(), PaymentTransactionStatusEnum.FAILED.getCode())).isEqualTo(2);
        PaymentCreateCommandDTO retry = baseCommand();
        retry.setMerchantOrderId("REQ202607230002");
        PaymentCreateResultDTO second = service.createPayment(retry);

        assertThat(second.getTransactionId()).isNotEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(2);
        assertThat(recordService.initialRecordCount).isEqualTo(2);
    }

    /** 渠道异步确认 SUCCESS 后，同一商户订单不得再创建新的首次交易。 */
    @Test
    void shouldRejectNewOrderIdAfterAsynchronousSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService, recordService, new CapturingTransactionEventOutboxService(), channelService);

        PaymentCreateResultDTO first = service.createPayment(baseCommand());
        assertThat(idempotencyService.synchronizeInitialTransactionStatus(
                first.getTransactionId(), PaymentTransactionStatusEnum.SUCCESS.getCode())).isEqualTo(2);
        PaymentCreateCommandDTO retry = baseCommand();
        retry.setMerchantOrderId("REQ202607230002");

        assertThatThrownBy(() -> service.createPayment(retry))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.initialRecordCount).isEqualTo(1);
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
            second.set(service.createPayment(baseCommand()));
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
        DefaultTransactionChannelMatchResultTransactionService resultTransactionService =
                new DefaultTransactionChannelMatchResultTransactionService(
                        recordService,
                        new DefaultTransactionLifecycleEventService(outboxService));
        TransactionChannelMatchService matchService = new DefaultTransactionChannelMatchService(
                recordService,
                channelService,
                resultTransactionService,
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
        assertThat(channelService.channelTransactionId)
                .as("T-P0-07 query recovery must use persisted channel transaction id instead of platform transaction id")
                .isEqualTo(recordService.operation.getChannelTransactionId());
        assertThat(channelService.channelTransactionId).isNotEqualTo(channelService.transactionId);
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
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        QueryOnlyPaymentChannelInvokeService channelService = new QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus.FAILED);
        DefaultTransactionChannelMatchResultTransactionService resultTransactionService =
                new DefaultTransactionChannelMatchResultTransactionService(
                        recordService,
                        new DefaultTransactionLifecycleEventService(outboxService));
        TransactionChannelMatchService matchService = new DefaultTransactionChannelMatchService(
                recordService,
                channelService,
                resultTransactionService,
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
     * 05C-CAP-01：Capture 渠道调用前必须已提交动作事实、幂等快照、Outbox 和渠道请求 INIT。
     */
    @Test
    void shouldCommitCaptureFactsBeforeCallingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CommittedFactsView committedFactsView = new CommittedFactsView(idempotencyService, recordService, outboxService);
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> {
                    CommittedFactsSnapshot snapshot = committedFactsView.readUsingIndependentTransaction();
                    assertThat(snapshot.followUpRecordCount()).isEqualTo(1);
                    assertThat(snapshot.outboxSavedCount()).isEqualTo(1);
                    assertThat(snapshot.idempotencyCompletedCount()).isEqualTo(1);
                    assertThat(snapshot.channelRequestCount()).isEqualTo(1);
                });
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, committedFactsView::commit),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService));

        PaymentCreateResultDTO resultDTO = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.lastFollowUpOperation.getChannelTransactionId()).startsWith("CH");
    }

    /** Capture 和 PreAuth Completion 必须复用原交易渠道及 MID，禁止按当前绑定重新选路。 */
    @Test
    void shouldRestoreOriginalChannelMidForCaptureWithoutRerouting() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        AtomicInteger restoreCount = new AtomicInteger();
        PaymentChannelRouteService sourceRouteService = new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                throw new AssertionError("capture must not reroute by current merchant binding");
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode,
                                                 Long channelId,
                                                 Long midConfigId,
                                                 String fallbackMidNo) {
                restoreCount.incrementAndGet();
                assertThat(channelCode).isEqualTo("MPGS");
                assertThat(channelId).isEqualTo(101L);
                assertThat(midConfigId).isEqualTo(1001L);
                assertThat(fallbackMidNo).isEqualTo("TEST-MID");
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed(channelCode);
                routeResultDTO.setChannelId(channelId);
                routeResultDTO.setMidConfigId(midConfigId);
                routeResultDTO.setMidNo(fallbackMidNo);
                return routeResultDTO;
            }
        };
        DefaultCaptureTransactionPreparationService preparationService =
                new DefaultCaptureTransactionPreparationService(
                        isoDictionaryService(),
                        sourceRouteService,
                        idempotencyService,
                        new CapturingTransactionEventOutboxService(),
                        recordService,
                        new DefaultTransactionStateMachineService());

        CapturePreparationResultDTO resultDTO = preparationService.prepareCapture(
                followUpCommand("CAPTURE-ORIGINAL-MID", new BigDecimal("5.00")),
                "200001:TX202607230001:CAPTURE-ORIGINAL-MID:CAPTURE",
                PaymentTransactionTypeEnum.CAPTURE);

        assertThat(restoreCount).hasValue(1);
        assertThat(resultDTO.getRouteResultDTO().getChannelCode()).isEqualTo("MPGS");
        assertThat(resultDTO.getRouteResultDTO().getMidConfigId()).isEqualTo(1001L);
    }

    /** 原 MID 不可用时仍须落失败动作和幂等快照，重复请求不得再次尝试渠道恢复。 */
    @Test
    void shouldPersistF414CaptureRejectionWithoutCallingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        AtomicInteger restoreCount = new AtomicInteger();
        PaymentChannelRouteService unavailableRouteService = new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                throw new AssertionError("capture rejection must not reroute");
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode,
                                                 Long channelId,
                                                 Long midConfigId,
                                                 String fallbackMidNo) {
                restoreCount.incrementAndGet();
                throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
            }
        };
        DefaultCaptureTransactionPreparationService preparationService =
                new DefaultCaptureTransactionPreparationService(
                        isoDictionaryService(),
                        unavailableRouteService,
                        idempotencyService,
                        new CapturingTransactionEventOutboxService(),
                        recordService,
                        new DefaultTransactionStateMachineService());
        PaymentCreateCommandDTO command = followUpCommand("CAPTURE-F414", new BigDecimal("5.00"));
        String idempotencyKey = "200001:TX202607230001:CAPTURE-F414:CAPTURE";

        CapturePreparationResultDTO rejected = preparationService.prepareCapture(
                command, idempotencyKey, PaymentTransactionTypeEnum.CAPTURE);
        CapturePreparationResultDTO duplicate = preparationService.prepareCapture(
                followUpCommand("CAPTURE-F414", new BigDecimal("5.00")),
                idempotencyKey,
                PaymentTransactionTypeEnum.CAPTURE);

        assertThat(rejected.isCallChannel()).isFalse();
        assertThat(rejected.getResultDTO().getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(rejected.getResultDTO().getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(rejected.getResultDTO().getMerchantResponseCode()).isEqualTo("F414");
        assertThat(rejected.getResultDTO().getMerchantResponseMessage()).isEqualTo("Original transaction rejected.");
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isZero();
        assertThat(idempotencyService.completedCount).isEqualTo(1);
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThat(duplicate.getResultDTO().getTransactionId()).isEqualTo(rejected.getResultDTO().getTransactionId());
        assertThat(restoreCount).hasValue(1);
    }

    /**
     * 05C-CAP-02：Capture 渠道成功后结果事务失败，重复同动作号只能返回已提交的 PROCESSING 事实，不能重新发起 Capture。
     */
    @Test
    void shouldKeepCaptureRecoverableFactsWhenResultTransactionFailsAfterChannelSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CapturingCaptureChannelResultTransactionService captureResultService =
                new CapturingCaptureChannelResultTransactionService(recordService);
        captureResultService.failResultRecord = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                captureResultService,
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService));

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);

        PaymentCreateResultDTO duplicate = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(duplicate.getTransactionId()).isEqualTo(recordService.lastFollowUpOperation.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(captureResultService.recordCount).isEqualTo(1);
    }

    /**
     * 05C-CAP-03：Capture timeout/unknown 后重复同动作号不得重新发起 Capture。
     */
    @Test
    void shouldNotReissueCaptureAfterTimeoutForSameMerchantActionNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        channelService.timeoutOnFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));
        PaymentCreateResultDTO duplicate = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05C-CAP-04：Capture 同步成功后重复同动作号必须返回终态快照，不得停留在准备阶段 PROCESSING。
     */
    @Test
    void shouldRefreshCaptureIdempotencySnapshotAfterSuccessfulResultTransaction() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));
        PaymentCreateResultDTO duplicate = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05C-CAP-05：Capture 明确失败也必须刷新幂等快照，同动作号重复返回失败终态且不重发渠道。
     */
    @Test
    void shouldRefreshCaptureIdempotencySnapshotAfterFailedResultTransaction() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.FAILED));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));
        PaymentCreateResultDTO duplicate = service.capture(followUpCommand("CAPTURE-0001", new BigDecimal("5.00")));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05C-CAP-06：Capture 渠道结果必须经独立 Spring Bean 的 REQUIRES_NEW 事务入口。
     */
    @Test
    void shouldUseRequiresNewTransactionForCaptureChannelResultPersistence() throws NoSuchMethodException {
        Method method = DefaultCaptureChannelResultTransactionService.class.getMethod(
                "recordCaptureChannelResult",
                CapturePreparationResultDTO.class,
                PaymentChannelInvokeResultDTO.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    /**
     * 05C-CAP-07：Capture 入口自身不得用大事务包住渠道调用。
     */
    @Test
    void shouldNotWrapCaptureEntryInDatabaseTransaction() throws NoSuchMethodException {
        Method method = PaymentTransactionServiceImpl.class.getMethod("capture", PaymentCreateCommandDTO.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    /**
     * 05D-REF-01：Refund 渠道调用前必须已提交动作事实、幂等快照、Outbox 和渠道请求 INIT。
     */
    @Test
    void shouldCommitRefundFactsBeforeCallingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CommittedFactsView committedFactsView = new CommittedFactsView(idempotencyService, recordService, outboxService);
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> {
                    CommittedFactsSnapshot snapshot = committedFactsView.readUsingIndependentTransaction();
                    assertThat(snapshot.followUpRecordCount()).isEqualTo(1);
                    assertThat(snapshot.outboxSavedCount()).isEqualTo(1);
                    assertThat(snapshot.idempotencyCompletedCount()).isEqualTo(1);
                    assertThat(snapshot.channelRequestCount()).isEqualTo(1);
                });
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, committedFactsView::commit),
                new CapturingRefundChannelResultTransactionService(recordService));

        PaymentCreateResultDTO resultDTO = service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00")));

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.lastFollowUpOperation.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.REFUND.getCode());
        assertThat(recordService.lastFollowUpOperation.getChannelTransactionId()).startsWith("CH");
    }

    /** 退款必须复用原成功交易的渠道和 MID，禁止按 REFUND 能力重新执行商户路由。 */
    @Test
    void shouldRestoreOriginalChannelMidForRefundWithoutRerouting() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        AtomicInteger restoreCount = new AtomicInteger();
        PaymentChannelRouteService sourceRouteService = new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                throw new AssertionError("refund must not reroute by REFUND capability");
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode,
                                                 Long channelId,
                                                 Long midConfigId,
                                                 String fallbackMidNo) {
                restoreCount.incrementAndGet();
                assertThat(channelCode).isEqualTo("MPGS");
                assertThat(channelId).isEqualTo(101L);
                assertThat(midConfigId).isEqualTo(1001L);
                assertThat(fallbackMidNo).isEqualTo("TEST-MID");
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed(channelCode);
                routeResultDTO.setChannelId(channelId);
                routeResultDTO.setMidConfigId(midConfigId);
                routeResultDTO.setMidNo(fallbackMidNo);
                return routeResultDTO;
            }
        };
        DefaultRefundTransactionPreparationService preparationService =
                new DefaultRefundTransactionPreparationService(
                        isoDictionaryService(),
                        sourceRouteService,
                        idempotencyService,
                        new CapturingTransactionEventOutboxService(),
                        recordService,
                        new DefaultTransactionStateMachineService());

        RefundPreparationResultDTO resultDTO = preparationService.prepareRefund(
                followUpCommand("REFUND-ORIGINAL-MID", new BigDecimal("5.00")),
                "200001:TX202607230001:REFUND-ORIGINAL-MID:REFUND");

        assertThat(restoreCount).hasValue(1);
        assertThat(resultDTO.getRouteResultDTO().getChannelCode()).isEqualTo("MPGS");
        assertThat(resultDTO.getRouteResultDTO().getMidConfigId()).isEqualTo(1001L);
    }

    /**
     * 05D-REF-02：Refund 渠道成功后结果事务失败，重复同动作号只能返回已提交的 PROCESSING 事实，不能重新发起 Refund。
     */
    @Test
    void shouldKeepRefundRecoverableFactsWhenResultTransactionFailsAfterChannelSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CapturingRefundChannelResultTransactionService refundResultService =
                new CapturingRefundChannelResultTransactionService(recordService);
        refundResultService.failResultRecord = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                refundResultService);

        assertThatThrownBy(() -> service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);

        PaymentCreateResultDTO duplicate = service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00")));

        assertThat(duplicate.getTransactionId()).isEqualTo(recordService.lastFollowUpOperation.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(refundResultService.recordCount).isEqualTo(1);
    }

    /**
     * 05D-REF-03：Refund timeout/unknown 后重复同动作号不得重新发起 Refund。
     */
    @Test
    void shouldNotReissueRefundAfterTimeoutForSameMerchantActionNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        channelService.timeoutOnFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00")));
        PaymentCreateResultDTO duplicate = service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00")));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05D-REF-04：相同 Refund 动作号不同金额必须拒绝，且不得再次调用渠道。
     */
    @Test
    void shouldRejectSameRefundActionNoWithDifferentAmountBeforeChannelInvocation() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        service.refund(followUpCommand("REFUND-0001", new BigDecimal("5.00")));

        assertThatThrownBy(() -> service.refund(followUpCommand("REFUND-0001", new BigDecimal("6.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
    }

    /**
     * Refund 属于后续交易，不适用路由前内风控；即使顶层风控服务会拒绝，也必须继续路由和渠道准备。
     */
    @Test
    void shouldContinueRefundWhenRiskRejectsBecauseFollowUpSkipsRisk() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.REJECT),
                idempotencyService,
                recordService,
                outboxService,
                channelService);

        PaymentCreateResultDTO resultDTO = service.refund(followUpCommand("REFUND-RISK-0001", new BigDecimal("5.00")));

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resultDTO.getFailReasonCode()).isNull();
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isEqualTo(1);
        assertThat(recordService.lastFollowUpRiskDecision).isNull();
        assertThat(recordService.lastFollowUpOperation.getChannelTransactionId()).isNotNull();
        assertThat(idempotencyService.completedCount).isEqualTo(2);
        assertThat(outboxService.savedCount).isEqualTo(1);
    }

    /**
     * 05D-REF-05：Unknown/Processing Refund 必须占用可退额度，新的动作号也不能超过真实剩余额度。
     */
    @Test
    void shouldReserveRefundCapacityForNonTerminalRefunds() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("10.00");
        recordService.pendingRefundAmount = new BigDecimal("7.00");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.refund(followUpCommand("REFUND-0002", new BigDecimal("4.00"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(channelService.paymentInvokeCount()).isEqualTo(0);
        assertThat(recordService.followUpRecordCount).isEqualTo(0);
    }

    /**
     * 05D-REF-06：Refund 渠道结果必须经独立 Spring Bean 的 REQUIRES_NEW 事务入口。
     */
    @Test
    void shouldUseRequiresNewTransactionForRefundChannelResultPersistence() throws NoSuchMethodException {
        Method method = DefaultRefundChannelResultTransactionService.class.getMethod(
                "recordRefundChannelResult",
                RefundPreparationResultDTO.class,
                PaymentChannelInvokeResultDTO.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    /**
     * 05D-REF-07：Refund 入口自身不得用大事务包住渠道调用。
     */
    @Test
    void shouldNotWrapRefundEntryInDatabaseTransaction() throws NoSuchMethodException {
        Method method = PaymentTransactionServiceImpl.class.getMethod("refund", PaymentCreateCommandDTO.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    /**
     * 05E-VOID-01：Void / Authorization Cancel 渠道调用前必须已提交动作事实、幂等快照、Outbox 和渠道请求 INIT。
     */
    @Test
    void shouldCommitVoidFactsBeforeCallingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CommittedFactsView committedFactsView = new CommittedFactsView(idempotencyService, recordService, outboxService);
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> {
                    CommittedFactsSnapshot snapshot = committedFactsView.readUsingIndependentTransaction();
                    assertThat(snapshot.followUpRecordCount()).isEqualTo(1);
                    assertThat(snapshot.outboxSavedCount()).isEqualTo(1);
                    assertThat(snapshot.idempotencyCompletedCount()).isEqualTo(1);
                    assertThat(snapshot.channelRequestCount()).isEqualTo(1);
                });
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService),
                voidPreparationService(idempotencyService, recordService, outboxService, committedFactsView::commit),
                new CapturingVoidChannelResultTransactionService(recordService));

        PaymentCreateResultDTO resultDTO = service.voidPayment(voidCommand("VOID-0001"));

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.lastFollowUpOperation.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.VOID.getCode());
        assertThat(recordService.lastFollowUpOperation.getChannelTransactionId()).startsWith("CH");
        assertThat(channelService.lastTargetTransactionId)
                .as("05E Void must send the source channel transaction id as MPGS targetTransactionId")
                .isEqualTo("CH-TX202607230001");
    }

    /**
     * 05E-VOID-02：Void 渠道成功后结果事务失败，重复同动作号只能返回已提交的 PROCESSING 事实，不能重新发起 Void。
     */
    @Test
    void shouldKeepVoidRecoverableFactsWhenResultTransactionFailsAfterChannelSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CapturingVoidChannelResultTransactionService voidResultService =
                new CapturingVoidChannelResultTransactionService(recordService);
        voidResultService.failResultRecord = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService),
                voidPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                voidResultService);

        assertThatThrownBy(() -> service.voidPayment(voidCommand("VOID-0001")))
                .isInstanceOf(RuntimeException.class);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);

        PaymentCreateResultDTO duplicate = service.voidPayment(voidCommand("VOID-0001"));

        assertThat(duplicate.getTransactionId()).isEqualTo(recordService.lastFollowUpOperation.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(voidResultService.recordCount).isEqualTo(1);
    }

    /**
     * 05E-VOID-03：Void timeout/unknown 后重复同动作号不得重新发起 Void / Authorization Cancel。
     */
    @Test
    void shouldNotReissueVoidAfterTimeoutForSameMerchantActionNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        channelService.timeoutOnFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.voidPayment(voidCommand("VOID-0001"));
        PaymentCreateResultDTO duplicate = service.voidPayment(voidCommand("VOID-0001"));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05E-VOID-04：相同 Void 动作号不同源交易必须拒绝，且不得再次调用渠道。
     */
    @Test
    void shouldRejectSameVoidActionNoWithDifferentSourceBeforeChannelInvocation() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        service.voidPayment(voidCommand("VOID-0001"));
        PaymentCreateCommandDTO changed = voidCommand("VOID-0001");
        changed.getTransactionInfo().setSourceTransactionId("TX202607230009");

        assertThatThrownBy(() -> service.voidPayment(changed))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
    }

    /**
     * 05E-VOID-05：Void 渠道结果必须经独立 Spring Bean 的 REQUIRES_NEW 事务入口。
     */
    @Test
    void shouldUseRequiresNewTransactionForVoidChannelResultPersistence() throws NoSuchMethodException {
        Method method = DefaultVoidChannelResultTransactionService.class.getMethod(
                "recordVoidChannelResult",
                VoidPreparationResultDTO.class,
                PaymentChannelInvokeResultDTO.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    /**
     * 05E-VOID-06：Void 入口自身不得用大事务包住渠道调用。
     */
    @Test
    void shouldNotWrapVoidEntryInDatabaseTransaction() throws NoSuchMethodException {
        Method method = PaymentTransactionServiceImpl.class.getMethod("voidPayment", PaymentCreateCommandDTO.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    /**
     * 05E-VOID-07：Capture 与 Authorization Cancel 并发时，未终态 Capture 必须阻止 Cancel 进入渠道。
     */
    @Test
    void shouldBlockAuthorizationCancelWhenCaptureIsPending() {
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

        assertThatThrownBy(() -> service.voidPayment(voidCommand("VOID-0002")))
                .as("05E pending capture must block authorization cancel before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * 05E-VOID-07B：未终态 Authorization Cancel 必须阻止 Capture，避免取消和请款并发释放/占用同一授权额度。
     */
    @Test
    void shouldBlockCaptureWhenAuthorizationCancelIsPending() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        recordService.pendingVoidExists = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-VOID-CONFLICT", new BigDecimal("5.00"))))
                .as("05E pending authorization cancel must block capture before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * 05E-VOID-08：Payment Void 与 Refund 互斥，未终态 Refund 必须阻止 Void，避免重复资金返还。
     */
    @Test
    void shouldBlockPaymentVoidWhenRefundIsPending() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = BigDecimal.ZERO;
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        recordService.pendingRefundAmount = new BigDecimal("5.00");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.voidPayment(voidCommand("VOID-0003")))
                .as("05E pending refund must block payment void before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * 05E-VOID-09：未终态 Payment Void 必须阻止 Refund，避免退款和撤销双重返还。
     */
    @Test
    void shouldBlockRefundWhenPaymentVoidIsPending() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableRefundAmount = new BigDecimal("12.34");
        recordService.pendingVoidExists = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.refund(followUpCommand("REFUND-VOID-CONFLICT", new BigDecimal("5.00"))))
                .as("05E pending payment void must block refund before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * 05F-INC-AUTH-01：Incremental Authorization 渠道调用前必须已提交动作事实、幂等快照、Outbox 和渠道请求 INIT。
     */
    @Test
    void shouldCommitIncrementalAuthorizationFactsBeforeCallingChannel() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CommittedFactsView committedFactsView = new CommittedFactsView(idempotencyService, recordService, outboxService);
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> {
                    CommittedFactsSnapshot snapshot = committedFactsView.readUsingIndependentTransaction();
                    assertThat(snapshot.followUpRecordCount()).isEqualTo(1);
                    assertThat(snapshot.outboxSavedCount()).isEqualTo(1);
                    assertThat(snapshot.idempotencyCompletedCount()).isEqualTo(1);
                    assertThat(snapshot.channelRequestCount()).isEqualTo(1);
                });
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService),
                voidPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingVoidChannelResultTransactionService(recordService),
                incrementalAuthorizationPreparationService(idempotencyService, recordService, outboxService, committedFactsView::commit),
                new CapturingIncrementalAuthorizationChannelResultTransactionService(recordService));

        PaymentCreateResultDTO resultDTO = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.lastFollowUpOperation.getTransactionType())
                .isEqualTo(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        assertThat(recordService.lastFollowUpOperation.getChannelTransactionId()).startsWith("CH");
    }

    /**
     * 05F-INC-AUTH-02：渠道成功后结果事务失败，重复同动作号只能返回已提交 PROCESSING 事实，不能重新发起增量授权。
     */
    @Test
    void shouldKeepIncrementalAuthorizationRecoverableFactsWhenResultTransactionFailsAfterChannelSuccess() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        CapturingTransactionEventOutboxService outboxService = new CapturingTransactionEventOutboxService();
        CapturingIncrementalAuthorizationChannelResultTransactionService incrementalResultService =
                new CapturingIncrementalAuthorizationChannelResultTransactionService(recordService);
        incrementalResultService.failResultRecord = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingPaymentChannelResultTransactionService(recordService),
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService),
                voidPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingVoidChannelResultTransactionService(recordService),
                incrementalAuthorizationPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                incrementalResultService);

        assertThatThrownBy(() -> service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(recordService.channelRequestCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);

        PaymentCreateResultDTO duplicate = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));

        assertThat(duplicate.getTransactionId()).isEqualTo(recordService.lastFollowUpOperation.getTransactionId());
        assertThat(duplicate.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(incrementalResultService.recordCount).isEqualTo(1);
    }

    /**
     * 05F-INC-AUTH-03：Incremental Authorization timeout/unknown 后重复同动作号不得重新发起渠道请求。
     */
    @Test
    void shouldNotReissueIncrementalAuthorizationAfterTimeoutForSameMerchantActionNo() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        channelService.timeoutOnFirstPayment = true;
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        PaymentCreateResultDTO first = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));
        PaymentCreateResultDTO duplicate = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));

        assertThat(first.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05F-INC-AUTH-04：相同 Incremental Authorization 动作号相同参数返回原动作，不创建第二笔且不调用渠道。
     */
    @Test
    void shouldReturnOriginalIncrementalAuthorizationForDuplicateSameMerchantActionNo() {
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

        PaymentCreateResultDTO first = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));
        PaymentCreateResultDTO duplicate = service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));

        assertThat(duplicate.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
    }

    /**
     * 05F-INC-AUTH-05：相同 Incremental Authorization 动作号不同金额必须拒绝，且不得再次调用渠道。
     */
    @Test
    void shouldRejectSameIncrementalAuthorizationActionNoWithDifferentAmountBeforeChannelInvocation() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                idempotencyService,
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        service.createIncrementalAuthorization(followUpCommand("INC-AUTH-0001", new BigDecimal("3.00")));

        assertThatThrownBy(() -> service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0001", new BigDecimal("4.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isEqualTo(1);
        assertThat(recordService.followUpRecordCount).isEqualTo(1);
    }

    /**
     * 05F-INC-AUTH-06：Incremental Authorization 渠道结果必须经独立 Spring Bean 的 REQUIRES_NEW 事务入口。
     */
    @Test
    void shouldUseRequiresNewTransactionForIncrementalAuthorizationChannelResultPersistence() throws NoSuchMethodException {
        Method method = DefaultIncrementalAuthorizationChannelResultTransactionService.class.getMethod(
                "recordIncrementalAuthorizationChannelResult",
                IncrementalAuthorizationPreparationResultDTO.class,
                PaymentChannelInvokeResultDTO.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    /**
     * 05F-INC-AUTH-07：Incremental Authorization 入口自身不得用大事务包住渠道调用。
     */
    @Test
    void shouldNotWrapIncrementalAuthorizationEntryInDatabaseTransaction() throws NoSuchMethodException {
        Method method = PaymentTransactionServiceImpl.class.getMethod(
                "createIncrementalAuthorization", PaymentCreateCommandDTO.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    /**
     * 05F-INC-AUTH-08：同步结果、回调和主动查询并发成功时，授权金额更新只能由动作终态 CAS 成功的一方执行一次。
     */
    @Test
    void shouldIncreaseAuthorizedAmountOnlyOnceWhenIncrementalAuthorizationTerminalResultRaces() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        PaymentCreateCommandDTO commandDTO = followUpCommand("INC-AUTH-0001", new BigDecimal("3.00"));
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setTransactionId("TX-INCREMENTAL-AUTH-0001");
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        resultDTO.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());

        boolean first = recordService.completeIncrementalAuthorizationChannelResult(
                operationDO,
                recordService.findSourceOrderByTransactionId("TX202607230001"),
                commandDTO,
                routeService().route(commandDTO),
                preparedInvokeResult("TX-INCREMENTAL-AUTH-0001"),
                resultDTO,
                2);
        boolean callbackRace = recordService.completeIncrementalAuthorizationChannelResult(
                operationDO,
                recordService.findSourceOrderByTransactionId("TX202607230001"),
                commandDTO,
                routeService().route(commandDTO),
                preparedInvokeResult("TX-INCREMENTAL-AUTH-0001"),
                resultDTO,
                2);

        assertThat(first).isTrue();
        assertThat(callbackRace).isFalse();
        assertThat(recordService.incrementalAuthorizationAmountIncreaseCount).isEqualTo(1);
    }

    /**
     * 05F-INC-AUTH-09：未终态 Incremental Authorization 必须阻止新的增量授权、Capture 和 Authorization Cancel 进入渠道。
     */
    @Test
    void shouldBlockIncrementalAuthorizationCaptureAndCancelWhenIncrementalAuthorizationIsPending() {
        CapturingTransactionRecordService recordService = new CapturingTransactionRecordService();
        recordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        recordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        recordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        recordService.pendingIncrementalAuthorizationExists = true;
        InspectingPaymentChannelInvokeService channelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                new InMemoryTransactionIdempotencyService(),
                recordService,
                new CapturingTransactionEventOutboxService(),
                channelService);

        assertThatThrownBy(() -> service.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-0002", new BigDecimal("3.00"))))
                .as("05F pending incremental authorization must block later incremental authorization")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.capture(followUpCommand("CAPTURE-INC-AUTH-CONFLICT", new BigDecimal("5.00"))))
                .as("05F pending incremental authorization must block capture before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.voidPayment(voidCommand("VOID-INC-AUTH-CONFLICT")))
                .as("05F pending incremental authorization must block authorization cancel before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(channelService.paymentInvokeCount()).isZero();
        assertThat(recordService.followUpRecordCount).isZero();
    }

    /**
     * 05F-INC-AUTH-10：未终态 Capture 或 Authorization Cancel 必须阻止 Incremental Authorization 进入渠道。
     */
    @Test
    void shouldBlockIncrementalAuthorizationWhenCaptureOrCancelIsPending() {
        CapturingTransactionRecordService capturePendingRecordService = new CapturingTransactionRecordService();
        capturePendingRecordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        capturePendingRecordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        capturePendingRecordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        capturePendingRecordService.pendingCaptureExists = true;
        InspectingPaymentChannelInvokeService captureChannelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl captureConflictService = newService(
                new InMemoryTransactionIdempotencyService(),
                capturePendingRecordService,
                new CapturingTransactionEventOutboxService(),
                captureChannelService);

        assertThatThrownBy(() -> captureConflictService.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-CAPTURE-CONFLICT", new BigDecimal("3.00"))))
                .as("05F pending capture must block incremental authorization before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(captureChannelService.paymentInvokeCount()).isZero();

        CapturingTransactionRecordService cancelPendingRecordService = new CapturingTransactionRecordService();
        cancelPendingRecordService.sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
        cancelPendingRecordService.sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();
        cancelPendingRecordService.sourceAvailableCaptureAmount = new BigDecimal("12.34");
        cancelPendingRecordService.pendingVoidExists = true;
        InspectingPaymentChannelInvokeService cancelChannelService = new InspectingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl cancelConflictService = newService(
                new InMemoryTransactionIdempotencyService(),
                cancelPendingRecordService,
                new CapturingTransactionEventOutboxService(),
                cancelChannelService);

        assertThatThrownBy(() -> cancelConflictService.createIncrementalAuthorization(
                followUpCommand("INC-AUTH-CANCEL-CONFLICT", new BigDecimal("3.00"))))
                .as("05F pending authorization cancel must block incremental authorization before channel call")
                .isInstanceOf(RuntimeException.class);
        assertThat(cancelChannelService.paymentInvokeCount()).isZero();
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
        transactionInfoDTO.setSourceTransactionDateTime(TRANSACTION_TIME);
        transactionInfoDTO.setRootTransactionDateTime(TRANSACTION_TIME);
        commandDTO.setTransactionInfo(transactionInfoDTO);
        return commandDTO;
    }

    private PaymentCreateCommandDTO voidCommand(String merchantActionNo) {
        PaymentCreateCommandDTO commandDTO = followUpCommand(merchantActionNo, null);
        commandDTO.setCurrency(null);
        return commandDTO;
    }

    private PaymentChannelInvokeResultDTO preparedInvokeResult(String transactionId) {
        PaymentChannelInvokeResultDTO invokeResultDTO = new PaymentChannelInvokeResultDTO();
        invokeResultDTO.setRequestId("CR-" + transactionId);
        invokeResultDTO.setRequestStatus("SUCCESS");
        invokeResultDTO.setChannelRequest(new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest());
        invokeResultDTO.getChannelRequest().setTransactionId(transactionId);
        invokeResultDTO.getChannelRequest().setChannelOrderNo("TX202607230001");
        invokeResultDTO.getChannelRequest().setChannelTransactionId("CH-" + transactionId);
        invokeResultDTO.setChannelResponse(channelResponse(ChannelTradeStatus.SUCCESS));
        return invokeResultDTO;
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService) {
        return newService(idempotencyService, recordService, outboxService, channelService,
                preparationService(idempotencyService, recordService, outboxService, () -> {
                }));
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService) {
        return new PaymentTransactionServiceImpl(
                isoDictionaryService(),
                riskInvokeService,
                routeService(),
                channelService,
                exchangeRateService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService(),
                new AlwaysAvailableDistributedLockService());
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
        return newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService,
                resultTransactionService,
                capturePreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingCaptureChannelResultTransactionService(recordService),
                refundPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingRefundChannelResultTransactionService(recordService));
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService,
                                                     DefaultPaymentTransactionPreparationService preparationService,
                                                     PaymentChannelResultTransactionService resultTransactionService,
                                                     CaptureTransactionPreparationService capturePreparationService,
                                                     CaptureChannelResultTransactionService captureResultTransactionService,
                                                     RefundTransactionPreparationService refundPreparationService,
                                                     RefundChannelResultTransactionService refundResultTransactionService) {
        return newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService,
                resultTransactionService,
                capturePreparationService,
                captureResultTransactionService,
                refundPreparationService,
                refundResultTransactionService,
                voidPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingVoidChannelResultTransactionService(recordService),
                incrementalAuthorizationPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingIncrementalAuthorizationChannelResultTransactionService(recordService));
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService,
                                                     DefaultPaymentTransactionPreparationService preparationService,
                                                     PaymentChannelResultTransactionService resultTransactionService,
                                                     CaptureTransactionPreparationService capturePreparationService,
                                                     CaptureChannelResultTransactionService captureResultTransactionService,
                                                     RefundTransactionPreparationService refundPreparationService,
                                                     RefundChannelResultTransactionService refundResultTransactionService,
                                                     VoidTransactionPreparationService voidPreparationService,
                                                     VoidChannelResultTransactionService voidResultTransactionService) {
        return newService(
                idempotencyService,
                recordService,
                outboxService,
                channelService,
                preparationService,
                resultTransactionService,
                capturePreparationService,
                captureResultTransactionService,
                refundPreparationService,
                refundResultTransactionService,
                voidPreparationService,
                voidResultTransactionService,
                incrementalAuthorizationPreparationService(idempotencyService, recordService, outboxService, () -> {
                }),
                new CapturingIncrementalAuthorizationChannelResultTransactionService(recordService));
    }

    private PaymentTransactionServiceImpl newService(InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionRecordService recordService,
                                                     CapturingTransactionEventOutboxService outboxService,
                                                     PaymentChannelInvokeService channelService,
                                                     DefaultPaymentTransactionPreparationService preparationService,
                                                     PaymentChannelResultTransactionService resultTransactionService,
                                                     CaptureTransactionPreparationService capturePreparationService,
                                                     CaptureChannelResultTransactionService captureResultTransactionService,
                                                     RefundTransactionPreparationService refundPreparationService,
                                                     RefundChannelResultTransactionService refundResultTransactionService,
                                                     VoidTransactionPreparationService voidPreparationService,
                                                     VoidChannelResultTransactionService voidResultTransactionService,
                                                     IncrementalAuthorizationTransactionPreparationService incrementalAuthorizationPreparationService,
                                                     IncrementalAuthorizationChannelResultTransactionService incrementalAuthorizationResultTransactionService) {
        return new PaymentTransactionServiceImpl(
                isoDictionaryService(),
                riskDecision(PaymentRiskDecisionEnum.PASS),
                routeService(),
                channelService,
                preparationService,
                resultTransactionService,
                capturePreparationService,
                captureResultTransactionService,
                refundPreparationService,
                refundResultTransactionService,
                voidPreparationService,
                voidResultTransactionService,
                incrementalAuthorizationPreparationService,
                incrementalAuthorizationResultTransactionService,
                exchangeRateService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService(),
                new DefaultChannelTransactionStatusResolver(),
                new AlwaysAvailableDistributedLockService(),
                new PaymentRedisProperties());
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

    private CaptureTransactionPreparationService capturePreparationService(InMemoryTransactionIdempotencyService idempotencyService,
                                                                          CapturingTransactionRecordService recordService,
                                                                          CapturingTransactionEventOutboxService outboxService,
                                                                          Runnable afterCommit) {
        DefaultCaptureTransactionPreparationService delegate = new DefaultCaptureTransactionPreparationService(
                isoDictionaryService(),
                routeService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService());
        return (commandDTO, idempotencyKey) -> {
            CapturePreparationResultDTO resultDTO = delegate.prepareCapture(commandDTO, idempotencyKey);
            afterCommit.run();
            return resultDTO;
        };
    }

    private IncrementalAuthorizationTransactionPreparationService incrementalAuthorizationPreparationService(
            InMemoryTransactionIdempotencyService idempotencyService,
            CapturingTransactionRecordService recordService,
            CapturingTransactionEventOutboxService outboxService,
            Runnable afterCommit) {
        DefaultIncrementalAuthorizationTransactionPreparationService delegate =
                new DefaultIncrementalAuthorizationTransactionPreparationService(
                        isoDictionaryService(),
                        routeService(),
                        idempotencyService,
                        outboxService,
                        recordService,
                        new DefaultTransactionStateMachineService());
        return (commandDTO, idempotencyKey) -> {
            IncrementalAuthorizationPreparationResultDTO resultDTO =
                    delegate.prepareIncrementalAuthorization(commandDTO, idempotencyKey);
            afterCommit.run();
            return resultDTO;
        };
    }

    private RefundTransactionPreparationService refundPreparationService(InMemoryTransactionIdempotencyService idempotencyService,
                                                                        CapturingTransactionRecordService recordService,
                                                                        CapturingTransactionEventOutboxService outboxService,
                                                                        Runnable afterCommit) {
        DefaultRefundTransactionPreparationService delegate = new DefaultRefundTransactionPreparationService(
                isoDictionaryService(),
                routeService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService());
        return (commandDTO, idempotencyKey) -> {
            RefundPreparationResultDTO resultDTO = delegate.prepareRefund(commandDTO, idempotencyKey);
            afterCommit.run();
            return resultDTO;
        };
    }

    private VoidTransactionPreparationService voidPreparationService(InMemoryTransactionIdempotencyService idempotencyService,
                                                                    CapturingTransactionRecordService recordService,
                                                                    CapturingTransactionEventOutboxService outboxService,
                                                                    Runnable afterCommit) {
        DefaultVoidTransactionPreparationService delegate = new DefaultVoidTransactionPreparationService(
                isoDictionaryService(),
                routeService(),
                idempotencyService,
                outboxService,
                recordService,
                new DefaultTransactionStateMachineService());
        return (commandDTO, idempotencyKey) -> {
            VoidPreparationResultDTO resultDTO = delegate.prepareVoid(commandDTO, idempotencyKey);
            afterCommit.run();
            return resultDTO;
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
        return new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
                routeResultDTO.setChannelId(101L);
                routeResultDTO.setMidConfigId(1001L);
                routeResultDTO.setMidNo("TEST-MID");
                routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
                routeResultDTO.setRoutedCurrency(commandDTO.getCurrency());
                return routeResultDTO;
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode,
                                                 Long channelId,
                                                 Long midConfigId,
                                                 String fallbackMidNo) {
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed(channelCode);
                routeResultDTO.setChannelId(channelId);
                routeResultDTO.setMidConfigId(midConfigId);
                routeResultDTO.setMidNo(fallbackMidNo);
                return routeResultDTO;
            }
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

        /**
         * 组合商户、商户订单标识和交易类型，生成并发基线测试使用的稳定业务键。
         */
        @Override
        public String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType) {
            return String.join(":", merchantId, merchantOrderId, transactionType);
        }

        /**
         * 从内存记录表读取幂等事实，模拟数据库按作用域和业务键查询。
         */
        @Override
        public Optional<TransactionIdempotencyDO> find(String scope, String key) {
            return Optional.ofNullable(records.get(scope + ":" + key));
        }

        /**
         * 按平台交易号扫描已完成的初始幂等记录，支持后续交易恢复源交易。
         */
        @Override
        public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
            return records.values().stream()
                    .filter(record -> transactionId.equals(record.getTransactionId()))
                    .findFirst();
        }

        /**
         * 以同步临界区模拟数据库唯一键竞争；等待未完成占位提交后，重复请求返回失败。
         */
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

        /** 以同步临界区模拟 FAILED + version CAS 重新占用商户订单流。 */
        @Override
        public synchronized boolean tryRestartFailedFlow(TransactionIdempotencyDO existingRecord,
                                                         TransactionIdempotencyDO replacement) {
            String storageKey = existingRecord.getIdempotencyScope() + ":" + existingRecord.getIdempotencyKey();
            TransactionIdempotencyDO current = records.get(storageKey);
            if (current != existingRecord
                    || !PaymentTransactionStatusEnum.FAILED.getCode().equals(current.getTransactionStatus())) {
                return false;
            }
            replacement.setId(existingRecord.getId());
            replacement.setVersion(existingRecord.getVersion() == null ? 1 : existingRecord.getVersion() + 1);
            records.put(storageKey, replacement);
            return true;
        }

        /** 模拟渠道回调/主动查询把请求幂等记录和商户订单流守卫同时推进到终态。 */
        @Override
        public synchronized int synchronizeInitialTransactionStatus(String transactionId, String transactionStatus) {
            int updated = 0;
            for (TransactionIdempotencyDO record : records.values()) {
                if (transactionId.equals(record.getTransactionId())
                        && !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(record.getTransactionStatus())
                        && !PaymentTransactionStatusEnum.FAILED.getCode().equals(record.getTransactionStatus())) {
                    record.setTransactionStatus(transactionStatus);
                    updated++;
                }
            }
            return updated;
        }

        /**
         * 完成占位记录并唤醒等待线程，模拟交易事实与幂等快照一并提交。
         */
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

        /**
         * 构造尚无业务结果的 PROCESSING 占位记录，供并发请求竞争唯一键。
         */
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
                                          int followUpRecordCount,
                                          int outboxSavedCount,
                                          int idempotencyCompletedCount,
                                          int channelRequestCount) {
    }

    private static class CommittedFactsView {

        private final InMemoryTransactionIdempotencyService idempotencyService;

        private final CapturingTransactionRecordService recordService;

        private final CapturingTransactionEventOutboxService outboxService;

        private volatile CommittedFactsSnapshot committedSnapshot = new CommittedFactsSnapshot(0, 0, 0, 0, 0);

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
                    recordService.followUpRecordCount,
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

        /**
         * 最近一次后续交易记录携带的风控决策，用于验证准备事务保存结果。
         */
        private PaymentRiskDecisionEnum lastFollowUpRiskDecision;

        private boolean failInitialRecord;

        private boolean pendingCaptureExists;

        private String pendingCaptureStage = PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode();

        private boolean markFollowUpProcessingForNewCaptures;

        private final ReentrantLock sourceOrderLock = new ReentrantLock();

        private TransactionOperationDO persistedNonTerminalCapture;

        private String sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();

        private String sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();

        private BigDecimal sourceAvailableCaptureAmount = new BigDecimal("12.34");

        private BigDecimal sourceAvailableRefundAmount = BigDecimal.ZERO;

        private BigDecimal pendingRefundAmount = BigDecimal.ZERO;

        private boolean pendingVoidExists;

        private boolean pendingIncrementalAuthorizationExists;

        private int incrementalAuthorizationAmountIncreaseCount;

        private TransactionOperationDO lastInitialOperation;

        private TransactionOperationDO lastFollowUpOperation;

        /**
         * 将初始交易转换为内存动作单并累计落库次数，可按用例配置在写入后注入失败。
         */
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

        /**
         * 把渠道返回的状态和渠道标识补写到已记录动作单，模拟独立结果事务成功。
         */
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

        /**
         * 固定返回未命中；该基线替身只允许通过源交易号读取主单。
         */
        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return null;
        }

        /**
         * 仅为固定源交易构造金额与状态快照，供请款、退款和撤销额度校验。
         */
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
            orderDO.setRefundedAmount(BigDecimal.ZERO);
            orderDO.setAvailableCaptureAmount(sourceAvailableCaptureAmount);
            orderDO.setAvailableRefundAmount(sourceAvailableRefundAmount);
            orderDO.setCurrencyExponent(2);
            orderDO.setTransactionDateTime(TRANSACTION_TIME);
            orderDO.setTransactionTimeZone("Asia/Shanghai");
            orderDO.setVersion(0);
            return orderDO;
        }

        /**
         * 按用例需要获取本地互斥锁后返回源主单，模拟数据库行锁保护累计额度计算。
         */
        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            if (markFollowUpProcessingForNewCaptures) {
                sourceOrderLock.lock();
            }
            return findSourceOrderByTransactionId("TX202607230001");
        }

        /**
         * 仅为固定源交易构造渠道动作事实，供后续交易复用原渠道身份。
         */
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
            operationDO.setChannelId(101L);
            operationDO.setChannelMidConfigId(1001L);
            operationDO.setChannelTerminalId("TEST-MID");
            operationDO.setChannelOrderNo(sourceTransactionId);
            operationDO.setChannelTransactionId("CH-" + sourceTransactionId);
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            return operationDO;
        }

        /**
         * 仅返回本替身已经记录且商户订单匹配的初始动作，用于重放结果查询。
         */
        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                          String merchantOrderNo,
                                                                          String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          LocalDateTime rootTransactionDateTime) {
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

        /**
         * 返回预置或刚持久化的在途请款，并在读取完成后释放模拟的源主单行锁。
         */
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

        /**
         * 按预设在途退款金额构造 PROCESSING 动作，供可退款金额计算纳入未决占用。
         */
        @Override
        public List<TransactionOperationDO> findNonTerminalRefunds(String merchantId,
                                                                   String operationId,
                                                                   LocalDateTime beginTime,
                                                                   LocalDateTime endTime) {
            if (!MERCHANT_ID.equals(merchantId)
                    || !"OP202607230001".equals(operationId)
                    || pendingRefundAmount == null
                    || pendingRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return List.of();
            }
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(199L);
            operationDO.setOperationId(operationId);
            operationDO.setTransactionId("TX-REFUND-UNKNOWN");
            operationDO.setSourceTransactionId("TX202607230001");
            operationDO.setMerchantId(merchantId);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId("REFUND-PENDING");
            operationDO.setMerchantOperationNo("REFUND-PENDING");
            operationDO.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
            operationDO.setTransactionAmount(pendingRefundAmount);
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelOrderNo("TX202607230001");
            operationDO.setChannelTransactionId("CH-REFUND-UNKNOWN");
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            operationDO.setVersion(0);
            return List.of(operationDO);
        }

        /**
         * 按开关返回一笔在途撤销，验证重复撤销在渠道结果未知时被拒绝。
         */
        @Override
        public List<TransactionOperationDO> findNonTerminalVoids(String merchantId,
                                                                 String operationId,
                                                                 LocalDateTime beginTime,
                                                                 LocalDateTime endTime) {
            if (!MERCHANT_ID.equals(merchantId)
                    || !"OP202607230001".equals(operationId)
                    || !pendingVoidExists) {
                return List.of();
            }
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(299L);
            operationDO.setOperationId(operationId);
            operationDO.setTransactionId("TX-VOID-UNKNOWN");
            operationDO.setSourceTransactionId("TX202607230001");
            operationDO.setMerchantId(merchantId);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId("VOID-PENDING");
            operationDO.setMerchantOperationNo("VOID-PENDING");
            operationDO.setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
            operationDO.setTransactionAmount(new BigDecimal("12.34"));
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelOrderNo("TX202607230001");
            operationDO.setChannelTransactionId("CH-VOID-UNKNOWN");
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            operationDO.setVersion(0);
            return List.of(operationDO);
        }

        /**
         * 按开关返回一笔在途增量授权，验证并发增额不能重复占用授权额度。
         */
        @Override
        public List<TransactionOperationDO> findNonTerminalIncrementalAuthorizations(String merchantId,
                                                                                     String operationId,
                                                                                     LocalDateTime beginTime,
                                                                                     LocalDateTime endTime) {
            if (!MERCHANT_ID.equals(merchantId)
                    || !"OP202607230001".equals(operationId)
                    || !pendingIncrementalAuthorizationExists) {
                return List.of();
            }
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setId(399L);
            operationDO.setOperationId(operationId);
            operationDO.setTransactionId("TX-INCREMENTAL-AUTH-UNKNOWN");
            operationDO.setSourceTransactionId("TX202607230001");
            operationDO.setMerchantId(merchantId);
            operationDO.setMerchantOrderNo(MERCHANT_ORDER_NO);
            operationDO.setMerchantOrderId("INCREMENTAL-AUTH-PENDING");
            operationDO.setMerchantOperationNo("INCREMENTAL-AUTH-PENDING");
            operationDO.setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
            operationDO.setTransactionAmount(new BigDecimal("3.00"));
            operationDO.setTransactionCurrency("USD");
            operationDO.setChannelCode("MPGS");
            operationDO.setChannelOrderNo("TX202607230001");
            operationDO.setChannelTransactionId("CH-INCREMENTAL-AUTH-UNKNOWN");
            operationDO.setTransactionDateTime(TRANSACTION_TIME);
            operationDO.setVersion(0);
            return List.of(operationDO);
        }

        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return null;
        }

        /**
         * 按开关返回一笔渠道结果未知的请款，模拟恢复任务的待匹配扫描。
         */
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
            if (recordDTO.getChannelInvokeResultDTO() != null && recordDTO.getChannelInvokeResultDTO().getChannelRequest() != null) {
                operationDO.setChannelCode(recordDTO.getChannelInvokeResultDTO().getChannelRequest().getChannelCode());
                operationDO.setChannelOrderNo(recordDTO.getChannelInvokeResultDTO().getChannelRequest().getChannelOrderNo());
                operationDO.setChannelTransactionId(recordDTO.getChannelInvokeResultDTO().getChannelRequest().getChannelTransactionId());
            }
            operationDO.setTransactionDateTime(recordDTO.getCommandDTO().getTransactionDateTime());
            operationDO.setVersion(0);
            return operationDO;
        }

        /**
         * 捕获后续交易及风控结论，并在并发用例中把新请款登记为在途后释放源主单锁。
         */
        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
            try {
                followUpRecordCount++;
                lastFollowUpRiskDecision = recordDTO.getRiskDecisionEnum();
                lastFollowUpOperation = pendingCaptureFromRecord(recordDTO);
                if (recordDTO.getChannelInvokeResultDTO() != null && recordDTO.getChannelInvokeResultDTO().getChannelRequest() != null) {
                    channelRequestCount++;
                }
                if (markFollowUpProcessingForNewCaptures
                        && PaymentTransactionTypeEnum.CAPTURE.getCode().equals(recordDTO.getResultDTO().getTransactionType())) {
                    persistedNonTerminalCapture = lastFollowUpOperation;
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

        /**
         * 固定拒绝回调终态更新，因为该替身只验证同步渠道结果的一致性边界。
         */
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

        /**
         * 将同步渠道结果写回最近的后续动作单，模拟结果事务成功并返回已更新。
         */
        @Override
        public boolean completeCaptureChannelResult(TransactionOperationDO operationDO,
                                                    TransactionOrderDO sourceOrderDO,
                                                    PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResultDTO,
                                                    PaymentChannelInvokeResultDTO invokeResultDTO,
                                                    PaymentCreateResultDTO resultDTO,
                                                    int currencyExponent) {
            if (lastFollowUpOperation != null) {
                lastFollowUpOperation.setTransactionStatus(resultDTO.getStatus());
                lastFollowUpOperation.setProcessStage(resultDTO.getProcessStage());
                if (invokeResultDTO != null && invokeResultDTO.getChannelRequest() != null) {
                    lastFollowUpOperation.setChannelOrderNo(invokeResultDTO.getChannelRequest().getChannelOrderNo());
                    lastFollowUpOperation.setChannelTransactionId(invokeResultDTO.getChannelRequest().getChannelTransactionId());
                }
                if (invokeResultDTO != null && invokeResultDTO.getChannelResponse() != null) {
                    lastFollowUpOperation.setChannelStatus(invokeResultDTO.getChannelResponse().getRawChannelStatus());
                }
            }
            return true;
        }

        /** 测试桩直接确认退款渠道结果已完成，避免引入真实持久化依赖。 */
        @Override
        public boolean completeRefundChannelResult(TransactionOperationDO operationDO,
                                                   TransactionOrderDO sourceOrderDO,
                                                   PaymentCreateCommandDTO commandDTO,
                                                   PaymentRouteResultDTO routeResultDTO,
                                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                                   PaymentCreateResultDTO resultDTO,
                                                   int currencyExponent) {
            return completeCaptureChannelResult(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
        }

        /**
         * 复用后续交易结果写回逻辑，模拟撤销渠道结果事务。
         */
        @Override
        public boolean completeVoidChannelResult(TransactionOperationDO operationDO,
                                                 TransactionOrderDO sourceOrderDO,
                                                 PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO invokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 int currencyExponent) {
            return completeCaptureChannelResult(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
        }

        /**
         * 拒绝覆盖既有终态；首次成功时写回动作状态并累计一次授权金额增额。
         */
        @Override
        public boolean completeIncrementalAuthorizationChannelResult(TransactionOperationDO operationDO,
                                                                     TransactionOrderDO sourceOrderDO,
                                                                     PaymentCreateCommandDTO commandDTO,
                                                                     PaymentRouteResultDTO routeResultDTO,
                                                                     PaymentChannelInvokeResultDTO invokeResultDTO,
                                                                     PaymentCreateResultDTO resultDTO,
                                                                     int currencyExponent) {
            if (operationDO != null
                    && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                    || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus()))) {
                return false;
            }
            boolean changed = completeCaptureChannelResult(
                    operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
            if (changed && operationDO != null) {
                operationDO.setTransactionStatus(resultDTO.getStatus());
                operationDO.setProcessStage(resultDTO.getProcessStage());
            }
            if (changed && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
                incrementalAuthorizationAmountIncreaseCount++;
            }
            return changed;
        }

        /**
         * 固定返回未更新，当前基线不通过该替身验证匹配元数据持久化。
         */
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

        /**
         * 固定返回未更新，避免商户响应日志影响交易一致性断言。
         */
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

        /**
         * 统计初始渠道结果事务，并可注入事务失败；成功时委托记录替身补写渠道结果。
         */
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

        @Override
        public boolean claimInitialChannelSubmission(String requestId, LocalDateTime transactionDateTime) {
            return true;
        }

        @Override
        public boolean recordInitialPreChannelFailure(PaymentCreateCommandDTO commandDTO,
                                                      PaymentRouteResultDTO routeResultDTO,
                                                      PaymentChannelInvokeResultDTO invokeResultDTO,
                                                      PaymentCreateResultDTO resultDTO,
                                                      PaymentRiskDecisionEnum riskDecisionEnum,
                                                      int currencyExponent) {
            recordInitialChannelResult(commandDTO, routeResultDTO, invokeResultDTO, resultDTO,
                    riskDecisionEnum, currencyExponent);
            return true;
        }

        @Override
        public void markThreeDsIndicator(String transactionId,
                                         LocalDateTime transactionDateTime,
                                         String indicator) {
            // This baseline fixture does not persist payment-method capability metadata.
        }
    }

    private static class CapturingCaptureChannelResultTransactionService implements CaptureChannelResultTransactionService {

        private final TransactionRecordService recordService;

        private int recordCount;

        private boolean failResultRecord;

        private CapturingCaptureChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        /**
         * 统计请款结果事务并可注入失败；成功时把准备快照交给记录替身完成状态更新。
         */
        @Override
        public void recordCaptureChannelResult(CapturePreparationResultDTO preparationResultDTO,
                                               PaymentChannelInvokeResultDTO invokeResultDTO) {
            recordCount++;
            if (failResultRecord) {
                throw new IllegalStateException("simulated capture result transaction failure");
            }
            recordService.completeCaptureChannelResult(
                    preparationResultDTO.getResultDTO() == null
                            ? null
                            : ((CapturingTransactionRecordService) recordService).lastFollowUpOperation,
                    preparationResultDTO.getSourceOrderDO(),
                    preparationResultDTO.getCommandDTO(),
                    preparationResultDTO.getRouteResultDTO(),
                    invokeResultDTO,
                    preparationResultDTO.getResultDTO(),
                    preparationResultDTO.getCurrencyExponent());
        }
    }

    private static class CapturingRefundChannelResultTransactionService implements RefundChannelResultTransactionService {

        private final TransactionRecordService recordService;

        private int recordCount;

        private boolean failResultRecord;

        private CapturingRefundChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        /**
         * 统计退款结果事务并可注入失败；成功时委托记录替身写回退款渠道结果。
         */
        @Override
        public void recordRefundChannelResult(RefundPreparationResultDTO preparationResultDTO,
                                              PaymentChannelInvokeResultDTO invokeResultDTO) {
            recordCount++;
            if (failResultRecord) {
                throw new IllegalStateException("simulated refund result transaction failure");
            }
            recordService.completeRefundChannelResult(
                    preparationResultDTO.getResultDTO() == null
                            ? null
                            : ((CapturingTransactionRecordService) recordService).lastFollowUpOperation,
                    preparationResultDTO.getSourceOrderDO(),
                    preparationResultDTO.getCommandDTO(),
                    preparationResultDTO.getRouteResultDTO(),
                    invokeResultDTO,
                    preparationResultDTO.getResultDTO(),
                    preparationResultDTO.getCurrencyExponent());
        }
    }

    private static class CapturingVoidChannelResultTransactionService implements VoidChannelResultTransactionService {

        private final TransactionRecordService recordService;

        private int recordCount;

        private boolean failResultRecord;

        private CapturingVoidChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        /**
         * 统计撤销结果事务并可注入失败；成功时委托记录替身写回撤销渠道结果。
         */
        @Override
        public void recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                            PaymentChannelInvokeResultDTO invokeResultDTO) {
            recordCount++;
            if (failResultRecord) {
                throw new IllegalStateException("simulated void result transaction failure");
            }
            recordService.completeVoidChannelResult(
                    preparationResultDTO.getResultDTO() == null
                            ? null
                            : ((CapturingTransactionRecordService) recordService).lastFollowUpOperation,
                    preparationResultDTO.getSourceOrderDO(),
                    preparationResultDTO.getCommandDTO(),
                    preparationResultDTO.getRouteResultDTO(),
                    invokeResultDTO,
                    preparationResultDTO.getResultDTO(),
                    preparationResultDTO.getCurrencyExponent());
        }
    }

    private static class CapturingIncrementalAuthorizationChannelResultTransactionService
            implements IncrementalAuthorizationChannelResultTransactionService {

        private final TransactionRecordService recordService;

        private int recordCount;

        private boolean failResultRecord;

        private CapturingIncrementalAuthorizationChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
        public void recordIncrementalAuthorizationChannelResult(
                IncrementalAuthorizationPreparationResultDTO preparationResultDTO,
                PaymentChannelInvokeResultDTO invokeResultDTO) {
            recordCount++;
            if (failResultRecord) {
                throw new IllegalStateException("simulated incremental authorization result transaction failure");
            }
            recordService.completeIncrementalAuthorizationChannelResult(
                    preparationResultDTO.getResultDTO() == null
                            ? null
                            : ((CapturingTransactionRecordService) recordService).lastFollowUpOperation,
                    preparationResultDTO.getSourceOrderDO(),
                    preparationResultDTO.getCommandDTO(),
                    preparationResultDTO.getRouteResultDTO(),
                    invokeResultDTO,
                    preparationResultDTO.getResultDTO(),
                    preparationResultDTO.getCurrencyExponent());
        }
    }

    private static class CapturingTransactionEventOutboxService implements TransactionEventOutboxService {

        private int savedCount;

        /**
         * 仅累计保存次数，用于断言交易事实提交时同步创建了一条 Outbox 事件。
         */
        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            savedCount++;
        }

        /**
         * 固定返回无待投递事件，隔离一致性基线与异步中继行为。
         */
        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of();
        }

        /**
         * 固定模拟发送状态更新成功；当前用例不检查 Outbox 乐观锁。
         */
        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            return true;
        }

        /**
         * 固定模拟失败状态更新成功；当前用例不检查重试调度持久化。
         */
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

        private String lastTargetTransactionId;

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response) {
            this(response, () -> {
            });
        }

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response, Runnable beforeInvokeAssertion) {
            this.response = response;
            this.beforeInvokeAssertion = beforeInvokeAssertion;
        }

        /**
         * 执行渠道调用前断言，捕获源渠道交易号，并可对第一次调用注入阻塞或超时。
         */
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
            lastTargetTransactionId = commandDTO.getTransactionInfo() == null
                    ? null : commandDTO.getTransactionInfo().getSourceChannelTransactionId();
            if (timeoutOnFirstPayment && invokeCount.get() == 1) {
                resultDTO.setRequestStatus("TIMEOUT");
                resultDTO.setExceptionType(ChannelTimeoutException.class.getSimpleName());
                resultDTO.setExceptionMessage("simulated timeout");
                resultDTO.setOutcomeUncertain(true);
                throw new PaymentChannelInvokeException(resultDTO, new ChannelTimeoutException("simulated timeout"));
            }
            resultDTO.setChannelResponse(response);
            return resultDTO;
        }

        /**
         * 复用已准备的渠道请求标识执行并发观察，并可对第一次调用注入阻塞或超时。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    PaymentPreparedChannelRequestDTO preparedChannelRequest) {
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
            resultDTO.setRequestId(preparedChannelRequest.getRequestId());
            resultDTO.setRequestStatus("SUCCESS");
            resultDTO.setChannelRequest(new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest());
            resultDTO.getChannelRequest().setChannelCode(routeResult.getChannelCode());
            resultDTO.getChannelRequest().setOperationId(operationId);
            resultDTO.getChannelRequest().setTransactionId(transactionId);
            resultDTO.getChannelRequest().setChannelOrderNo(preparedChannelRequest.getChannelOrderNo());
            resultDTO.getChannelRequest().setChannelTransactionId(preparedChannelRequest.getChannelTransactionId());
            resultDTO.getChannelRequest().setAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
            resultDTO.getChannelRequest().setCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
            lastTargetTransactionId = commandDTO.getTransactionInfo() == null
                    ? null : commandDTO.getTransactionInfo().getSourceChannelTransactionId();
            if (timeoutOnFirstPayment && invokeCount.get() == 1) {
                resultDTO.setRequestStatus("TIMEOUT");
                resultDTO.setExceptionType(ChannelTimeoutException.class.getSimpleName());
                resultDTO.setExceptionMessage("simulated timeout");
                resultDTO.setOutcomeUncertain(true);
                throw new PaymentChannelInvokeException(resultDTO, new ChannelTimeoutException("simulated timeout"));
            }
            if (response != null) {
                response.setChannelOrderNo(preparedChannelRequest.getChannelOrderNo());
                response.setChannelTransactionId(preparedChannelRequest.getChannelTransactionId());
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

        /**
         * 恢复任务不创建初始交易，空实现用于保持预置的待匹配事实不变。
         */
        @Override
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        }

        /**
         * 恢复任务不补写同步初始结果，空实现用于隔离恢复路径。
         */
        @Override
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
        }

        /**
         * 返回预置待匹配主单，供恢复服务读取当前 PROCESSING 状态。
         */
        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        /**
         * 返回同一预置主单，模拟按源交易号反查命中。
         */
        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            return order;
        }

        /**
         * 返回同一预置主单，模拟恢复事务已取得数据库行锁。
         */
        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        /**
         * 返回预置动作单，供恢复服务复用已落库的渠道身份。
         */
        @Override
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            return operation;
        }

        /**
         * 返回唯一预置动作单，模拟商户订单维度查询命中。
         */
        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                          String merchantOrderNo,
                                                                          String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          LocalDateTime rootTransactionDateTime) {
            return List.of(operation);
        }

        /**
         * 返回唯一预置初始动作单，避免用例依赖真实分表查询。
         */
        @Override
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            return List.of(operation);
        }

        /**
         * 固定返回无在途请款，隔离恢复状态推进与额度占用检查。
         */
        @Override
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            return List.of();
        }

        /**
         * 返回预置动作单，模拟按渠道订单号和渠道交易号反查命中。
         */
        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return operation;
        }

        /**
         * 返回唯一预置待匹配动作单，模拟恢复调度的一批扫描结果。
         */
        @Override
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of(operation);
        }

        /**
         * 恢复任务不创建后续交易，空实现用于限制用例观察范围。
         */
        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        }

        /**
         * 断言恢复结果符合预期终态，并捕获终态与失败原因供测试核对。
         */
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

        /**
         * 固定模拟匹配元数据更新成功，使用例聚焦终态恢复结果。
         */
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

        /**
         * 固定模拟商户响应日志更新成功，当前恢复用例不检查日志内容。
         */
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

        private String channelTransactionId;

        private QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus queryStatus) {
            this.queryStatus = queryStatus;
        }

        /**
         * 捕获普通查询调用的平台与渠道订单号，并返回预设渠道状态。
         */
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

        /**
         * 捕获已准备请求中的完整渠道身份，并返回预设渠道状态用于恢复终态。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO preparedChannelRequest) {
            invokeCount.incrementAndGet();
            this.transactionId = transactionId;
            this.channelOrderNo = preparedChannelRequest.getChannelOrderNo();
            this.channelTransactionId = preparedChannelRequest.getChannelTransactionId();
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId(preparedChannelRequest.getRequestId());
            resultDTO.setRequestStatus("SUCCESS");
            ChannelPaymentResponse response = new ChannelPaymentResponse();
            response.setChannelCode(routeResult.getChannelCode());
            response.setChannelOrderNo(preparedChannelRequest.getChannelOrderNo());
            response.setChannelTransactionId(preparedChannelRequest.getChannelTransactionId());
            response.setChannelTradeStatus(queryStatus.getCode());
            response.setRawChannelStatus(queryStatus.getCode());
            response.setChannelResponseCode(ChannelTradeStatus.SUCCESS == queryStatus ? "00" : "05");
            response.setChannelResponseMessage(ChannelTradeStatus.SUCCESS == queryStatus ? "Approved by query" : "Declined by query");
            resultDTO.setChannelResponse(response);
            return resultDTO;
        }
    }
}

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
 * @description : BUG-VERIFY-001-001 修复前一致性基线测试，记录当前渠道调用、幂等、交易事实和恢复能力的 P0 缺口。
 * @status : create
 */
class PaymentTransactionConsistencyBaselineTests {

    /**
     * MERCHANT ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MERCHANT_ID = "200001";

    /**
     * MERCHANT ORDER NO 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MERCHANT_ORDER_NO = "M202607230001";

    /**
     * MERCHANT ORDER ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

        /**
         * records 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Map<String, TransactionIdempotencyDO> records = new LinkedHashMap<>();

        /**
         * completed Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int completedCount;

        @Override
        /**
         * 构建 build Transaction Operation Key 对应的领域对象、请求对象或日志对象。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderId merchant Order Id 输入值，含义由调用方法名称和所属业务对象限定
         * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType) {
            return String.join(":", merchantId, merchantOrderId, transactionType);
        }

        @Override
        /**
         * 构建 build Initial Transaction Key 对应的领域对象、请求对象或日志对象。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @return 转换或构建后的目标对象
         */
        public String buildInitialTransactionKey(String merchantId, String merchantOrderNo) {
            return String.join(":", merchantId, merchantOrderNo, "INITIAL");
        }

        @Override
        /**
         * 查询 find 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param scope scope 输入值，含义由调用方法名称和所属业务对象限定
         * @param key key 输入值，含义由调用方法名称和所属业务对象限定
         * @return 解析或查询得到的业务值
         */
        public Optional<TransactionIdempotencyDO> find(String scope, String key) {
            return Optional.ofNullable(records.get(scope + ":" + key));
        }

        @Override
        /**
         * 查询 find Initial Transaction 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 解析或查询得到的业务值
         */
        public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
            return records.values().stream()
                    .filter(record -> transactionId.equals(record.getTransactionId()))
                    .findFirst();
        }

        @Override
        /**
         * 完成 try Begin 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param record record 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
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
/**
 * 推进 complete 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param scope scope 输入值，含义由调用方法名称和所属业务对象限定
 * @param key key 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param transactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param transactionAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
 * @param transactionCurrency 币种代码，格式为 ISO 4217 三位大写字母
 * @param resultSnapshot result Snapshot 输入值，含义由调用方法名称和所属业务对象限定
 */
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
/**
 * 完成 new Processing Record 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param scope scope 输入值，含义由调用方法名称和所属业务对象限定
 * @param key key 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
 * @param merchantOrderId merchant Order Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
 * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param timeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param requestFingerprint request Fingerprint 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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

        /**
         * idempotency Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final InMemoryTransactionIdempotencyService idempotencyService;

        /**
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final CapturingTransactionRecordService recordService;

        /**
         * outbox Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
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

        /**
         * initial Record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int initialRecordCount;

        /**
         * follow Up Record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int followUpRecordCount;

        /**
         * channel Request Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int channelRequestCount;

        /**
         * fail Initial Record 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean failInitialRecord;

        /**
         * pending Capture Exists 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean pendingCaptureExists;

        /**
         * pending Capture Stage 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String pendingCaptureStage = PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode();

        /**
         * mark Follow Up Processing For New Captures 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean markFollowUpProcessingForNewCaptures;

        /**
         * source Order Lock 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final ReentrantLock sourceOrderLock = new ReentrantLock();

        /**
         * persisted Non Terminal Capture 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private TransactionOperationDO persistedNonTerminalCapture;

        /**
         * source Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String sourceStatus = PaymentTransactionStatusEnum.SUCCESS.getCode();

        /**
         * source Transaction Type 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();

        /**
         * source Available Capture Amount 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private BigDecimal sourceAvailableCaptureAmount = new BigDecimal("12.34");

        /**
         * source Available Refund Amount 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private BigDecimal sourceAvailableRefundAmount = BigDecimal.ZERO;

        /**
         * pending Refund Amount 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private BigDecimal pendingRefundAmount = BigDecimal.ZERO;

        /**
         * pending Void Exists 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean pendingVoidExists;

        /**
         * pending Incremental Authorization Exists 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean pendingIncrementalAuthorizationExists;

        /**
         * incremental Authorization Amount Increase Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int incrementalAuthorizationAmountIncreaseCount;

        /**
         * last Initial Operation 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private TransactionOperationDO lastInitialOperation;

        /**
         * last Follow Up Operation 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private TransactionOperationDO lastFollowUpOperation;

        @Override
/**
 * 写入或更新 record Initial Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
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
/**
 * 推进 complete Initial Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
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
        /**
         * 查询 find Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 解析或查询得到的业务值
         */
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return null;
        }

        @Override
        /**
         * 查询 find Source Order By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 解析或查询得到的业务值
         */
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

        @Override
        /**
         * 完成 lock Order 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 当前方法计算或转换后的业务结果
         */
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            if (markFollowUpProcessingForNewCaptures) {
                sourceOrderLock.lock();
            }
            return findSourceOrderByTransactionId("TX202607230001");
        }

        @Override
        /**
         * 查询 find Source Operation By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
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
/**
 * 查询 find Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @return 渠道 API 操作类型或平台操作映射结果
 */
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
        /**
         * 查询 find Initial Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            if (lastInitialOperation == null
                    || !lastInitialOperation.getMerchantId().equals(merchantId)
                    || !lastInitialOperation.getMerchantOrderNo().equals(merchantOrderNo)) {
                return List.of();
            }
            return List.of(lastInitialOperation);
        }

        @Override
/**
 * 查询 find Non Terminal Captures 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
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
/**
 * 查询 find Non Terminal Refunds 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
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

        @Override
/**
 * 查询 find Non Terminal Voids 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
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

        @Override
/**
 * 查询 find Non Terminal Incremental Authorizations 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
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
        /**
         * 查询 find Operation By Channel Transaction 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
         * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return null;
        }

        @Override
/**
 * 完成 list Pending Channel Match 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
        /**
         * 写入或更新 record Follow Up Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param recordDTO record DTO 输入值，含义由调用方法名称和所属业务对象限定
         */
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
            try {
                followUpRecordCount++;
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

        @Override
/**
 * 推进 complete By Channel Callback 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param orderDO order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param failReasonCode fail Reason Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param failReasonMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 当前方法计算或转换后的业务结果
 */
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
/**
 * 推进 complete Capture Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 推进 complete Refund Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @return 当前方法计算或转换后的业务结果
 */
        public boolean completeRefundChannelResult(TransactionOperationDO operationDO,
                                                   TransactionOrderDO sourceOrderDO,
                                                   PaymentCreateCommandDTO commandDTO,
                                                   PaymentRouteResultDTO routeResultDTO,
                                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                                   PaymentCreateResultDTO resultDTO,
                                                   int currencyExponent) {
            return completeCaptureChannelResult(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
        }

        @Override
/**
 * 推进 complete Void Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @return 当前方法计算或转换后的业务结果
 */
        public boolean completeVoidChannelResult(TransactionOperationDO operationDO,
                                                 TransactionOrderDO sourceOrderDO,
                                                 PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO invokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 int currencyExponent) {
            return completeCaptureChannelResult(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
        }

        @Override
/**
 * 推进 complete Incremental Authorization Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 写入或更新 update Channel Match 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param matchResult match Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestId request Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param nextMatchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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
        /**
         * 写入或更新 update Merchant Api Response Log 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return false;
        }
    }

    private static class CapturingPaymentChannelResultTransactionService implements PaymentChannelResultTransactionService {

        /**
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int recordCount;

        /**
         * fail Result Record 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean failResultRecord;

        private CapturingPaymentChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
/**
 * 写入或更新 record Initial Channel Result 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
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

    private static class CapturingCaptureChannelResultTransactionService implements CaptureChannelResultTransactionService {

        /**
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int recordCount;

        /**
         * fail Result Record 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean failResultRecord;

        private CapturingCaptureChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
/**
 * 写入或更新 record Capture Channel Result 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param preparationResultDTO preparation Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 */
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

        /**
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int recordCount;

        /**
         * fail Result Record 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean failResultRecord;

        private CapturingRefundChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
/**
 * 写入或更新 record Refund Channel Result 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param preparationResultDTO preparation Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 */
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

        /**
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * record Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int recordCount;

        /**
         * fail Result Record 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean failResultRecord;

        private CapturingVoidChannelResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
/**
 * 写入或更新 record Void Channel Result 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param preparationResultDTO preparation Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 */
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

        /**
         * saved Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int savedCount;

        @Override
        /**
         * 写入或更新 save 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
         */
        public void save(TransactionEventOutboxDO eventDO) {
            savedCount++;
        }

        @Override
        /**
         * 完成 list Due Events 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param now now 输入值，含义由调用方法名称和所属业务对象限定
         * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        /**
         * 推进 mark Sent 对应的状态或处理结果，并保留后续查询所需信息。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
         * @param sentTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @return 当前方法计算或转换后的业务结果
         */
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            return true;
        }

        @Override
/**
 * 推进 mark Failed 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param nextRetryTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            return true;
        }
    }

    private static class InspectingPaymentChannelInvokeService implements PaymentChannelInvokeService {

        /**
         * response 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private ChannelPaymentResponse response;

        /**
         * before Invoke Assertion 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Runnable beforeInvokeAssertion;

        /**
         * invoke Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final AtomicInteger invokeCount = new AtomicInteger();

        /**
         * timeout On First Payment 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean timeoutOnFirstPayment;

        /**
         * block First Payment 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean blockFirstPayment;

        /**
         * last Target Transaction Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String lastTargetTransactionId;

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response) {
            this(response, () -> {
            });
        }

        private InspectingPaymentChannelInvokeService(ChannelPaymentResponse response, Runnable beforeInvokeAssertion) {
            this.response = response;
            this.beforeInvokeAssertion = beforeInvokeAssertion;
        }

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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
                throw new PaymentChannelInvokeException(resultDTO, new ChannelTimeoutException("simulated timeout"));
            }
            resultDTO.setChannelResponse(response);
            return resultDTO;
        }

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param preparedChannelRequest prepared Channel Request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
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

        /**
         * operation 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionOperationDO operation;

        /**
         * order 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionOrderDO order;

        /**
         * expected Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final String expectedStatus;

        /**
         * completed Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String completedStatus;

        /**
         * completed Fail Reason 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String completedFailReason;

        private RecoverableTransactionRecordService(String expectedStatus) {
            this.expectedStatus = expectedStatus;
            this.operation = pendingOperation();
            this.order = pendingOrder();
        }

        @Override
/**
 * 写入或更新 record Initial Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        }

        @Override
/**
 * 推进 complete Initial Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
        }

        @Override
        /**
         * 查询 find Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 解析或查询得到的业务值
         */
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        /**
         * 查询 find Source Order By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 解析或查询得到的业务值
         */
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            return order;
        }

        @Override
        /**
         * 完成 lock Order 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 当前方法计算或转换后的业务结果
         */
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        /**
         * 查询 find Source Operation By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            return operation;
        }

        @Override
        /**
         * 查询 find Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId, String merchantOrderNo, String transactionId) {
            return List.of(operation);
        }

        @Override
        /**
         * 查询 find Initial Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            return List.of(operation);
        }

        @Override
/**
 * 查询 find Non Terminal Captures 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            return List.of();
        }

        @Override
        /**
         * 查询 find Operation By Channel Transaction 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
         * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return operation;
        }

        @Override
/**
 * 完成 list Pending Channel Match 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of(operation);
        }

        @Override
        /**
         * 写入或更新 record Follow Up Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param recordDTO record DTO 输入值，含义由调用方法名称和所属业务对象限定
         */
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        }

        @Override
/**
 * 推进 complete By Channel Callback 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param orderDO order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param failReasonCode fail Reason Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param failReasonMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 当前方法计算或转换后的业务结果
 */
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
/**
 * 写入或更新 update Channel Match 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param matchResult match Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestId request Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param nextMatchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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
        /**
         * 写入或更新 update Merchant Api Response Log 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
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

        /**
         * query Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final ChannelTradeStatus queryStatus;

        /**
         * invoke Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final AtomicInteger invokeCount = new AtomicInteger();

        /**
         * transaction Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String transactionId;

        /**
         * channel Order No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String channelOrderNo;

        /**
         * channel Transaction Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String channelTransactionId;

        private QueryOnlyPaymentChannelInvokeService(ChannelTradeStatus queryStatus) {
            this.queryStatus = queryStatus;
        }

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param preparedChannelRequest prepared Channel Request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
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

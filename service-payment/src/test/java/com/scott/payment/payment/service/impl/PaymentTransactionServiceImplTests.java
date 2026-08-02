package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.redis.lock.DistributedLockBusyException;
import com.scott.payment.component.redis.lock.DistributedLockExecution;
import com.scott.payment.component.redis.lock.DistributedLockService;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.domain.state.DefaultTransactionStateMachineService;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelInvokeService.PaymentChannelInvokeException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionServiceImplTests
 * @date : 2026-07-12 20:50
 * @email : scott_x@163.com
 * @description : 收单交易服务单元测试，验证创建授权交易时的幂等、风控短路、渠道路由和渠道响应状态映射。
 * @status : create
 */
@Slf4j
class PaymentTransactionServiceImplTests {

    /**
     * 分片时间必须在任何持久化、渠道调用和结果回写之前统一到 MySQL DATETIME(3) 精度。
     */
    @Test
    void shouldNormalizeInitialTransactionTimeToDatabasePrecisionBeforeProcessing() {
        CapturingPaymentChannelInvokeService channelInvokeService =
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202608020001");
        commandDTO.setMerchantOrderId("PAY202608020001");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 8, 2, 12, 31, 25, 233_622_000));

        service.createPayment(commandDTO);

        assertThat(commandDTO.getTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 12, 31, 25, 233_000_000));
        assertThat(channelInvokeService.commandDTO.getTransactionDateTime())
                .isEqualTo(commandDTO.getTransactionDateTime());
    }

    /**
     * 测试用原平台交易 ID，时间片段为 2026-07-12 10:30:00.000。
     */
    private static final String SOURCE_TRANSACTION_ID = "TX202607121030000000001";

    /**
     * 测试用请款动作交易 ID，用于覆盖商户以后续动作 ID 发起退款的场景。
     */
    private static final String CAPTURE_TRANSACTION_ID = "202607141615000000999";

    /**
     * 测试用内部生命周期关联 ID。
     */
    private static final String SOURCE_OPERATION_ID = "OP202607121030000000001";

    /**
     * 测试用原动作渠道交易 ID。
     */
    private static final String SOURCE_CHANNEL_TRANSACTION_ID = "CH202607121030000000001";

    /**
     * 风控通过后应进入渠道路由和渠道调用，并返回字典状态及交易生命周期标识。
     */
    @Test
    void shouldReturnDictionaryStatusAndTransactionLifecycleIds() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(), channelInvokeService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setMerchantOrderId("AUTH202607120001");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        assertThat(resultDTO.getOperationId()).startsWith("OP");
        assertThat(resultDTO.getTransactionId()).startsWith("20260712103000000");
        assertThat(resultDTO.getTransactionId()).doesNotStartWith("TX");
        assertThat(resultDTO.getAmount()).isEqualTo(1234L);
        assertThat(channelInvokeService.commandDTO).isSameAs(commandDTO);
        assertThat(channelInvokeService.routeResultDTO.getChannelCode()).isEqualTo("MPGS");
        assertThat(channelInvokeService.operationId).isEqualTo(resultDTO.getOperationId());
        assertThat(channelInvokeService.transactionId).isEqualTo(resultDTO.getTransactionId());
        assertThat(channelInvokeService.channelOrderNo).isEqualTo(resultDTO.getTransactionId());
        assertThat(eventOutboxService.eventDO.getMessageKey()).isEqualTo(resultDTO.getTransactionId());
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("INIT");
        assertThat(eventOutboxService.eventDO.getEventType()).isEqualTo("TRANSACTION_CREATED");
        assertThat(idempotencyService.find("TRANSACTION_OPERATION", "200001:M202607120001:INITIAL"))
                .get()
                .extracting(TransactionIdempotencyDO::getTransactionId)
                .isEqualTo(resultDTO.getTransactionId());
    }

    /**
     * 渠道支持商户标签币种时不触发 EDC，交易汇率按 1.00000000 落入命令上下文。
     */
    @Test
    void shouldKeepDefaultRateWhenRequestedCurrencyIsSupportedByChannel() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getLabelCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getTransactionCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getTransactionAmount()).isEqualByComparingTo("12.34");
        assertThat(channelInvokeService.commandDTO.getTransactionRate()).isEqualByComparingTo("1.00000000");
        assertThat(channelInvokeService.commandDTO.getEdcEnabled()).isZero();
    }

    /**
     * 渠道不支持商户标签币种但存在交易汇率时，应按 EDC 把标签金额换算为渠道支持币种后再上送渠道。
     */
    @Test
    void shouldConvertLabelCurrencyToRoutedCurrencyWhenEdcRateExists() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                channelInvokeService,
                commandDTO -> {
                    PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
                    routeResultDTO.setChannelId(101L);
                    routeResultDTO.setMidConfigId(1001L);
                    routeResultDTO.setMidNo("TESTDEVMER031");
                    routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
                    routeResultDTO.setRoutedCurrency("USD");
                    routeResultDTO.setEdcRequired(true);
                    return routeResultDTO;
                },
                exchangeRateService());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setAmount(new BigDecimal("100.00"));
        commandDTO.setCurrency("CNY");

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getCurrency()).isEqualTo("USD");
        assertThat(resultDTO.getAmount()).isEqualTo(1400L);
        assertThat(channelInvokeService.commandDTO.getLabelCurrency()).isEqualTo("CNY");
        assertThat(channelInvokeService.commandDTO.getLabelAmount()).isEqualByComparingTo("100.00");
        assertThat(channelInvokeService.commandDTO.getTransactionCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getTransactionAmount()).isEqualByComparingTo("14.00");
        assertThat(channelInvokeService.commandDTO.getTransactionRate()).isEqualByComparingTo("0.14000000");
        assertThat(channelInvokeService.commandDTO.getRateSource()).isEqualTo("TEST");
        assertThat(channelInvokeService.commandDTO.getEdcEnabled()).isOne();
    }

    /**
     * 渠道不支持标签币种且系统没有交易汇率时，交易应失败落库并禁止请求渠道，避免无汇率时误扣款。
     */
    @Test
    void shouldRejectTransactionWhenEdcRateMissing() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService,
                commandDTO -> {
                    PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
                    routeResultDTO.setChannelId(101L);
                    routeResultDTO.setMidConfigId(1001L);
                    routeResultDTO.setMidNo("TESTDEVMER031");
                    routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
                    routeResultDTO.setRoutedCurrency("EUR");
                    routeResultDTO.setEdcRequired(true);
                    return routeResultDTO;
                },
                exchangeRateService());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setCurrency("CNY");

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.EXCHANGE_RATE_NOT_FOUND.getCode());
        assertThat(resultDTO.getCurrency()).isEqualTo("CNY");
        assertThat(resultDTO.getAmount()).isEqualTo(1234L);
        assertThat(channelInvokeService.commandDTO).isNull();
        assertThat(transactionRecordService.commandDTO.getEdcEnabled()).isOne();
        assertThat(transactionRecordService.commandDTO.getLabelCurrency()).isEqualTo("CNY");
        assertThat(transactionRecordService.commandDTO.getTransactionCurrency()).isEqualTo("CNY");
        assertThat(transactionRecordService.commandDTO.getTransactionAmount()).isEqualByComparingTo("12.34");
        assertThat(transactionRecordService.commandDTO.getTransactionRate()).isEqualByComparingTo("1.00000000");
        assertThat(transactionRecordService.resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.EXCHANGE_RATE_NOT_FOUND.getCode());
    }

    /**
     * 首次类交易必须在本地事务内记录主单、动作单和状态历史，为后续查询、请款、退款和时间线提供基础数据。
     */
    @Test
    void shouldRecordInitialTransactionFactsWhenTransactionCreated() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                idempotencyService,
                eventOutboxService,
                transactionRecordService,
                List.of(),
                channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(transactionRecordService.commandDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(transactionRecordService.resultDTO.getTransactionId()).isEqualTo(resultDTO.getTransactionId());
        assertThat(transactionRecordService.routeResultDTO.getChannelCode()).isEqualTo("MPGS");
        assertThat(transactionRecordService.channelInvokeResultDTO.getRequestStatus()).isEqualTo("INIT");
        assertThat(transactionRecordService.channelResponse).isNull();
        assertThat(transactionRecordService.riskDecisionEnum).isEqualTo(PaymentRiskDecisionEnum.PASS);
        assertThat(transactionRecordService.currencyExponent).isEqualTo(2);
    }

    /**
     * 渠道请求发送前因本地配置缺失而失败时，渠道不可能受理交易，必须直接进入失败终态。
     */
    @Test
    void shouldMarkTransactionFailedWhenChannelRequestFailsBeforeDispatch() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        log.info("测试发送前渠道失败状态：模拟 MPGS 配置缺失，预期交易进入 FAILED 终态");
        FailingPaymentChannelInvokeService channelInvokeService = new FailingPaymentChannelInvokeService(false);
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                idempotencyService,
                eventOutboxService,
                transactionRecordService,
                List.of(),
                channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
        assertThat(resultDTO.getMerchantResponseCode()).isEqualTo(ApiResultEnum.PAYMENT_REJECTED.getCode());
        assertThat(transactionRecordService.resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(transactionRecordService.channelInvokeResultDTO.getRequestStatus()).isEqualTo("INIT");
        log.info("发送前渠道失败状态验证完成：status=FAILED，processStage=FINISHED，failReasonCode=CHANNEL_REQUEST_FAILED");
    }

    /**
     * 渠道请求可能已被受理但响应结果未知时，资金动作必须保持处理中等待查询或回调勾兑。
     */
    @Test
    void shouldKeepTransactionProcessingWhenChannelOutcomeIsUncertain() {
        log.info("测试渠道结果不确定状态：模拟网络异常，预期交易保持 PROCESSING 等待勾兑");
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                new FailingPaymentChannelInvokeService(true));

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        assertThat(resultDTO.getFailReasonCode()).isNull();
        assertThat(resultDTO.getMerchantResponseCode()).isEqualTo(ApiResultEnum.PROCESSING.getCode());
        assertThat(transactionRecordService.resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        log.info("渠道结果不确定状态验证完成：status=PROCESSING，processStage=CHANNEL_PROCESSING");
    }

    /**
     * 子商户信息允许部分字段为空；交易响应回显时不能因为空字段触发 JDK 不允许 null 的集合构造异常。
     */
    @Test
    void shouldIgnoreNullFieldsWhenSubMerchantInfoIsPartiallyProvided() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS)));
        PaymentCreateCommandDTO commandDTO = baseCommand();
        PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfoDTO = new PaymentCreateCommandDTO.SubMerchantInfoDTO();
        subMerchantInfoDTO.setSubId("SUB200045");
        subMerchantInfoDTO.setSubName("Codex");
        subMerchantInfoDTO.setSubCountryCode("CHN");
        subMerchantInfoDTO.setMerchantCategory("4077");
        commandDTO.setSubMerchantInfo(subMerchantInfoDTO);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getSubMerchantInfo()).isNotNull();
        assertThat(resultDTO.getSubMerchantInfo().getSubId()).isEqualTo("SUB200045");
        assertThat(resultDTO.getSubMerchantInfo().getSubCompanyName()).isNull();
    }

    /**
     * 一步支付入口应把交易类型传递为 PAYMENT，并复用首次交易受理骨架。
     */
    @Test
    void shouldCreatePaymentWithPaymentTransactionType() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(), channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createPayment(baseCommand());

        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.PAYMENT.getCode());
        assertThat(channelInvokeService.commandDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.PAYMENT.getCode());
        assertThat(idempotencyService.records).containsKey("TRANSACTION_OPERATION:200001:M202607120001:INITIAL");
    }

    /**
     * 同一商户订单号已存在有效支付流时，不允许再发起授权流，避免一笔商户订单同时存在两笔支付语义。
     */
    @Test
    void shouldRejectAuthorizationWhenMerchantOrderAlreadyHasPaymentFlow() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        transactionRecordService.initialOperations = List.of(initialOperation(PaymentTransactionTypeEnum.PAYMENT, PaymentTransactionStatusEnum.SUCCESS));
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);

        assertThatThrownBy(() -> service.createAuthorization(baseCommand()))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 同一商户订单号已存在有效授权流时，不允许再发起一步支付流。
     */
    @Test
    void shouldRejectPaymentWhenMerchantOrderAlreadyHasAuthorizationFlow() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        transactionRecordService.initialOperations = List.of(initialOperation(PaymentTransactionTypeEnum.AUTHORIZATION, PaymentTransactionStatusEnum.PROCESSING));
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);

        assertThatThrownBy(() -> service.createPayment(baseCommand()))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 同一商户订单号已存在有效授权起点时，不允许再次发起新的授权起点；请款、撤销和退款必须作为后续动作进入原生命周期。
     */
    @Test
    void shouldRejectDuplicateAuthorizationWhenMerchantOrderAlreadyHasAuthorizationFlow() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        transactionRecordService.initialOperations = List.of(initialOperation(PaymentTransactionTypeEnum.AUTHORIZATION, PaymentTransactionStatusEnum.SUCCESS));
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);

        assertThatThrownBy(() -> service.createAuthorization(baseCommand()))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 已失败的首次起点不占用商户订单号流程，商户修正参数后可继续用同一商户订单号重试。
     */
    @Test
    void shouldAllowRetryWhenExistingInitialFlowFailed() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        transactionRecordService.initialOperations = List.of(initialOperation(PaymentTransactionTypeEnum.PAYMENT, PaymentTransactionStatusEnum.FAILED));
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        assertThat(channelInvokeService.commandDTO).isNotNull();
    }

    /**
     * 商户不需要上送卡品牌；首次类交易应按 PAN 前缀识别统一卡品牌并返回给 OpenAPI。
     */
    @Test
    void shouldResolvePaymentBrandFromCardNumberWhenMerchantDoesNotPassCardBrand() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS)));
        PaymentCreateCommandDTO commandDTO = baseCommand();
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123450000000008");
        commandDTO.setCardInfo(cardInfoDTO);

        PaymentCreateResultDTO resultDTO = service.createPayment(commandDTO);

        assertThat(resultDTO.getPaymentBrand()).isEqualTo("MASTERCARD");
        assertThat(resultDTO.getCardBin()).isEqualTo("512345****0008");
    }

    /**
     * 预授权入口应把交易类型传递为 PRE_AUTHORIZATION，并复用首次交易受理骨架。
     */
    @Test
    void shouldCreatePreAuthorizationWithPreAuthorizationTransactionType() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(), channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createPreAuthorization(baseCommand());

        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode());
        assertThat(channelInvokeService.commandDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode());
    }

    /**
     * 请款应按原交易时间和 transaction_id 定位授权主单，校验可请款金额后生成新的动作单并调用渠道。
     */
    @Test
    void shouldCreateCaptureAfterSourceTransactionLocatedAndAmountChecked() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                idempotencyService,
                eventOutboxService,
                transactionRecordService,
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.CAPTURE, new BigDecimal("5.00"));
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 16, 15));

        PaymentCreateResultDTO resultDTO = service.capture(commandDTO);

        assertThat(resultDTO.getOperationId()).isEqualTo(SOURCE_OPERATION_ID);
        assertThat(resultDTO.getTransactionId()).startsWith("20260714161500000");
        assertThat(resultDTO.getTransactionId()).doesNotStartWith("TX");
        assertThat(resultDTO.getSourceTransactionId()).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.CAPTURE.getCode());
        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resultDTO.getAmount()).isEqualTo(500L);
        assertThat(channelInvokeService.operationId).isEqualTo(SOURCE_OPERATION_ID);
        assertThat(channelInvokeService.channelOrderNo).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(channelInvokeService.commandDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(channelInvokeService.commandDTO.getMerchantOrderId()).isEqualTo("CAPTURE202607120001");
        assertThat(channelInvokeService.commandDTO.getTransactionDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 14, 16, 15));
        assertThat(channelInvokeService.commandDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(channelInvokeService.commandDTO.getTransactionInfo().getSourceChannelTransactionId()).isEqualTo(SOURCE_CHANNEL_TRANSACTION_ID);
        assertThat(transactionRecordService.followUpRecordDTO.getResultDTO().getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.CAPTURE.getCode());
    }

    /**
     * 退款即使以上一笔请款交易 ID 作为源交易，也必须使用生命周期根交易 ID 作为 MPGS orderId。
     */
    @Test
    void shouldUseRootTransactionIdAsChannelOrderNoWhenRefundSourceIsCaptureOperation() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("1.00"));
        commandDTO.getTransactionInfo().setSourceTransactionId(CAPTURE_TRANSACTION_ID);
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 15, 14, 30));

        PaymentCreateResultDTO resultDTO = service.refund(commandDTO);

        assertThat(resultDTO.getSourceTransactionId()).isEqualTo(CAPTURE_TRANSACTION_ID);
        assertThat(channelInvokeService.channelOrderNo).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(channelInvokeService.commandDTO.getTransactionInfo().getSourceChannelTransactionId()).isEqualTo("CH202607141615000000999");
        assertThat(transactionRecordService.followUpRecordDTO.getSourceOrderDO().getRootTransactionId()).isEqualTo(SOURCE_TRANSACTION_ID);
    }

    /**
     * 退款允许商户不传币种和商户订单号；支付核心应按 sourceTransactionId 定位原交易并补齐渠道请求币种。
     */
    @Test
    void shouldRefundWithSourceTransactionIdOnlyForOptionalCurrencyAndMerchantOrderNo() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("1.00"));
        commandDTO.setMerchantOrderNo(null);
        commandDTO.setCurrency(null);

        PaymentCreateResultDTO resultDTO = service.refund(commandDTO);

        assertThat(resultDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(resultDTO.getOrderCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getCurrency()).isEqualTo("USD");
        assertThat(channelInvokeService.commandDTO.getLabelCurrency()).isEqualTo("USD");
        assertThat(transactionRecordService.followUpRecordDTO.getResultDTO().getTotalRefundAmount()).isEqualByComparingTo("1.00");
    }

    /**
     * 退款传入商户订单号时必须与原交易一致，避免商户把退款动作误关联到其他业务订单。
     */
    @Test
    void shouldRejectRefundWhenMerchantOrderNoDoesNotMatchSourceTransaction() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("1.00"));
        commandDTO.setMerchantOrderNo("OTHER202607120001");

        assertThatThrownBy(() -> service.refund(commandDTO))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 退款传入币种时必须与原交易币种一致；不一致时应在渠道调用前拒绝。
     */
    @Test
    void shouldRejectRefundWhenCurrencyDoesNotMatchSourceTransaction() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("1.00"));
        commandDTO.setCurrency("EUR");

        assertThatThrownBy(() -> service.refund(commandDTO))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 支付订单退款响应的授权和请款累计展示应保持原支付金额，退款金额只累计到 totalRefundAmount。
     */
    @Test
    void shouldReturnPaymentAmountAsAuthorizedAndCapturedTotalsForPaymentRefund() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        transactionRecordService.sourceTransactionType = PaymentTransactionTypeEnum.PAYMENT.getCode();
        transactionRecordService.sourceAuthorizedAmount = BigDecimal.ZERO;
        transactionRecordService.sourceCapturedAmount = new BigDecimal("102.00");
        transactionRecordService.sourceTransactionAmount = new BigDecimal("102.00");
        transactionRecordService.sourceAvailableRefundAmount = new BigDecimal("102.00");
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("22.00"));
        commandDTO.setMerchantOrderNo("M202607120001");

        PaymentCreateResultDTO resultDTO = service.refund(commandDTO);

        assertThat(resultDTO.getTotalAuthorizedAmount()).isEqualByComparingTo("102.00");
        assertThat(resultDTO.getTotalCapturedAmount()).isEqualByComparingTo("102.00");
        assertThat(resultDTO.getTotalRefundAmount()).isEqualByComparingTo("22.00");
        assertThat(resultDTO.getOrderAmount()).isEqualByComparingTo("22.00");
        assertThat(resultDTO.getOrderCurrency()).isEqualTo("USD");
    }

    /**
     * 退款金额超过主单可退金额时必须在渠道调用前被状态机拒绝。
     */
    @Test
    void shouldRejectRefundWhenAmountExceedsAvailableRefundAmount() {
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                channelInvokeService);

        assertThatThrownBy(() -> service.refund(followUpCommand(PaymentTransactionTypeEnum.REFUND, new BigDecimal("9.00"))))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
        assertThat(channelInvokeService.commandDTO).isNull();
    }

    /**
     * 查询应按 merchantId + orderNo 查询同一商户订单下的关联交易动作列表。
     */
    @Test
    void shouldQueryTransactionsByMerchantOrderNo() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING)));

        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setAmount(null);
        commandDTO.setCurrency(null);
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setTransactionId(SOURCE_TRANSACTION_ID);
        transactionInfoDTO.setSourceTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        transactionInfoDTO.setRootTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        commandDTO.setTransactionInfo(transactionInfoDTO);

        PaymentQueryResultDTO resultDTO = service.query(commandDTO);

        assertThat(resultDTO.getMerchantId()).isEqualTo("200001");
        assertThat(resultDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(resultDTO.getTransactionInfo()).hasSize(1);
        assertThat(resultDTO.getTransactionInfo().get(0).getTransactionId()).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(resultDTO.getTransactionInfo().get(0).getCode()).isEqualTo(ApiResultEnum.PAYMENT_SUCCESS.getCode());
        assertThat(resultDTO.getTransactionInfo().get(0).getRootTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 30));
    }

    /** 后续动作必须使用调用链传入的动作时间和根主单时间，禁止从平台交易号解析分片。 */
    @Test
    void shouldLocateSourceOrderWithExplicitActionAndRootShardingTimes() {
        CapturingTransactionRecordService transactionRecordService = new CapturingTransactionRecordService();
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                transactionRecordService,
                List.of(),
                channelInvokeService);
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.CAPTURE, new BigDecimal("5.00"));
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 16, 16));

        PaymentCreateResultDTO resultDTO = service.capture(commandDTO);

        assertThat(resultDTO.getOperationId()).isEqualTo(SOURCE_OPERATION_ID);
        assertThat(transactionRecordService.sourceLookupTransactionId).isEqualTo(SOURCE_TRANSACTION_ID);
        assertThat(transactionRecordService.sourceLookupDateTime)
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 30));
        assertThat(transactionRecordService.rootLookupDateTime)
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 30));
        assertThat(channelInvokeService.commandDTO).isSameAs(commandDTO);
    }

    /** 缺少显式源交易分片时间时必须拒绝，不能退回交易号解析。 */
    @Test
    void shouldRejectFollowUpWhenSourceTransactionDateTimeIsMissing() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new EmptyInitialTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                new CapturingTransactionRecordService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS)));
        PaymentCreateCommandDTO commandDTO = followUpCommand(PaymentTransactionTypeEnum.CAPTURE, new BigDecimal("5.00"));
        commandDTO.getTransactionInfo().setSourceTransactionDateTime(null);
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 16, 17));

        assertThatThrownBy(() -> service.capture(commandDTO))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sourceTransactionDateTime");
    }

    /**
     * 风控拒绝时必须短路，不应继续执行渠道路由和扣款请求。
     */
    @Test
    void shouldReturnFailedWhenRiskRejected() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.REJECT), idempotencyService, eventOutboxService, List.of());
        PaymentCreateCommandDTO commandDTO = baseCommand();

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        assertThat(eventOutboxService.eventDO).isNotNull();
        assertThat(eventOutboxService.eventDO.getTransactionId()).isEqualTo(resultDTO.getTransactionId());
        assertThat(eventOutboxService.events)
                .extracting(TransactionEventOutboxDO::getEventType)
                .containsExactly("TRANSACTION_CREATED", "TRANSACTION_STATUS_CHANGED");
        assertThat(eventOutboxService.events)
                .extracting(TransactionEventOutboxDO::getTransactionDateTime)
                .containsOnly(commandDTO.getTransactionDateTime());
    }

    /**
     * 风控要求 3DS 时交易进入挂起状态，等待付款人完成后续动作。
     */
    @Test
    void shouldReturnPendingWhenRiskRequiresThreeDs() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.REQUIRE_3DS), idempotencyService, eventOutboxService, List.of());
        PaymentCreateCommandDTO commandDTO = baseCommand();

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_3DS.getCode());
        assertThat(resultDTO.getPendingReasonCode()).isEqualTo(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
        assertThat(eventOutboxService.eventDO).isNull();
    }

    /**
     * 风控人工复核时交易进入挂起状态，不能继续请求渠道。
     */
    @Test
    void shouldReturnPendingWhenRiskRequiresReview() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.REVIEW), idempotencyService, eventOutboxService, List.of());
        PaymentCreateCommandDTO commandDTO = baseCommand();

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_RISK_REVIEW.getCode());
        assertThat(resultDTO.getPendingReasonCode()).isEqualTo(PaymentPendingReasonEnum.RISK_REVIEW.getCode());
        assertThat(eventOutboxService.eventDO).isNull();
    }

    /**
     * 风控无响应或异常空响应时按拒绝处理，避免风控不可用时误放行交易。
     */
    @Test
    void shouldFailClosedWhenRiskDecisionIsEmpty() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                commandDTO -> null, idempotencyService, eventOutboxService, List.of());
        PaymentCreateCommandDTO commandDTO = baseCommand();

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        assertThat(eventOutboxService.eventDO).isNotNull();
        assertThat(eventOutboxService.eventDO.getTransactionId()).isEqualTo(resultDTO.getTransactionId());
    }

    /**
     * 相同商户、商户订单号和交易类型重复请求时应返回第一次创建的交易结果快照。
     */
    @Test
    void shouldReturnFirstResultWhenMerchantRepeatsSameCreateRequest() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of());
        PaymentCreateCommandDTO commandDTO = baseCommand();

        PaymentCreateResultDTO firstResult = service.createAuthorization(commandDTO);
        PaymentCreateResultDTO repeatedResult = service.createAuthorization(commandDTO);

        assertThat(repeatedResult.getTransactionId()).isEqualTo(firstResult.getTransactionId());
        assertThat(repeatedResult.getOperationId()).isEqualTo(firstResult.getOperationId());
        assertThat(idempotencyService.records).hasSize(1);
    }

    /**
     * 分布式锁获取失败且数据库还没有幂等结果时，应提示调用方稍后重试。
     */
    @Test
    void shouldReturnProcessingWhenConcurrentRequestCannotAcquireLock() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(new RejectingRedisLockService()));
        PaymentCreateCommandDTO commandDTO = baseCommand();

        assertThatThrownBy(() -> service.createAuthorization(commandDTO))
                .hasMessage("The network is busy, please try again later");
    }

    /**
     * 首次交易锁应使用环境隔离的脱敏 Key，并在本地准备事务提交后、渠道调用前释放。
     */
    @Test
    void shouldUseGovernedInitialLockKeysAndReleaseLocksBeforeChannelInvocation() {
        TrackingRedisLockService lockService = new TrackingRedisLockService();
        boolean[] locksHeldAtChannelInvocation = {false};
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> locksHeldAtChannelInvocation[0] = lockService.hasActiveLocks());
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(lockService),
                channelInvokeService);

        service.createAuthorization(baseCommand());

        assertThat(lockService.lockTtlByKey).hasSize(2);
        assertThat(lockService.lockTtlByKey.keySet())
                .anyMatch(key -> key.matches("acquiring:local:lock:payment:\\{[0-9a-f]{64}}:operation"))
                .anyMatch(key -> key.matches("acquiring:local:lock:payment:\\{[0-9a-f]{64}}:merchant-order-flow"))
                .allSatisfy(key -> assertThat(key)
                        .doesNotContain("200001", "M202607120001", "transaction:", "service-payment", ":v1:"));
        assertThat(lockService.lockTtlByKey.values()).containsOnly(30L);
        assertThat(locksHeldAtChannelInvocation[0]).isFalse();
        assertThat(lockService.hasActiveLocks()).isFalse();
    }

    /**
     * Capture 锁应复用同一精简命名规则，并只覆盖本地准备阶段。
     */
    @Test
    void shouldUseGovernedCaptureLockKeyAndReleaseLockBeforeChannelInvocation() {
        TrackingRedisLockService lockService = new TrackingRedisLockService();
        boolean[] locksHeldAtChannelInvocation = {false};
        CapturingPaymentChannelInvokeService channelInvokeService = new CapturingPaymentChannelInvokeService(
                channelResponse(ChannelTradeStatus.SUCCESS),
                () -> locksHeldAtChannelInvocation[0] = lockService.hasActiveLocks());
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(lockService),
                channelInvokeService);

        service.capture(followUpCommand(PaymentTransactionTypeEnum.CAPTURE, new BigDecimal("5.00")));

        assertThat(lockService.lockTtlByKey).hasSize(1);
        assertThat(lockService.lockTtlByKey.keySet())
                .allMatch(key -> key.matches("acquiring:local:lock:payment:\\{[0-9a-f]{64}}:operation"))
                .allSatisfy(key -> assertThat(key)
                        .doesNotContain("200001", SOURCE_TRANSACTION_ID, "CAPTURE202607120001",
                                "transaction:", "service-payment", ":v1:"));
        assertThat(lockService.lockTtlByKey.values()).containsOnly(30L);
        assertThat(locksHeldAtChannelInvocation[0]).isFalse();
        assertThat(lockService.hasActiveLocks()).isFalse();
    }

    /**
     * 准备事务已提交后，即使 Redis 解锁异常，也应依赖租约自然过期并继续调用渠道。
     */
    @Test
    void shouldContinueChannelInvocationWhenPreparationLockReleaseFails() {
        FailingUnlockRedisLockService lockService = new FailingUnlockRedisLockService();
        CapturingPaymentChannelInvokeService channelInvokeService =
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS));
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(lockService),
                channelInvokeService);

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(channelInvokeService.commandDTO).isNotNull();
        assertThat(lockService.unlockAttempts).isEqualTo(2);
    }

    /**
     * 渠道同步返回成功时，平台创建结果应映射为成功终态。
     */
    @Test
    void shouldReturnSuccessWhenChannelApproved() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.SUCCESS)));

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isNull();
    }

    /**
     * WorldPay 一步支付同步 AUTHORISED 仅表示授权成功，必须等待 captured 回调或查询勾兑后才能标记付款成功。
     */
    @Test
    void shouldKeepWorldPayPaymentPendingWhenSyncAuthorised() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(worldPayResponse("WPGXML", "AUTHORISED")));

        PaymentCreateResultDTO resultDTO = service.createPayment(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        assertThat(resultDTO.getPendingReasonCode()).isEqualTo(PaymentPendingReasonEnum.WAITING_CHANNEL_CALLBACK.getCode());
    }

    /**
     * WorldPay 一步支付收到 CAPTURED 才代表资金动作成功，可映射为平台成功终态。
     */
    @Test
    void shouldMarkWorldPayPaymentSuccessWhenCaptured() {
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(worldPayResponse("WPGXML", "CAPTURED")));

        PaymentCreateResultDTO resultDTO = service.createPayment(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
    }

    /**
     * 渠道同步返回失败时，平台结果只暴露平台失败码；渠道真实原因后续应落交互日志或交易附属信息。
     */
    @Test
    void shouldReturnFailedWhenChannelDeclined() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.FAILED)));

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(resultDTO.getFailReasonCode()).isEqualTo(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
        assertThat(resultDTO.getMerchantResponseMessage()).isEqualTo("05: Declined");
    }

    /**
     * MPGS result=ERROR 类失败不应把渠道技术或参数错误直接返回给商户，应统一返回模糊拒绝提示。
     */
    @Test
    void shouldReturnGenericMerchantMessageWhenMpgsResultError() {
        ChannelPaymentResponse response = channelResponse(ChannelTradeStatus.FAILED);
        response.setRawChannelStatus("ERROR");
        response.setChannelResponseCode("INVALID_REQUEST");
        response.setChannelResponseMessage("Unexpected parameter 'authentication.threeDs.acsEci'");
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS),
                new InMemoryTransactionIdempotencyService(),
                new CapturingTransactionEventOutboxService(),
                List.of(),
                new CapturingPaymentChannelInvokeService(response));

        PaymentCreateResultDTO resultDTO = service.createPayment(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(resultDTO.getMerchantResponseMessage())
                .isEqualTo("The transaction was declined; please contact your card issuer or try again.");
        assertThat(resultDTO.getMerchantResponseMessage()).doesNotContain("Unexpected parameter");
    }

    /**
     * 渠道要求跳转时，平台交易应进入 3DS 等待状态。
     */
    @Test
    void shouldReturnPendingWhenChannelNeedsRedirect() {
        InMemoryTransactionIdempotencyService idempotencyService = new InMemoryTransactionIdempotencyService();
        CapturingTransactionEventOutboxService eventOutboxService = new CapturingTransactionEventOutboxService();
        PaymentTransactionServiceImpl service = newService(
                riskDecision(PaymentRiskDecisionEnum.PASS), idempotencyService, eventOutboxService, List.of(),
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.NEED_REDIRECT)));

        PaymentCreateResultDTO resultDTO = service.createAuthorization(baseCommand());

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_3DS.getCode());
        assertThat(resultDTO.getPendingReasonCode()).isEqualTo(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setMerchantOrderId("AUTH202607120001");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        return commandDTO;
    }

    private PaymentCreateCommandDTO followUpCommand(PaymentTransactionTypeEnum transactionTypeEnum, BigDecimal amount) {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo(transactionTypeEnum.getCode() + "202607120001");
        commandDTO.setMerchantOrderId(transactionTypeEnum.getCode() + "202607120001");
        commandDTO.setAmount(amount);
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionType(transactionTypeEnum.getCode());
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 13, 11, 30));
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId(SOURCE_TRANSACTION_ID);
        transactionInfoDTO.setSourceTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        transactionInfoDTO.setRootTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        commandDTO.setTransactionInfo(transactionInfoDTO);
        return commandDTO;
    }

    private PaymentRiskInvokeService riskDecision(PaymentRiskDecisionEnum decisionEnum) {
        return commandDTO -> {
            PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
            decisionDTO.setPassed(decisionEnum.isAllowProceed());
            decisionDTO.setDecision(decisionEnum.getCode());
            return decisionDTO;
        };
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionEventOutboxService eventOutboxService,
                                                     List<DistributedLockService> redisLockServices) {
        return newService(riskInvokeService, idempotencyService, eventOutboxService, new CapturingTransactionRecordService(), redisLockServices,
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING)));
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionEventOutboxService eventOutboxService,
                                                     List<DistributedLockService> redisLockServices,
                                                     PaymentChannelInvokeService channelInvokeService) {
        return newService(riskInvokeService, idempotencyService, eventOutboxService, new CapturingTransactionRecordService(), redisLockServices, channelInvokeService);
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionEventOutboxService eventOutboxService,
                                                     CapturingTransactionRecordService transactionRecordService,
                                                     List<DistributedLockService> redisLockServices,
                                                     PaymentChannelInvokeService channelInvokeService) {
        return newService(riskInvokeService, idempotencyService, eventOutboxService, transactionRecordService,
                redisLockServices, channelInvokeService, routeService(), exchangeRateService());
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionEventOutboxService eventOutboxService,
                                                     CapturingTransactionRecordService transactionRecordService,
                                                     List<DistributedLockService> redisLockServices,
                                                     PaymentChannelInvokeService channelInvokeService,
                                                     PaymentChannelRouteService routeService,
                                                     PaymentExchangeRateService exchangeRateService) {
        return new PaymentTransactionServiceImpl(
                isoDictionaryService(),
                riskInvokeService,
                routeService,
                channelInvokeService,
                exchangeRateService,
                idempotencyService,
                eventOutboxService,
                transactionRecordService,
                new DefaultTransactionStateMachineService(),
                redisLockServices.isEmpty()
                        ? new AlwaysAvailableDistributedLockService()
                        : redisLockServices.get(0));
    }

    private PaymentChannelRouteService routeService() {
        return commandDTO -> {
            PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
            routeResultDTO.setChannelId(101L);
            routeResultDTO.setMidConfigId(1001L);
            routeResultDTO.setMidNo("TESTDEVMER031");
            routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
            routeResultDTO.setRoutedCurrency(commandDTO.getCurrency());
            return routeResultDTO;
        };
    }

    private PaymentExchangeRateService exchangeRateService() {
        return (baseCurrency, quoteCurrency, atTime) -> {
            if ("CNY".equalsIgnoreCase(baseCurrency) && "USD".equalsIgnoreCase(quoteCurrency)) {
                PaymentExchangeRateDTO dto = new PaymentExchangeRateDTO();
                dto.setBaseCurrency("CNY");
                dto.setQuoteCurrency("USD");
                dto.setSourceCode("TEST");
                dto.setFinalRate(new BigDecimal("0.14000000"));
                dto.setEffectiveTime(LocalDateTime.of(2026, 7, 12, 10, 0));
                return Optional.of(dto);
            }
            return Optional.empty();
        };
    }

    private ChannelPaymentResponse channelResponse(ChannelTradeStatus tradeStatus) {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelTradeStatus(tradeStatus.getCode());
        response.setChannelResponseCode(ChannelTradeStatus.SUCCESS == tradeStatus ? "00" : "05");
        response.setChannelResponseMessage(ChannelTradeStatus.SUCCESS == tradeStatus ? "Approved" : "Declined");
        response.setAuthCode(ChannelTradeStatus.SUCCESS == tradeStatus ? "123456" : null);
        response.setRrn(ChannelTradeStatus.SUCCESS == tradeStatus ? "RCPT001" : null);
        response.setAcquirerReferenceNo(ChannelTradeStatus.SUCCESS == tradeStatus ? "REF001" : null);
        return response;
    }

    private ChannelPaymentResponse worldPayResponse(String channelCode, String rawStatus) {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode(channelCode);
        response.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
        response.setRawChannelStatus(rawStatus);
        response.setChannelResponseCode(rawStatus);
        response.setChannelResponseMessage(rawStatus);
        return response;
    }

    /**
     * 构造商户订单号起点动作，用于验证支付流和授权流互斥规则。
     *
     * @param typeEnum   起点交易类型
     * @param statusEnum 起点交易状态
     * @return 测试动作单
     */
    private TransactionOperationDO initialOperation(PaymentTransactionTypeEnum typeEnum,
                                                    PaymentTransactionStatusEnum statusEnum) {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setOperationId(SOURCE_OPERATION_ID);
        operationDO.setTransactionId(SOURCE_TRANSACTION_ID);
        operationDO.setMerchantId("200001");
        operationDO.setMerchantOrderNo("M202607120001");
        operationDO.setMerchantOrderId("AUTH202607120001");
        operationDO.setTransactionType(typeEnum.getCode());
        operationDO.setTransactionStatus(statusEnum.getCode());
        operationDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        operationDO.setOperationTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        return operationDO;
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
                return List.of(usdCurrencyInfo(), cnyCurrencyInfo(), eurCurrencyInfo());
            }

            @Override
            public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
                return List.of(usdCurrencyInfo(), cnyCurrencyInfo(), eurCurrencyInfo());
            }

            @Override
            public Optional<IsoCurrencyInfo> getCurrency(String value) {
                if ("USD".equalsIgnoreCase(value)) {
                    return Optional.of(usdCurrencyInfo());
                }
                if ("CNY".equalsIgnoreCase(value)) {
                    return Optional.of(cnyCurrencyInfo());
                }
                if ("EUR".equalsIgnoreCase(value)) {
                    return Optional.of(eurCurrencyInfo());
                }
                return Optional.empty();
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

            private IsoCurrencyInfo cnyCurrencyInfo() {
                return new IsoCurrencyInfo("CNY", "156", "Yuan Renminbi", "人民币", 2, 100L, new BigDecimal("0.01"), "¥");
            }

            private IsoCurrencyInfo eurCurrencyInfo() {
                return new IsoCurrencyInfo("EUR", "978", "Euro", "欧元", 2, 100L, new BigDecimal("0.01"), "€");
            }
        };
    }

    private static class CapturingTransactionRecordService implements TransactionRecordService {

        /**
         * command DTO，用于保存 Capturing Transaction Record Service 中与 commanddto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentCreateCommandDTO commandDTO;

        /**
         * route Result DTO，用于保存 Capturing Transaction Record Service 中与 routeresultdto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentRouteResultDTO routeResultDTO;

        /**
         * channel Response，用于保存 Capturing Transaction Record Service 中与 渠道response 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private ChannelPaymentResponse channelResponse;

        /**
         * channel Invoke Result DTO，用于保存 Capturing Transaction Record Service 中与 渠道invokeresultdto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentChannelInvokeResultDTO channelInvokeResultDTO;

        /**
         * result Channel Response，用于保存 Capturing Transaction Record Service 中与 result渠道response 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private ChannelPaymentResponse resultChannelResponse;

        /**
         * result Channel Invoke Result DTO，用于保存 Capturing Transaction Record Service 中与 result渠道invokeresultdto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentChannelInvokeResultDTO resultChannelInvokeResultDTO;

        /**
         * result DTO，用于保存 Capturing Transaction Record Service 中与 resultdto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentCreateResultDTO resultDTO;
        /** 最近一次源交易查询收到的平台交易号。 */
        private String sourceLookupTransactionId;
        /** 最近一次源交易查询收到的动作真实分片时间。 */
        private LocalDateTime sourceLookupDateTime;
        /** 最近一次源交易查询收到的生命周期主单真实分片时间。 */
        private LocalDateTime rootLookupDateTime;

        /**
         * risk Decision Enum，用于保存 Capturing Transaction Record Service 中与 riskdecisionenum 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentRiskDecisionEnum riskDecisionEnum;

        /**
         * currency Exponent，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private int currencyExponent;

        /**
         * follow Up Record DTO，用于保存 Capturing Transaction Record Service 中与 followuprecorddto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private TransactionFollowUpRecordDTO followUpRecordDTO;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceTransactionType = PaymentTransactionTypeEnum.AUTHORIZATION.getCode();

        /**
         * source Transaction Amount，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal sourceTransactionAmount = new BigDecimal("12.34");

        /**
         * source Authorized Amount，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal sourceAuthorizedAmount = new BigDecimal("12.34");

        /**
         * source Captured Amount，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal sourceCapturedAmount = BigDecimal.ZERO;

        /**
         * source Available Refund Amount，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal sourceAvailableRefundAmount = new BigDecimal("5.00");

        /**
         * initial Operations，用于保存 Capturing Transaction Record Service 中与 initial动作 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<TransactionOperationDO> initialOperations = List.of();

        /**
         * 捕获渠道调用前落库的初始交易参数，供用例核对准备阶段的交易事实。
         */
        @Override
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
            this.commandDTO = commandDTO;
            this.routeResultDTO = routeResultDTO;
            this.channelInvokeResultDTO = channelInvokeResultDTO;
            this.channelResponse = channelInvokeResultDTO == null ? null : channelInvokeResultDTO.getChannelResponse();
            this.resultDTO = resultDTO;
            this.riskDecisionEnum = riskDecisionEnum;
            this.currencyExponent = currencyExponent;
        }

        /**
         * 捕获渠道返回后的最终结果参数，与准备阶段快照分开供断言比较。
         */
        @Override
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
            this.commandDTO = commandDTO;
            this.routeResultDTO = routeResultDTO;
            this.resultChannelInvokeResultDTO = channelInvokeResultDTO;
            this.resultChannelResponse = channelInvokeResultDTO == null ? null : channelInvokeResultDTO.getChannelResponse();
            this.resultDTO = resultDTO;
            this.riskDecisionEnum = riskDecisionEnum;
            this.currencyExponent = currencyExponent;
        }

        /**
         * 根据测试中已捕获的初始交易或固定源交易标识构造主单，其他条件返回未命中。
         */
        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            if (isRecordedInitialOperation(transactionDateTime, operationId)) {
                TransactionOrderDO orderDO = new TransactionOrderDO();
                orderDO.setOperationId(resultDTO.getOperationId());
                orderDO.setRootTransactionId(resultDTO.getTransactionId());
                orderDO.setLatestTransactionId(resultDTO.getTransactionId());
                orderDO.setMerchantId(commandDTO.getMerchantId());
                orderDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
                orderDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
                orderDO.setTransactionType(resultDTO.getTransactionType());
                orderDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                orderDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
                orderDO.setTransactionCurrency(commandDTO.getTransactionCurrency());
                orderDO.setTransactionAmount(commandDTO.getTransactionAmount());
                orderDO.setAuthorizedAmount(BigDecimal.ZERO);
                orderDO.setCapturedAmount(BigDecimal.ZERO);
                orderDO.setRefundedAmount(BigDecimal.ZERO);
                orderDO.setAvailableCaptureAmount(BigDecimal.ZERO);
                orderDO.setAvailableRefundAmount(BigDecimal.ZERO);
                orderDO.setCurrencyExponent(2);
                orderDO.setTransactionDateTime(transactionDateTime);
                orderDO.setTransactionUtcTime(transactionDateTime);
                orderDO.setTransactionTimeZone("Asia/Shanghai");
                orderDO.setVersion(0);
                return orderDO;
            }
            if (!LocalDateTime.of(2026, 7, 12, 10, 30).equals(transactionDateTime)
                    || !SOURCE_OPERATION_ID.equals(operationId)) {
                return null;
            }
            TransactionOrderDO orderDO = new TransactionOrderDO();
            orderDO.setOperationId(SOURCE_OPERATION_ID);
            orderDO.setRootTransactionId(SOURCE_TRANSACTION_ID);
            orderDO.setLatestTransactionId(SOURCE_TRANSACTION_ID);
            orderDO.setMerchantId("200001");
            orderDO.setMerchantOrderNo("M202607120001");
            orderDO.setMerchantOrderId("AUTH202607120001");
            orderDO.setTransactionType(sourceTransactionType);
            orderDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
            orderDO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            orderDO.setTransactionCurrency("USD");
            orderDO.setTransactionAmount(sourceTransactionAmount);
            orderDO.setAuthorizedAmount(sourceAuthorizedAmount);
            orderDO.setCapturedAmount(sourceCapturedAmount);
            orderDO.setRefundedAmount(BigDecimal.ZERO);
            orderDO.setAvailableCaptureAmount(new BigDecimal("12.34"));
            orderDO.setAvailableRefundAmount(sourceAvailableRefundAmount);
            orderDO.setCurrencyExponent(2);
            orderDO.setTransactionDateTime(transactionDateTime);
            orderDO.setTransactionUtcTime(LocalDateTime.of(2026, 7, 12, 2, 30));
            orderDO.setTransactionTimeZone("Asia/Shanghai");
            orderDO.setVersion(0);
            return orderDO;
        }

        /**
         * 仅为固定源交易及其请款交易返回主单，模拟按平台交易号反查。
         */
        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            if (!SOURCE_TRANSACTION_ID.equals(sourceTransactionId) && !CAPTURE_TRANSACTION_ID.equals(sourceTransactionId)) {
                return null;
            }
            return findOrder(LocalDateTime.of(2026, 7, 12, 10, 30), SOURCE_OPERATION_ID);
        }

        /**
         * 记录后续动作显式传入的源交易和根交易分片时间，再复用固定测试主单。
         */
        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId,
                                                                 LocalDateTime sourceTransactionDateTime,
                                                                 LocalDateTime rootTransactionDateTime) {
            this.sourceLookupTransactionId = sourceTransactionId;
            this.sourceLookupDateTime = sourceTransactionDateTime;
            this.rootLookupDateTime = rootTransactionDateTime;
            return findSourceOrderByTransactionId(sourceTransactionId);
        }

        /**
         * 复用主单查询结果模拟数据库行锁读取，不额外实现并发控制。
         */
        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return findOrder(transactionDateTime, operationId);
        }

        /**
         * 为已记录初始交易或固定源交易构造动作单，供后续交易校验读取渠道事实。
         */
        @Override
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            if (resultDTO != null && sourceTransactionId.equals(resultDTO.getTransactionId())) {
                TransactionOperationDO operationDO = new TransactionOperationDO();
                operationDO.setId(1L);
                operationDO.setOperationId(resultDTO.getOperationId());
                operationDO.setTransactionId(resultDTO.getTransactionId());
                operationDO.setMerchantId(commandDTO.getMerchantId());
                operationDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
                operationDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
                operationDO.setTransactionType(resultDTO.getTransactionType());
                operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
                operationDO.setTransactionAmount(commandDTO.getTransactionAmount());
                operationDO.setTransactionCurrency(commandDTO.getTransactionCurrency());
                PaymentChannelInvokeResultDTO invokeResultDTO = resultChannelInvokeResultDTO == null
                        ? channelInvokeResultDTO : resultChannelInvokeResultDTO;
                if (invokeResultDTO != null && invokeResultDTO.getChannelRequest() != null) {
                    operationDO.setChannelOrderNo(invokeResultDTO.getChannelRequest().getChannelOrderNo());
                    operationDO.setChannelTransactionId(invokeResultDTO.getChannelRequest().getChannelTransactionId());
                }
                operationDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
                operationDO.setVersion(0);
                return operationDO;
            }
            if (!SOURCE_TRANSACTION_ID.equals(sourceTransactionId) && !CAPTURE_TRANSACTION_ID.equals(sourceTransactionId)) {
                return null;
            }
            TransactionOperationDO operationDO = new TransactionOperationDO();
            operationDO.setOperationId(SOURCE_OPERATION_ID);
            operationDO.setTransactionId(sourceTransactionId);
            operationDO.setChannelOrderNo(SOURCE_TRANSACTION_ID);
            operationDO.setChannelTransactionId(SOURCE_TRANSACTION_ID.equals(sourceTransactionId)
                    ? SOURCE_CHANNEL_TRANSACTION_ID
                    : "CH202607141615000000999");
            operationDO.setTransactionDateTime(SOURCE_TRANSACTION_ID.equals(sourceTransactionId)
                    ? LocalDateTime.of(2026, 7, 12, 10, 30)
                    : LocalDateTime.of(2026, 7, 14, 16, 15));
            return operationDO;
        }

        private boolean isRecordedInitialOperation(LocalDateTime transactionDateTime, String operationId) {
            return resultDTO != null
                    && commandDTO != null
                    && transactionDateTime != null
                    && transactionDateTime.equals(commandDTO.getTransactionDateTime())
                    && resultDTO.getOperationId().equals(operationId);
        }

/**
 * 查询动作by商户订单，按调用方提供的过滤条件返回对应业务视图。
 * <p>
 * 前置条件：调用方已按 支付核心服务 的权限和数据范围传入查询条件。
 * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
 * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
 * @param merchantOrderNo merchant Order No 输入值，参与 商户订单no 的查询、校验、转换、写入或日志摘要
 * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @return 查询得到的业务对象、分页结果或空结果
 */
        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                          String merchantOrderNo,
                                                                          String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          LocalDateTime rootTransactionDateTime) {
            if (!"200001".equals(merchantId) || !"M202607120001".equals(merchantOrderNo)) {
                return List.of();
            }
            TransactionOperationDO operationDO = findSourceOperationByTransactionId(SOURCE_TRANSACTION_ID);
            operationDO.setMerchantId(merchantId);
            operationDO.setMerchantOrderNo(merchantOrderNo);
            operationDO.setMerchantOrderId("AUTH202607120001");
            operationDO.setTransactionType(sourceTransactionType);
            operationDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
            operationDO.setOperationSequence(1);
            operationDO.setOperationTime(LocalDateTime.of(2026, 7, 12, 10, 30));
            operationDO.setTransactionAmount(sourceTransactionAmount);
            operationDO.setTransactionCurrency("USD");
            operationDO.setAuthCode("123456");
            return StringUtils.hasText(transactionId) && !SOURCE_TRANSACTION_ID.equals(transactionId)
                    ? List.of()
                    : List.of(operationDO);
        }

        /**
         * 返回用例预置的初始动作集合，仅匹配固定商户订单。
         */
        @Override
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            if (!"200001".equals(merchantId) || !"M202607120001".equals(merchantOrderNo)) {
                return List.of();
            }
            return initialOperations;
        }

        /**
         * 固定返回无在途请款，使测试聚焦当前交易编排而不引入累计额度冲突。
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
         * 仅在固定渠道订单号和渠道交易号同时匹配时返回源动作单。
         */
        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            if (!SOURCE_TRANSACTION_ID.equals(channelOrderNo) || !SOURCE_CHANNEL_TRANSACTION_ID.equals(channelTransactionId)) {
                return null;
            }
            return findSourceOperationByTransactionId(SOURCE_TRANSACTION_ID);
        }

        /**
         * 固定返回无待匹配交易，避免后台恢复流程干扰服务主路径测试。
         */
        @Override
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of();
        }

        /**
         * 捕获请款、退款或撤销准备阶段生成的后续交易记录。
         */
        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
            this.followUpRecordDTO = recordDTO;
        }

        /**
         * 固定模拟回调终态 CAS 成功；当前用例不验证回调竞态。
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
            return true;
        }

        /**
         * 固定模拟渠道匹配状态更新成功，隔离非当前测试范围的持久化逻辑。
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
         * 固定模拟商户响应日志更新成功，当前替身不保存日志内容。
         */
        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return true;
        }
    }

    private static class CapturingTransactionEventOutboxService implements TransactionEventOutboxService {

        /** 按事务写入顺序捕获全部事件，验证创建事件和终态事件的先后关系。 */
        private final List<TransactionEventOutboxDO> events = new ArrayList<>();

        /**
         * event DO，用于保存 Capturing Transaction Event Outbox Service 中与 eventdo 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private TransactionEventOutboxDO eventDO;

        /**
         * 捕获交易生命周期事件，供用例核对已提交事实对应的 Outbox 内容。
         */
        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            if (this.eventDO == null) {
                this.eventDO = eventDO;
            }
            this.events.add(eventDO);
        }

        /**
         * 返回已捕获事件作为唯一到期记录，未保存事件时返回空集合。
         */
        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.copyOf(events);
        }

        /**
         * 在内存对象上标记发送成功，模拟 Outbox 状态更新。
         */
        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            eventDO.setEventStatus("SENT");
            eventDO.setSentTime(sentTime);
            return true;
        }

        /**
         * 在内存对象上记录发送失败及重试信息，模拟失败状态更新。
         */
        @Override
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            eventDO.setEventStatus("FAILED");
            eventDO.setNextRetryTime(nextRetryTime);
            eventDO.setFailReason(failReason);
            return true;
        }
    }

    private static class CapturingPaymentChannelInvokeService implements PaymentChannelInvokeService {

        /**
         * response，用于保存 Capturing Payment Channel Invoke Service 中与 response 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final ChannelPaymentResponse response;

        /**
         * command DTO，用于保存 Capturing Payment Channel Invoke Service 中与 commanddto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentCreateCommandDTO commandDTO;

        /**
         * route Result DTO，用于保存 Capturing Payment Channel Invoke Service 中与 routeresultdto 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentRouteResultDTO routeResultDTO;

        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、transactionType 共同定位一次交易动作。
         * </p>
         */
        private String operationId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
         */
        private String transactionId;

        /**
         * channel Order No，用于保存 Capturing Payment Channel Invoke Service 中与 渠道订单no 相关的业务属性。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String channelOrderNo;

        /**
         * 渠道调用前观察钩子，用于断言本地准备阶段锁已经释放。
         */
        private final Runnable beforeInvoke;

        private CapturingPaymentChannelInvokeService(ChannelPaymentResponse response) {
            this(response, () -> {
            });
        }

        private CapturingPaymentChannelInvokeService(ChannelPaymentResponse response, Runnable beforeInvoke) {
            this.response = response;
            this.beforeInvoke = beforeInvoke;
        }

        /**
         * 为旧重载补齐确定性渠道请求标识，再统一委托到可捕获参数的重载。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
            prepared.setRequestId("CR-" + transactionId);
            prepared.setChannelOrderNo(channelOrderNo);
            prepared.setChannelTransactionId("CH-" + transactionId);
            return invoke(commandDTO, routeResult, operationId, transactionId, prepared);
        }

        /**
         * 执行调用前观察钩子，捕获编排参数并返回用例预置的渠道响应。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    PaymentPreparedChannelRequestDTO preparedChannelRequest) {
            beforeInvoke.run();
            this.commandDTO = commandDTO;
            this.routeResultDTO = routeResult;
            this.operationId = operationId;
            this.transactionId = transactionId;
            this.channelOrderNo = preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo();
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId(preparedChannelRequest == null ? null : preparedChannelRequest.getRequestId());
            resultDTO.setChannelRequest(new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest());
            resultDTO.getChannelRequest().setChannelCode(routeResult.getChannelCode());
            resultDTO.getChannelRequest().setOperationId(operationId);
            resultDTO.getChannelRequest().setTransactionId(transactionId);
            resultDTO.getChannelRequest().setChannelOrderNo(preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo());
            resultDTO.getChannelRequest().setChannelTransactionId(preparedChannelRequest == null ? null : preparedChannelRequest.getChannelTransactionId());
            resultDTO.getChannelRequest().setAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
            resultDTO.getChannelRequest().setCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
            resultDTO.setChannelResponse(response);
            resultDTO.setRequestStatus("SUCCESS");
            return resultDTO;
        }
    }

    private static class FailingPaymentChannelInvokeService implements PaymentChannelInvokeService {

        /**
         * 模拟失败是否发生在可能已送达渠道的阶段。
         */
        private final boolean outcomeUncertain;

        private FailingPaymentChannelInvokeService(boolean outcomeUncertain) {
            this.outcomeUncertain = outcomeUncertain;
        }

        /**
         * 为旧重载生成确定性请求标识后进入统一的渠道失败模拟路径。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
            prepared.setRequestId("CR-" + transactionId);
            prepared.setChannelOrderNo(channelOrderNo);
            prepared.setChannelTransactionId("CH-" + transactionId);
            return invoke(commandDTO, routeResult, operationId, transactionId, prepared);
        }

        /**
         * 构造失败调用快照并抛出渠道异常，用于验证本地失败结果和幂等快照。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    PaymentPreparedChannelRequestDTO preparedChannelRequest) {
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId(preparedChannelRequest == null ? null : preparedChannelRequest.getRequestId());
            resultDTO.setChannelRequest(new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest());
            resultDTO.getChannelRequest().setChannelCode(routeResult.getChannelCode());
            resultDTO.getChannelRequest().setOperationId(operationId);
            resultDTO.getChannelRequest().setTransactionId(transactionId);
            resultDTO.getChannelRequest().setChannelOrderNo(preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo());
            resultDTO.getChannelRequest().setChannelTransactionId(preparedChannelRequest == null ? null : preparedChannelRequest.getChannelTransactionId());
            resultDTO.setRequestStatus("FAILED");
            resultDTO.setExceptionType("ChannelRequestException");
            String exceptionMessage = outcomeUncertain
                    ? "MPGS network request failed"
                    : "MPGS password is required";
            resultDTO.setExceptionMessage(exceptionMessage);
            resultDTO.setOutcomeUncertain(outcomeUncertain);
            throw new PaymentChannelInvokeException(resultDTO, new RuntimeException(exceptionMessage));
        }
    }

    private static class InMemoryTransactionIdempotencyService implements TransactionIdempotencyService {

        /**
         * records，用于保存 In Memory Transaction Idempotency Service 中与 记录 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final Map<String, TransactionIdempotencyDO> records = new LinkedHashMap<>();

        /**
         * 按商户、商户订单标识和交易类型生成稳定键，模拟数据库唯一约束使用的业务键。
         */
        @Override
        public String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType) {
            return String.join(":", merchantId, merchantOrderId, transactionType);
        }

        /**
         * 从内存记录表读取幂等记录，模拟数据库按作用域和键查询。
         */
        @Override
        public Optional<TransactionIdempotencyDO> find(String scope, String key) {
            return Optional.ofNullable(records.get(scope + ":" + key));
        }

        /**
         * 仅为固定源交易返回已成功的初始幂等记录，其他交易视为不存在。
         */
        @Override
        public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
            if (!SOURCE_TRANSACTION_ID.equals(transactionId)) {
                return Optional.empty();
            }
            TransactionIdempotencyDO record = new TransactionIdempotencyDO();
            record.setOperationId(SOURCE_OPERATION_ID);
            record.setTransactionId(transactionId);
            record.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
            record.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
            record.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
            return Optional.of(record);
        }

        /**
         * 以 put-if-absent 语义模拟数据库唯一键竞争，重复开始时返回失败。
         */
        @Override
        public boolean tryBegin(TransactionIdempotencyDO record) {
            String storageKey = record.getIdempotencyScope() + ":" + record.getIdempotencyKey();
            if (records.containsKey(storageKey)) {
                return false;
            }
            records.put(storageKey, record);
            return true;
        }

        /**
         * 将交易结果写回已占位的内存幂等记录，模拟同一数据库事务内完成快照。
         */
        @Override
        public void complete(String scope,
                             String key,
                             String operationId,
                             String transactionId,
                             String transactionStatus,
                             BigDecimal transactionAmount,
                             String transactionCurrency,
                             String resultSnapshot) {
            TransactionIdempotencyDO record = records.get(scope + ":" + key);
            record.setOperationId(operationId);
            record.setTransactionId(transactionId);
            record.setTransactionStatus(transactionStatus);
            record.setTransactionAmount(transactionAmount);
            record.setTransactionCurrency(transactionCurrency);
            record.setResultSnapshot(resultSnapshot);
        }

        /**
         * 按输入构造 PROCESSING 占位记录，不提前写入交易成功或失败结论。
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

    private static class EmptyInitialTransactionIdempotencyService extends InMemoryTransactionIdempotencyService {

        /**
         * 固定返回无初始交易，用于验证无法从数据库恢复源交易时的拒绝路径。
         */
        @Override
        public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
            return Optional.empty();
        }
    }

    private static class RejectingRedisLockService extends TestDistributedLockService {

        /**
         * 固定拒绝获取锁，用于验证并发请求不能进入本地交易准备阶段。
         */
        @Override
        public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
            return false;
        }

        /**
         * 空实现；拒绝获取锁后不应存在需要释放的锁状态。
         */
        @Override
        public void unlock(String key) {
        }
    }

    private static class TrackingRedisLockService extends TestDistributedLockService {

        /**
         * 记录每个测试锁 Key 使用的租约秒数，供用例核对锁时长边界。
         */
        private final Map<String, Long> lockTtlByKey = new LinkedHashMap<>();

        /**
         * 保存当前仍持有的锁及其 token，用于断言渠道调用前准备锁已释放。
         */
        private final Map<String, Boolean> activeLocks = new LinkedHashMap<>();

        /**
         * 记录租约和锁 token，并固定模拟成功获取锁。
         */
        @Override
        public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
            lockTtlByKey.put(key, leaseTime.toSeconds());
            activeLocks.put(key, Boolean.TRUE);
            return true;
        }

        /**
         * 仅当锁 Key 与 token 同时匹配时移除活动锁，模拟所有权校验释放。
         */
        @Override
        public void unlock(String key) {
            activeLocks.remove(key);
        }

        private boolean hasActiveLocks() {
            return !activeLocks.isEmpty();
        }
    }

    private static class FailingUnlockRedisLockService extends TestDistributedLockService {

        /**
         * 解锁调用次数，用于确认首次交易的两把准备锁都执行了释放尝试。
         */
        private int unlockAttempts;

        /**
         * 模拟成功获取 Redis 锁。
         *
         * @param key 锁 Key
         * @param waitTime 最大等待时间
         * @param leaseTime 锁租约
         * @return 固定返回 true
         */
        @Override
        public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
            return true;
        }

        /**
         * 模拟 Redis 在 compare-delete 阶段不可用。
         *
         * @param key 锁 Key
         */
        @Override
        public void unlock(String key) {
            unlockAttempts++;
            throw new IllegalStateException("redis unavailable");
        }
    }

    private abstract static class TestDistributedLockService implements DistributedLockService {

        /**
         * 将测试看门狗锁统一映射为 30 秒固定租约锁，复用子类竞争行为。
         *
         * @param key 测试锁 Key
         * @param waitTime 测试等待时间
         * @return 子类固定租约锁的获取结果
         */
        @Override
        public boolean tryLockWithWatchdog(String key, Duration waitTime) {
            return tryLock(key, waitTime, Duration.ofSeconds(30));
        }

        /**
         * 获取测试固定租约锁，竞争失败时保留真实接口的异常契约。
         *
         * @param key 测试锁 Key
         * @param waitTime 测试等待时间
         * @param leaseTime 测试固定租约
         */
        @Override
        public void lock(String key, Duration waitTime, Duration leaseTime) {
            if (!tryLock(key, waitTime, leaseTime)) {
                throw new DistributedLockBusyException("test lock is busy");
            }
        }

        /**
         * 测试替身不维护线程所有权，统一视为当前线程持锁。
         *
         * @param key 测试锁 Key
         * @return 固定返回 true
         */
        @Override
        public boolean isHeldByCurrentThread(String key) {
            return true;
        }

        @Override
        public <T> DistributedLockExecution<T> execute(String key,
                                                       Duration waitTime,
                                                       Duration leaseTime,
                                                       Supplier<T> action) {
            if (!tryLock(key, waitTime, leaseTime)) {
                return DistributedLockExecution.contended();
            }
            try {
                return DistributedLockExecution.acquired(action.get());
            } finally {
                unlock(key);
            }
        }
    }

}

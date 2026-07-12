package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.redis.lock.RedisLockService;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionServiceImplTests
 * @date : 2026-07-12 20:50
 * @email : scott_x@163.com
 * @description : 收单交易服务单元测试，验证创建授权交易时的幂等、风控短路、渠道路由和渠道响应状态映射。
 * @status : create
 */
class PaymentTransactionServiceImplTests {

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
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");

        PaymentCreateResultDTO resultDTO = service.createAuthorization(commandDTO);

        assertThat(resultDTO.getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(resultDTO.getProcessStage()).isEqualTo(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        assertThat(resultDTO.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        assertThat(resultDTO.getTransactionOrderNo()).startsWith("TO");
        assertThat(resultDTO.getTransactionNo()).startsWith("TX");
        assertThat(resultDTO.getPaymentOrderNo()).isEqualTo(resultDTO.getTransactionOrderNo());
        assertThat(resultDTO.getAmount()).isEqualTo(1234L);
        assertThat(channelInvokeService.commandDTO).isSameAs(commandDTO);
        assertThat(channelInvokeService.routeResultDTO.getChannelCode()).isEqualTo("MPGS");
        assertThat(channelInvokeService.transactionOrderNo).isEqualTo(resultDTO.getTransactionOrderNo());
        assertThat(channelInvokeService.transactionNo).isEqualTo(resultDTO.getTransactionNo());
        assertThat(eventOutboxService.eventDO.getMessageKey()).isEqualTo(resultDTO.getTransactionNo());
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("INIT");
        assertThat(idempotencyService.find("PAYMENT_CREATE", "200001:M202607120001:AUTHORIZATION"))
                .get()
                .extracting(TransactionIdempotencyDO::getTransactionNo)
                .isEqualTo(resultDTO.getTransactionNo());
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
        assertThat(eventOutboxService.eventDO).isNull();
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
        assertThat(eventOutboxService.eventDO).isNull();
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

        assertThat(repeatedResult.getTransactionNo()).isEqualTo(firstResult.getTransactionNo());
        assertThat(repeatedResult.getTransactionOrderNo()).isEqualTo(firstResult.getTransactionOrderNo());
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

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createAuthorization(commandDTO))
                .hasMessage("The network is busy, please try again later");
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
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
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
                                                     List<RedisLockService> redisLockServices) {
        return newService(riskInvokeService, idempotencyService, eventOutboxService, redisLockServices,
                new CapturingPaymentChannelInvokeService(channelResponse(ChannelTradeStatus.PROCESSING)));
    }

    private PaymentTransactionServiceImpl newService(PaymentRiskInvokeService riskInvokeService,
                                                     InMemoryTransactionIdempotencyService idempotencyService,
                                                     CapturingTransactionEventOutboxService eventOutboxService,
                                                     List<RedisLockService> redisLockServices,
                                                     PaymentChannelInvokeService channelInvokeService) {
        return new PaymentTransactionServiceImpl(
                isoDictionaryService(),
                riskInvokeService,
                routeService(),
                channelInvokeService,
                idempotencyService,
                eventOutboxService,
                redisLockServices);
    }

    private PaymentChannelRouteService routeService() {
        return commandDTO -> {
            PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
            routeResultDTO.setMidConfigId(1001L);
            routeResultDTO.setMidNo("TESTDEVMER031");
            return routeResultDTO;
        };
    }

    private ChannelPaymentResponse channelResponse(ChannelTradeStatus tradeStatus) {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelTradeStatus(tradeStatus.getCode());
        response.setChannelResponseCode(ChannelTradeStatus.SUCCESS == tradeStatus ? "00" : "05");
        response.setChannelResponseMessage(ChannelTradeStatus.SUCCESS == tradeStatus ? "Approved" : "Declined");
        return response;
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
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCurrencyInfo> getCurrency(String value) {
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
        };
    }

    private static class CapturingTransactionEventOutboxService implements TransactionEventOutboxService {

        private TransactionEventOutboxDO eventDO;

        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return eventDO == null ? List.of() : List.of(eventDO);
        }

        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            eventDO.setEventStatus("SENT");
            eventDO.setSentTime(sentTime);
            return true;
        }

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

        private final ChannelPaymentResponse response;

        private PaymentCreateCommandDTO commandDTO;

        private PaymentRouteResultDTO routeResultDTO;

        private String transactionOrderNo;

        private String transactionNo;

        private CapturingPaymentChannelInvokeService(ChannelPaymentResponse response) {
            this.response = response;
        }

        @Override
        public ChannelPaymentResponse invoke(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResult,
                                             String transactionOrderNo,
                                             String transactionNo) {
            this.commandDTO = commandDTO;
            this.routeResultDTO = routeResult;
            this.transactionOrderNo = transactionOrderNo;
            this.transactionNo = transactionNo;
            return response;
        }
    }

    private static class InMemoryTransactionIdempotencyService implements TransactionIdempotencyService {

        private final Map<String, TransactionIdempotencyDO> records = new LinkedHashMap<>();

        @Override
        public String buildPaymentCreateKey(String merchantId, String merchantOrderNo, String transactionType) {
            return String.join(":", merchantId, merchantOrderNo, transactionType);
        }

        @Override
        public Optional<TransactionIdempotencyDO> find(String scope, String key) {
            return Optional.ofNullable(records.get(scope + ":" + key));
        }

        @Override
        public boolean tryBegin(TransactionIdempotencyDO record) {
            String storageKey = record.getIdempotencyScope() + ":" + record.getIdempotencyKey();
            if (records.containsKey(storageKey)) {
                return false;
            }
            records.put(storageKey, record);
            return true;
        }

        @Override
        public void complete(String scope,
                             String key,
                             String transactionOrderNo,
                             String transactionNo,
                             String transactionStatus,
                             Long transactionAmountMinor,
                             String transactionCurrency,
                             String resultSnapshot) {
            TransactionIdempotencyDO record = records.get(scope + ":" + key);
            record.setTransactionOrderNo(transactionOrderNo);
            record.setTransactionNo(transactionNo);
            record.setTransactionStatus(transactionStatus);
            record.setTransactionAmountMinor(transactionAmountMinor);
            record.setTransactionCurrency(transactionCurrency);
            record.setResultSnapshot(resultSnapshot);
        }

        @Override
        public TransactionIdempotencyDO newProcessingRecord(String scope,
                                                            String key,
                                                            String merchantId,
                                                            String merchantOrderNo,
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
            record.setTransactionType(transactionType);
            record.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            record.setTransactionDateTime(transactionDateTime);
            record.setTimeZone(timeZone);
            return record;
        }
    }

    private static class RejectingRedisLockService implements RedisLockService {

        @Override
        public boolean tryLock(String key, String value, long ttlSeconds) {
            return false;
        }

        @Override
        public void unlock(String key, String value) {
        }
    }
}

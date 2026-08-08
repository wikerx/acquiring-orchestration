package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.config.MerchantNotificationProperties;
import com.scott.payment.payment.domain.state.PaymentCheckoutAttemptStatusEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutPageStateEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutSessionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSecurityEventDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.entity.PaymentCheckoutTokenDO;
import com.scott.payment.payment.mapper.PaymentCheckoutAttemptMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSecurityEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutTokenMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.security.PaymentCheckoutCardEnvelopeService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.support.PaymentCheckoutTokenSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Hosted Checkout 内部服务单元测试。
 */
class DefaultPaymentCheckoutServiceTests {

    /** Hosted Checkout 会话持久化 Mapper 测试替身。 */
    private PaymentCheckoutSessionMapper sessionMapper;

    /** 不透明 Token 持久化 Mapper 测试替身。 */
    private PaymentCheckoutTokenMapper tokenMapper;

    /** 支付尝试持久化 Mapper 测试替身。 */
    private PaymentCheckoutAttemptMapper attemptMapper;

    /** Checkout 生命周期事件 Mapper 测试替身。 */
    private PaymentCheckoutEventMapper eventMapper;

    /** Checkout 安全审计事件 Mapper 测试替身。 */
    private PaymentCheckoutSecurityEventMapper securityEventMapper;

    /** 3DS 状态和返回 Token 处理服务测试替身。 */
    private PaymentCheckoutThreeDsService threeDsService;

    /** 支付核心交易提交服务测试替身。 */
    private PaymentTransactionService paymentTransactionService;

    /** 立即执行回调的事务模板，避免单元测试依赖真实数据库事务。 */
    private TransactionOperations transactionOperations;

    /** 商户通知任务 Mapper 测试替身。 */
    private TransactionMerchantNotificationMapper merchantNotificationMapper;

    /** Hosted Checkout TTL、重试次数和轮询间隔测试配置。 */
    private PaymentCheckoutProperties properties;

    /** 每个用例重新构建的被测服务。 */
    private DefaultPaymentCheckoutService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(PaymentCheckoutSessionMapper.class);
        tokenMapper = mock(PaymentCheckoutTokenMapper.class);
        attemptMapper = mock(PaymentCheckoutAttemptMapper.class);
        eventMapper = mock(PaymentCheckoutEventMapper.class);
        securityEventMapper = mock(PaymentCheckoutSecurityEventMapper.class);
        threeDsService = mock(PaymentCheckoutThreeDsService.class);
        paymentTransactionService = mock(PaymentTransactionService.class);
        merchantNotificationMapper = mock(TransactionMerchantNotificationMapper.class);
        transactionOperations = new ImmediateTransactionOperations();
        properties = new PaymentCheckoutProperties();
        properties.setTokenPepper("unit-test-hosted-checkout-token-pepper");
        properties.setTokenKeyVersion("test-v1");
        service = new DefaultPaymentCheckoutService(
                sessionMapper,
                tokenMapper,
                attemptMapper,
                eventMapper,
                securityEventMapper,
                new SequenceGlobalIdGenerator(),
                properties,
                threeDsService,
                paymentTransactionService,
                merchantNotificationMapper,
                new MerchantNotificationProperties(),
                transactionOperations);
    }

    @Test
    void shouldCreateSessionAndStoreOnlyTokenHash() {
        PaymentCheckoutSessionCreateCommandDTO commandDTO = createCommand();
        ArgumentCaptor<PaymentCheckoutSessionDO> sessionCaptor = ArgumentCaptor.forClass(PaymentCheckoutSessionDO.class);
        ArgumentCaptor<PaymentCheckoutTokenDO> tokenCaptor = ArgumentCaptor.forClass(PaymentCheckoutTokenDO.class);

        PaymentCheckoutSessionCreateResultDTO resultDTO = service.createSession(commandDTO);

        verify(sessionMapper).insert(sessionCaptor.capture());
        verify(tokenMapper).insert(tokenCaptor.capture());
        PaymentCheckoutSessionDO sessionDO = sessionCaptor.getValue();
        PaymentCheckoutTokenDO tokenDO = tokenCaptor.getValue();
        assertThat(resultDTO.getCheckoutUrl()).startsWith("https://pay.example.com/checkout/");
        assertThat(resultDTO.getIdempotentHit()).isFalse();
        assertThat(sessionDO.getCheckoutStatus()).isEqualTo(PaymentCheckoutSessionStatusEnum.PAYABLE.getCode());
        assertThat(sessionDO.getAllowedPaymentMethodsJson()).contains("\"paymentMethod\":\"BANK_CARD\"");
        assertThat(tokenDO.getTokenHash()).hasSize(64);
        assertThat(tokenDO.getTokenHashAlg()).isEqualTo(PaymentCheckoutTokenSupport.TOKEN_HASH_ALG);
        assertThat(tokenDO.getTokenKeyVersion()).isEqualTo("test-v1");
        assertThat(tokenDO.getIssueReason()).isEqualTo("SESSION_CREATE");
        assertThat(sessionDO.getExpireTime()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(tokenDO.getExpireTime()).isAfter(sessionDO.getExpireTime().plusDays(28));
        assertThat(resultDTO.getCheckoutUrl()).doesNotContain(tokenDO.getTokenHash());
    }

    @Test
    void shouldReissueTokenWhenIdempotentCreateMatchesFingerprint() {
        PaymentCheckoutSessionDO existed = payableSession();
        existed.setRequestFingerprint("fp-001");
        when(sessionMapper.selectByMerchantRequest("200001", "REQ-001")).thenReturn(existed);

        PaymentCheckoutSessionCreateResultDTO resultDTO = service.createSession(createCommand());

        assertThat(resultDTO.getIdempotentHit()).isTrue();
        assertThat(resultDTO.getCheckoutSessionId()).isEqualTo(existed.getCheckoutSessionId());
        verify(sessionMapper, never()).insert(any(PaymentCheckoutSessionDO.class));
        verify(tokenMapper).insert(any(PaymentCheckoutTokenDO.class));
    }

    @Test
    void shouldRejectIdempotentCreateWhenFingerprintDiffers() {
        PaymentCheckoutSessionDO existed = payableSession();
        existed.setRequestFingerprint("fp-old");
        when(sessionMapper.selectByMerchantRequest("200001", "REQ-001")).thenReturn(existed);

        assertThatThrownBy(() -> service.createSession(createCommand()))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode());
    }

    @Test
    void shouldReportRecognizedButUnsupportedCardBin() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(sessionDO));
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO);
        PaymentCheckoutCardBinCommandDTO commandDTO = new PaymentCheckoutCardBinCommandDTO();
        commandDTO.setTokenHash("token-hash");
        commandDTO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        commandDTO.setCardBin("378282");

        PaymentCheckoutCardBinResultDTO resultDTO = service.resolveCardBin(commandDTO);

        assertThat(resultDTO.getCardBrand()).isEqualTo("AMEX");
        assertThat(resultDTO.getRecognized()).isTrue();
        assertThat(resultDTO.getSupported()).isFalse();
    }

    @Test
    void shouldRejectUnsupportedCardBrandBeforeCreatingAttempt() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(sessionDO));
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO);
        PaymentCheckoutPaymentSubmitCommandDTO commandDTO = submitCommand();
        commandDTO.getCardInfo().setCardNo("378282246310005");

        assertThatThrownBy(() -> service.submitPayment(commandDTO))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CARD_NOT_SUPPORTED.getCode());
        verify(attemptMapper, never()).insert(any(PaymentCheckoutAttemptDO.class));
        verify(paymentTransactionService, never()).createPayment(any());
    }

    @Test
    void shouldReturnBlockedWithoutOrderDetailsWhenTokenInvalid() {
        PaymentCheckoutSessionQueryCommandDTO commandDTO = new PaymentCheckoutSessionQueryCommandDTO();
        commandDTO.setTokenHash("missing-token-hash");
        commandDTO.setClientIpHash("ip-hash");

        PaymentCheckoutSessionQueryResultDTO resultDTO = service.querySession(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.BLOCKED.getCode());
        assertThat(resultDTO.getOrder()).isNull();
        assertThat(resultDTO.getMerchant()).isNull();
        verify(securityEventMapper).insert(any(PaymentCheckoutSecurityEventDO.class));
    }

    @Test
    void shouldQueryPayableSessionAndMarkTokenUsed() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO);
        PaymentCheckoutSessionQueryCommandDTO commandDTO = new PaymentCheckoutSessionQueryCommandDTO();
        commandDTO.setTokenHash("token-hash");
        commandDTO.setClientIpHash("ip-hash");
        commandDTO.setUserAgentHash("ua-hash");

        PaymentCheckoutSessionQueryResultDTO resultDTO = service.querySession(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PAYABLE.getCode());
        assertThat(resultDTO.getOrder().getAmount()).isEqualByComparingTo("49.97");
        assertThat(resultDTO.getPaymentMethods()).hasSize(1);
        verify(tokenMapper).markUsed(eq("token-hash"), eq("ip-hash"), eq("ua-hash"), any());
        verify(sessionMapper).markOpened(eq(sessionDO.getCheckoutSessionId()), any());
    }

    @Test
    void shouldRenderFullOrderAfterPaymentDeadlineExpires() {
        PaymentCheckoutSessionDO sessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.EXPIRED);
        sessionDO.setExpireTime(LocalDateTime.now().minusMinutes(1));
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        tokenDO.setExpireTime(LocalDateTime.now().plusDays(30));
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO);
        PaymentCheckoutSessionQueryCommandDTO commandDTO = new PaymentCheckoutSessionQueryCommandDTO();
        commandDTO.setTokenHash("token-hash");

        PaymentCheckoutSessionQueryResultDTO resultDTO = service.querySession(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.EXPIRED.getCode());
        assertThat(resultDTO.getMerchant().getDisplayName()).isEqualTo("Scott Demo Store");
        assertThat(resultDTO.getOrder().getOrderNo()).isEqualTo("M202607270001");
        assertThat(resultDTO.getCheckout().getRetryAllowed()).isTrue();
    }

    @Test
    void shouldExpireDuePayableSessionsWithCas() {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutSessionDO first = payableSession();
        first.setExpireTime(now.minusMinutes(5));
        first.setMerchantNotifyUrlCiphertext(com.scott.payment.component.security.crypto.SensitiveFieldCipher.encrypt(
                "https://merchant.example/notify?key=secret",
                properties.getSensitiveFieldEncryptionKey(),
                first.getMerchantId() + "|" + first.getMerchantOrderNo()));
        PaymentCheckoutSessionDO second = payableSession();
        second.setCheckoutSessionId("2607271200000000000099");
        second.setExpireTime(now.minusMinutes(1));
        when(sessionMapper.selectExpireDue(now, 100)).thenReturn(List.of(first, second));
        when(sessionMapper.markExpiredCas(anyString(), anyString(), eq(0), eq(now))).thenReturn(1, 0);

        int expired = service.expireDue(now, 100);

        assertThat(expired).isEqualTo(1);
        verify(eventMapper).insert(any(com.scott.payment.payment.entity.PaymentCheckoutEventDO.class));
        verify(merchantNotificationMapper).insertLogical(any(com.scott.payment.payment.entity.TransactionMerchantNotificationDO.class));
    }

    @Test
    void shouldSubmitDirectPaymentWithoutInvokingThreeDs() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutSessionDO succeededSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO channelSubmittedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        PaymentCreateResultDTO coreResult = successPaymentResult();
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, processingSessionDO, succeededSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(channelSubmittedAttempt, succeededAttempt);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(0), any()))
                .thenReturn(1);
        when(paymentTransactionService.createPayment(any())).thenReturn(coreResult);
        ArgumentCaptor<PaymentCheckoutAttemptDO> attemptCaptor = ArgumentCaptor.forClass(PaymentCheckoutAttemptDO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        verify(attemptMapper).insert(attemptCaptor.capture());
        PaymentCheckoutAttemptDO attemptDO = attemptCaptor.getValue();
        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.SUCCEEDED.getCode());
        assertThat(attemptDO.getCardNumberMasked()).isEqualTo("512345******0008");
        assertThat(attemptDO.getCardBin()).isEqualTo("512345");
        assertThat(attemptDO.getCardLast4()).isEqualTo("0008");
        assertThat(attemptDO.getPaymentAccountHash()).hasSize(64);
        assertThat(attemptDO.getPaymentAccountHash()).doesNotContain("512345");
        assertThat(attemptDO.getBrowserInfoJson()).contains("\"securityCode\":\"***\"");
        assertThat(attemptDO.getAttemptStatus()).isEqualTo(PaymentCheckoutAttemptStatusEnum.CARD_SUBMITTED.getCode());
        verify(threeDsService, never()).authenticate(any(), any(), any(), anyString());
        verify(paymentTransactionService).createPayment(any());
    }

    @Test
    void shouldIgnoreThreeDsChallengeConfigurationAndSubmitDirectPayment() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO channelSubmittedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO processingAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.PROCESSING,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL);
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, processingSessionDO, processingSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(channelSubmittedAttempt, processingAttempt);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(threeDsService.authenticate(any(), any(), any(), anyString())).thenThrow(new AssertionError("3DS must be disabled"));
        when(paymentTransactionService.createPayment(any())).thenReturn(processingPaymentResult());

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        assertThat(resultDTO.getThreeDsAction()).isNull();
        verify(threeDsService, never()).authenticate(any(), any(), any(), anyString());
        verify(paymentTransactionService).createPayment(any());
    }

    @Test
    void shouldCreateNewTransactionWhenRetryingFailedPayment() {
        PaymentCheckoutSessionDO failedSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE);
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO channelSubmittedAttempt = newRetryAttempt(failedSessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL,
                "ATTEMPT-RETRY-002");
        PaymentCheckoutAttemptDO processingAttempt = newRetryAttempt(failedSessionDO,
                PaymentCheckoutAttemptStatusEnum.PROCESSING,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL,
                "ATTEMPT-RETRY-002");
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(failedSessionDO));
        when(sessionMapper.selectByCheckoutSessionId(failedSessionDO.getCheckoutSessionId()))
                .thenReturn(failedSessionDO, payingSessionDO, processingSessionDO, processingSessionDO);
        when(attemptMapper.selectMaxAttemptNo(failedSessionDO.getCheckoutSessionId())).thenReturn(1);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(channelSubmittedAttempt, processingAttempt);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(paymentTransactionService.createPayment(any())).thenReturn(processingPaymentResult());
        PaymentCheckoutPaymentSubmitCommandDTO commandDTO = submitCommand();
        commandDTO.setAttemptRequestId("ATTEMPT-RETRY-002");
        ArgumentCaptor<PaymentCheckoutAttemptDO> attemptCaptor = ArgumentCaptor.forClass(PaymentCheckoutAttemptDO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(commandDTO);

        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        assertThat(attemptCaptor.getValue().getAttemptNo()).isEqualTo(2);
        assertThat(attemptCaptor.getValue().getTransactionId())
                .isEqualTo(channelSubmittedAttempt.getTransactionId())
                .isNotEqualTo(failedSessionDO.getLatestTransactionId());
    }

    @Test
    void shouldReopenExpiredSessionAndCreateNewTransaction() {
        PaymentCheckoutSessionDO expiredSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.EXPIRED);
        expiredSessionDO.setExpireTime(LocalDateTime.now().minusMinutes(1));
        PaymentCheckoutSessionDO reopenedSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYABLE);
        reopenedSessionDO.setExpireTime(LocalDateTime.now().plusHours(24));
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO channelSubmittedAttempt = newRetryAttempt(reopenedSessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL,
                "ATTEMPT-EXPIRED-002");
        PaymentCheckoutAttemptDO processingAttempt = newRetryAttempt(reopenedSessionDO,
                PaymentCheckoutAttemptStatusEnum.PROCESSING,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL,
                "ATTEMPT-EXPIRED-002");
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(expiredSessionDO));
        when(sessionMapper.selectByCheckoutSessionId(expiredSessionDO.getCheckoutSessionId()))
                .thenReturn(expiredSessionDO, reopenedSessionDO, payingSessionDO, processingSessionDO, processingSessionDO);
        when(sessionMapper.reopenExpiredForRetryCas(eq(expiredSessionDO.getCheckoutSessionId()), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectMaxAttemptNo(expiredSessionDO.getCheckoutSessionId())).thenReturn(1);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(channelSubmittedAttempt, processingAttempt);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(paymentTransactionService.createPayment(any())).thenReturn(processingPaymentResult());
        PaymentCheckoutPaymentSubmitCommandDTO commandDTO = submitCommand();
        commandDTO.setAttemptRequestId("ATTEMPT-EXPIRED-002");
        ArgumentCaptor<PaymentCheckoutAttemptDO> attemptCaptor = ArgumentCaptor.forClass(PaymentCheckoutAttemptDO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(commandDTO);

        verify(sessionMapper).reopenExpiredForRetryCas(eq(expiredSessionDO.getCheckoutSessionId()), any(), eq(0), any());
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        assertThat(attemptCaptor.getValue().getAttemptNo()).isEqualTo(2);
        assertThat(attemptCaptor.getValue().getTransactionId())
                .isEqualTo(channelSubmittedAttempt.getTransactionId())
                .isNotEqualTo(expiredSessionDO.getLatestTransactionId());
    }

    @Test
    void shouldBlockInvalidThreeDsReturnToken() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("invalid-hash");
        PaymentCheckoutAttemptDO attemptDO = processingAttempt(payableSession());
        attemptDO.setAttemptStatus(PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode());
        attemptDO.setThreeDsReturnTokenHash("expected-hash");
        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId())).thenReturn(attemptDO);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.BLOCKED.getCode());
        verify(securityEventMapper).insert(any(PaymentCheckoutSecurityEventDO.class));
    }

    @Test
    void shouldNotMarkSuccessOnThreeDsReturn() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        PaymentCheckoutSessionDO sessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO attemptDO = processingAttempt(sessionDO);
        attemptDO.setAttemptStatus(PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode());
        attemptDO.setThreeDsReturnTokenHash("return-token-hash");
        PaymentCheckoutAttemptDO returnedAttempt = processingAttempt(sessionDO);
        returnedAttempt.setAttemptStatus(PaymentCheckoutAttemptStatusEnum.PROCESSING.getCode());
        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId())).thenReturn(attemptDO, returnedAttempt);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO, processingSessionDO);
        when(attemptMapper.markThreeDsReturnedCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verify(sessionMapper, never()).markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void shouldReturnExistingAttemptWhenSubmitRequestIsRepeated() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        PaymentCheckoutAttemptDO attemptDO = processingAttempt(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO);
        when(attemptMapper.selectByAttemptRequest(sessionDO.getCheckoutSessionId(), "ATTEMPT-001")).thenReturn(attemptDO);

        PaymentCheckoutCardEnvelopeService envelopeService = mock(PaymentCheckoutCardEnvelopeService.class);
        ReflectionTestUtils.setField(service, "cardEnvelopeService", envelopeService);
        PaymentCheckoutPaymentSubmitCommandDTO commandDTO = submitCommand();
        commandDTO.setCardInfo(null);
        commandDTO.setCardDataEnvelope(new PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO());

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(commandDTO);

        assertThat(resultDTO.getCheckoutAttemptId()).isEqualTo(attemptDO.getCheckoutAttemptId());
        verify(attemptMapper, never()).insert(any(PaymentCheckoutAttemptDO.class));
        verify(sessionMapper, never()).markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any(), any());
        verifyNoInteractions(envelopeService);
    }

    private PaymentCheckoutSessionCreateCommandDTO createCommand() {
        PaymentCheckoutSessionCreateCommandDTO commandDTO = new PaymentCheckoutSessionCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607270001");
        commandDTO.setMerchantRequestId("REQ-001");
        commandDTO.setRequestFingerprint("fp-001");
        commandDTO.setAmount(new BigDecimal("49.97"));
        commandDTO.setCurrency("USD");
        commandDTO.setCurrencyExponent(2);
        commandDTO.setCheckoutDomain("https://pay.example.com");
        commandDTO.setMerchantDisplayName("Scott Demo Store");
        commandDTO.setOrderSubject("Demo Order");
        commandDTO.setRetryAllowed(1);
        commandDTO.setMaxAttemptCount(3);
        PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO methodDTO =
                new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        methodDTO.setPaymentMethod("BANK_CARD");
        methodDTO.setChannelCode("MPGS");
        methodDTO.setBrands(List.of("VISA", "MASTERCARD"));
        methodDTO.setThreeDsMode("AUTO");
        commandDTO.setAllowedPaymentMethods(List.of(methodDTO));
        return commandDTO;
    }

    private PaymentCheckoutPaymentSubmitCommandDTO submitCommand() {
        PaymentCheckoutPaymentSubmitCommandDTO commandDTO = new PaymentCheckoutPaymentSubmitCommandDTO();
        commandDTO.setTokenHash("token-hash");
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setAttemptRequestId("ATTEMPT-001");
        commandDTO.setPaymentMethod("BANK_CARD");
        commandDTO.setRequestFingerprint("submit-fp");
        commandDTO.setBrowserInfoJson("{\"securityCode\":\"123\",\"cardNo\":\"5123456789010008\"}");
        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123456789010008");
        cardInfoDTO.setSecurityCode("123");
        cardInfoDTO.setExpirationMonth("12");
        cardInfoDTO.setExpirationYear("2030");
        cardInfoDTO.setCardholderName("SCOTT BUYER");
        commandDTO.setCardInfo(cardInfoDTO);
        PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO billingDTO =
                new PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO();
        billingDTO.setFirstName("Scott");
        billingDTO.setLastName("Buyer");
        billingDTO.setEmail("buyer@example.com");
        billingDTO.setCountry("USA");
        commandDTO.setBillingCardHolderInfo(billingDTO);
        return commandDTO;
    }

    private PaymentCheckoutSessionDO payableSession() {
        PaymentCheckoutSessionDO sessionDO = new PaymentCheckoutSessionDO();
        sessionDO.setCheckoutSessionId("2607271200000000000010");
        sessionDO.setMerchantId("200001");
        sessionDO.setMerchantOrderNo("M202607270001");
        sessionDO.setMerchantRequestId("REQ-001");
        sessionDO.setRequestFingerprint("fp-001");
        sessionDO.setCheckoutStatus(PaymentCheckoutSessionStatusEnum.PAYABLE.getCode());
        sessionDO.setProcessStage("WAITING_PAYER");
        sessionDO.setLabelAmount(new BigDecimal("49.97"));
        sessionDO.setLabelCurrency("USD");
        sessionDO.setCurrencyExponent(2);
        sessionDO.setOrderSubject("Demo Order");
        sessionDO.setMerchantDisplayName("Scott Demo Store");
        sessionDO.setAllowedPaymentMethodsJson("""
                [{"paymentMethod":"BANK_CARD","channelCode":"MPGS","brands":["VISA","MASTERCARD"],"threeDsMode":"AUTO"}]
                """);
        sessionDO.setRetryAllowed(1);
        sessionDO.setMaxAttemptCount(3);
        sessionDO.setAttemptCount(0);
        sessionDO.setCheckoutDomain("https://pay.example.com");
        sessionDO.setExpireTime(LocalDateTime.now().plusMinutes(30));
        sessionDO.setVersion(0);
        sessionDO.setDeleted(0);
        return sessionDO;
    }

    private PaymentCheckoutSessionDO sessionWithStatus(PaymentCheckoutSessionStatusEnum statusEnum) {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        sessionDO.setCheckoutStatus(statusEnum.getCode());
        sessionDO.setProcessStage(statusEnum == PaymentCheckoutSessionStatusEnum.AUTHENTICATING
                ? PaymentCheckoutProcessStageEnum.WAITING_3DS.getCode()
                : PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode());
        sessionDO.setAttemptCount(1);
        sessionDO.setLastAttemptId("2607271200000000000038");
        sessionDO.setLatestTransactionId("2607271200000000000047");
        return sessionDO;
    }

    private PaymentCheckoutTokenDO activeToken(PaymentCheckoutSessionDO sessionDO) {
        PaymentCheckoutTokenDO tokenDO = new PaymentCheckoutTokenDO();
        tokenDO.setCheckoutTokenId("2607271200000000000029");
        tokenDO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        tokenDO.setMerchantId(sessionDO.getMerchantId());
        tokenDO.setTokenHash("token-hash");
        tokenDO.setTokenStatus("ACTIVE");
        tokenDO.setExpireTime(LocalDateTime.now().plusMinutes(30));
        tokenDO.setVersion(0);
        tokenDO.setDeleted(0);
        return tokenDO;
    }

    private PaymentCheckoutAttemptDO processingAttempt(PaymentCheckoutSessionDO sessionDO) {
        PaymentCheckoutAttemptDO attemptDO = new PaymentCheckoutAttemptDO();
        attemptDO.setCheckoutAttemptId("2607271200000000000038");
        attemptDO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        attemptDO.setMerchantId(sessionDO.getMerchantId());
        attemptDO.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        attemptDO.setPaymentMethod("BANK_CARD");
        attemptDO.setPaymentBrand("MASTERCARD");
        attemptDO.setAttemptStatus("PROCESSING");
        attemptDO.setLabelAmount(sessionDO.getLabelAmount());
        attemptDO.setLabelCurrency(sessionDO.getLabelCurrency());
        attemptDO.setTransactionId("2607271200000000000047");
        attemptDO.setTransactionDateTime(LocalDateTime.now());
        attemptDO.setVersion(0);
        return attemptDO;
    }

    private PaymentCheckoutAttemptDO attemptWithStatus(PaymentCheckoutSessionDO sessionDO,
                                                       PaymentCheckoutAttemptStatusEnum statusEnum,
                                                       PaymentCheckoutProcessStageEnum stageEnum) {
        PaymentCheckoutAttemptDO attemptDO = processingAttempt(sessionDO);
        attemptDO.setAttemptStatus(statusEnum.getCode());
        attemptDO.setProcessStage(stageEnum.getCode());
        attemptDO.setThreeDsReturnTokenHash("return-token-hash");
        attemptDO.setChannelResponseCode("AUTH123");
        return attemptDO;
    }

    private PaymentCheckoutAttemptDO newRetryAttempt(PaymentCheckoutSessionDO sessionDO,
                                                     PaymentCheckoutAttemptStatusEnum statusEnum,
                                                     PaymentCheckoutProcessStageEnum stageEnum,
                                                     String attemptRequestId) {
        PaymentCheckoutAttemptDO attemptDO = attemptWithStatus(sessionDO, statusEnum, stageEnum);
        attemptDO.setCheckoutAttemptId("2607271200000000001");
        attemptDO.setAttemptRequestId(attemptRequestId);
        attemptDO.setAttemptNo(2);
        attemptDO.setOperationId("OP2607271200000000002");
        attemptDO.setTransactionId("2607271200000000003");
        return attemptDO;
    }

    private PaymentCheckoutThreeDsResultDTO passedThreeDsResult() {
        PaymentCheckoutThreeDsResultDTO resultDTO = new PaymentCheckoutThreeDsResultDTO();
        resultDTO.setStatus("PASSED");
        resultDTO.setAuthenticationTransactionId("3DS2607271200000000000047");
        resultDTO.setChannelOrderNo("2607271200000000000047");
        resultDTO.setThreeDsStatus("AUTHENTICATION_SUCCESSFUL");
        resultDTO.setThreeDsVersion("3DS2");
        resultDTO.setDsTransactionId("ds-tx-001");
        resultDTO.setEci("05");
        resultDTO.setCavv("masked-token");
        return resultDTO;
    }

    private PaymentCheckoutThreeDsResultDTO challengeThreeDsResult() {
        PaymentCheckoutThreeDsResultDTO resultDTO = passedThreeDsResult();
        resultDTO.setStatus("CHALLENGE_REQUIRED");
        resultDTO.setRedirectHtml("<html><body><form action=\"https://acs.example.test/challenge\"></form></body></html>");
        return resultDTO;
    }

    private PaymentCreateResultDTO successPaymentResult() {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resultDTO.setMerchantResponseCode("T200");
        resultDTO.setMerchantResponseMessage("Approved");
        resultDTO.setTransactionId("2607271200000000000047");
        resultDTO.setOperationId("OP2607271200000000000048");
        return resultDTO;
    }

    private PaymentCreateResultDTO processingPaymentResult() {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setMerchantResponseCode("T200");
        resultDTO.setMerchantResponseMessage("Processing");
        resultDTO.setTransactionId("2607271200000000000047");
        resultDTO.setOperationId("OP2607271200000000000048");
        return resultDTO;
    }

    private static class SequenceGlobalIdGenerator implements GlobalIdGenerator {

        /** 仅用于测试的单调递增序列，不具备分布式唯一性。 */
        private final AtomicLong sequence = new AtomicLong(2607271200000000000L);

        /** 返回下一个确定性测试编号。 */
        @Override
        public String nextId() {
            return Long.toString(sequence.incrementAndGet());
        }
    }

    private static class ImmediateTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}

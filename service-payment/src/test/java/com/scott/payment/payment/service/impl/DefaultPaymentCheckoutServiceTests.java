package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
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
import com.scott.payment.payment.service.PaymentAuthenticationRecordService;
import com.scott.payment.payment.security.PaymentCheckoutCardEnvelopeService;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
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

    /** 3DS 认证审计服务测试替身。 */
    private PaymentAuthenticationRecordService authenticationRecordService;

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
        authenticationRecordService = mock(PaymentAuthenticationRecordService.class);
        merchantNotificationMapper = mock(TransactionMerchantNotificationMapper.class);
        transactionOperations = new ImmediateTransactionOperations();
        properties = new PaymentCheckoutProperties();
        properties.setTokenPepper("unit-test-hosted-checkout-token-pepper");
        properties.setTokenKeyVersion("test-v1");
        when(threeDsService.authenticate(any(), any(), any(), anyString(), any()))
                .thenReturn(notRequiredThreeDsResult());
        when(paymentTransactionService.preparePayment(any()))
                .thenAnswer(invocation -> preparedCoreTransaction(invocation.getArgument(0)));
        when(paymentTransactionService.prepareAuthorization(any()))
                .thenAnswer(invocation -> preparedCoreTransaction(invocation.getArgument(0)));
        when(paymentTransactionService.submitPreparedTransaction(any())).thenReturn(successPaymentResult());
        when(paymentTransactionService.resumePreparedTransaction(any())).thenReturn(successPaymentResult());
        when(attemptMapper.markCorePreparedCas(anyString(), anyString(), anyString(), any(), anyString(), any(),
                anyString(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(1);
        when(sessionMapper.syncPreparedIdentityCas(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), any())).thenReturn(1);
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
                authenticationRecordService,
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
    void shouldAcceptProviderNeutralChannelCodeInCheckoutSnapshot() {
        PaymentCheckoutSessionCreateCommandDTO commandDTO = createCommand();
        commandDTO.getAllowedPaymentMethods().get(0).setChannelCode("ALTERNATE_PROVIDER");
        ArgumentCaptor<PaymentCheckoutSessionDO> sessionCaptor = ArgumentCaptor.forClass(PaymentCheckoutSessionDO.class);

        service.createSession(commandDTO);

        verify(sessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getChannelCode()).isEqualTo("ALTERNATE_PROVIDER");
        assertThat(sessionCaptor.getValue().getAllowedPaymentMethodsJson()).contains("ALTERNATE_PROVIDER");
    }

    @Test
    void shouldLeaveUnspecifiedCheckoutChannelForPaymentRouting() {
        PaymentCheckoutSessionCreateCommandDTO commandDTO = createCommand();
        commandDTO.getAllowedPaymentMethods().get(0).setChannelCode(null);
        ArgumentCaptor<PaymentCheckoutSessionDO> sessionCaptor = ArgumentCaptor.forClass(PaymentCheckoutSessionDO.class);

        service.createSession(commandDTO);

        verify(sessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getChannelCode()).isNull();
        assertThat(sessionCaptor.getValue().getAllowedPaymentMethodsJson()).doesNotContain("MPGS");
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
    void shouldConvergeTimedOutPreChannelThreeDsProcessingDuringStatusPolling() {
        PaymentCheckoutSessionDO processingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutSessionDO retryableSession = sessionWithStatus(
                PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE);
        retryableSession.setProcessStage(PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode());
        retryableSession.setVersion(2);
        PaymentCheckoutAttemptDO processingAttempt = processingAttempt(processingSession);
        processingAttempt.setThreeDsRequired(1);
        processingAttempt.setProcessStage(PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode());
        processingAttempt.setAuthenticationStartTime(null);
        processingAttempt.setSubmitTime(LocalDateTime.now().minusSeconds(601));
        PaymentCheckoutAttemptDO failedAttempt = attemptWithStatus(processingSession,
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        failedAttempt.setFailureReasonCode("THREE_DS_AUTHENTICATION_FAILED");
        failedAttempt.setPayerVisibleMessage("Payment could not be completed. Please try another card or contact your bank.");
        failedAttempt.setVersion(2);
        PaymentCheckoutPaymentStatusCommandDTO commandDTO = new PaymentCheckoutPaymentStatusCommandDTO();
        commandDTO.setTokenHash("token-hash");
        commandDTO.setCheckoutSessionId(processingSession.getCheckoutSessionId());
        commandDTO.setCheckoutAttemptId(processingAttempt.getCheckoutAttemptId());

        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(processingSession));
        when(sessionMapper.selectByCheckoutSessionId(processingSession.getCheckoutSessionId()))
                .thenReturn(processingSession, processingSession, retryableSession);
        when(attemptMapper.selectByCheckoutAttemptId(processingAttempt.getCheckoutAttemptId()))
                .thenReturn(processingAttempt, failedAttempt);
        when(attemptMapper.markThreeDsTimedOutCas(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn(1);
        when(sessionMapper.markFailedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);

        PaymentCheckoutPaymentResultDTO resultDTO = service.queryPaymentStatus(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE.getCode());
        assertThat(resultDTO.getFailure().getReasonCode()).isEqualTo("THREE_DS_AUTHENTICATION_FAILED");
        verify(sessionMapper).markFailedCas(eq(processingSession.getCheckoutSessionId()),
                eq(PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE.getCode()),
                eq(PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode()), any(), any());
        verify(paymentTransactionService).failPreparedTransaction(
                any(), eq("THREE_DS_AUTHENTICATION_TIMEOUT"), anyString());
        verify(authenticationRecordService).recordTimeout(failedAttempt);
    }

    @Test
    void shouldKeepRecentPreChannelThreeDsProcessingDuringStatusPolling() {
        PaymentCheckoutSessionDO processingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        processingSession.setLabelAmount(new BigDecimal("44.370000"));
        PaymentCheckoutAttemptDO processingAttempt = processingAttempt(processingSession);
        processingAttempt.setThreeDsRequired(1);
        processingAttempt.setProcessStage(PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode());
        processingAttempt.setAuthenticationStartTime(LocalDateTime.now().minusSeconds(599));
        PaymentCheckoutPaymentStatusCommandDTO commandDTO = paymentStatusCommand(processingSession, processingAttempt);

        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(processingSession));
        when(sessionMapper.selectByCheckoutSessionId(processingSession.getCheckoutSessionId()))
                .thenReturn(processingSession);
        when(attemptMapper.selectByCheckoutAttemptId(processingAttempt.getCheckoutAttemptId()))
                .thenReturn(processingAttempt);

        PaymentCheckoutPaymentResultDTO resultDTO = service.queryPaymentStatus(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        assertThat(resultDTO.getResult().getAmount()).isEqualByComparingTo("44.37");
        assertThat(resultDTO.getResult().getAmount().scale()).isEqualTo(2);
        verify(attemptMapper, never()).markThreeDsTimedOutCas(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any(), any());
        verify(sessionMapper, never()).markFailedCas(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldNotFailSessionWhenThreeDsTimeoutCasLosesToNewerAttemptState() {
        PaymentCheckoutSessionDO processingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO timedOutAttempt = processingAttempt(processingSession);
        timedOutAttempt.setThreeDsRequired(1);
        timedOutAttempt.setAuthenticationStartTime(LocalDateTime.now().minusSeconds(601));
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(processingSession,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL);
        submittedAttempt.setThreeDsRequired(1);
        submittedAttempt.setVersion(1);
        PaymentCheckoutPaymentStatusCommandDTO commandDTO = paymentStatusCommand(processingSession, timedOutAttempt);

        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(processingSession));
        when(sessionMapper.selectByCheckoutSessionId(processingSession.getCheckoutSessionId()))
                .thenReturn(processingSession, processingSession);
        when(attemptMapper.selectByCheckoutAttemptId(timedOutAttempt.getCheckoutAttemptId()))
                .thenReturn(timedOutAttempt, submittedAttempt);
        when(attemptMapper.markThreeDsTimedOutCas(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn(0);

        PaymentCheckoutPaymentResultDTO resultDTO = service.queryPaymentStatus(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verify(sessionMapper, never()).markFailedCas(anyString(), anyString(), anyString(), any(), any());
        verifyNoInteractions(paymentTransactionService);
        verifyNoInteractions(authenticationRecordService);
    }

    @Test
    void shouldNeverApplyThreeDsTimeoutAfterFundsWereSubmittedToChannel() {
        PaymentCheckoutSessionDO processingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutAttemptDO submittedProcessingAttempt = processingAttempt(processingSession);
        submittedProcessingAttempt.setThreeDsRequired(1);
        submittedProcessingAttempt.setAuthenticationStartTime(LocalDateTime.now().minusSeconds(601));
        submittedProcessingAttempt.setChannelSubmitTime(LocalDateTime.now().minusSeconds(590));
        PaymentCheckoutPaymentStatusCommandDTO commandDTO = paymentStatusCommand(
                processingSession, submittedProcessingAttempt);

        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(processingSession));
        when(sessionMapper.selectByCheckoutSessionId(processingSession.getCheckoutSessionId()))
                .thenReturn(processingSession);
        when(attemptMapper.selectByCheckoutAttemptId(submittedProcessingAttempt.getCheckoutAttemptId()))
                .thenReturn(submittedProcessingAttempt);

        PaymentCheckoutPaymentResultDTO resultDTO = service.queryPaymentStatus(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verify(attemptMapper, never()).markThreeDsTimedOutCas(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any(), any());
        verify(sessionMapper, never()).markFailedCas(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldQueryPayableSessionAndMarkTokenUsed() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        sessionDO.setLabelAmount(new BigDecimal("49.970000"));
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
        assertThat(resultDTO.getOrder().getAmount().scale()).isEqualTo(2);
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
    void shouldSubmitNonThreeDsPaymentUsingThePolicyRouteIdentity() {
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
        when(paymentTransactionService.submitPreparedTransaction(any())).thenReturn(coreResult);
        ArgumentCaptor<PaymentCheckoutAttemptDO> attemptCaptor = ArgumentCaptor.forClass(PaymentCheckoutAttemptDO.class);
        ArgumentCaptor<PaymentCreateCommandDTO> createCommandCaptor = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);

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
        verify(threeDsService).authenticate(any(), any(), any(), anyString(), any());
        verify(paymentTransactionService).preparePayment(createCommandCaptor.capture());
        verify(paymentTransactionService).submitPreparedTransaction(any());
        assertThat(createCommandCaptor.getValue().getRequestSource()).isEqualTo("HOSTED_CHECKOUT");
        assertThat(createCommandCaptor.getValue().getSourceUrl()).isNull();
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelCode()).isEqualTo("MPGS");
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelId()).isEqualTo(101L);
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelMidConfigId()).isEqualTo(1001L);
    }

    @Test
    void shouldFailRetryablyWhenRouteFailsBeforeChannelSubmission() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO retryableSessionDO = sessionWithStatus(
                PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE);
        PaymentCheckoutAttemptDO failedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        failedAttempt.setFailureReasonCode("ROUTE_FAILED");
        failedAttempt.setPayerVisibleMessage("Payment could not be completed. Please try another card or contact your bank.");
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(sessionDO));
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, payingSessionDO, retryableSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(failedAttempt);
        when(sessionMapper.markFailedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(threeDsService.authenticate(any(), any(), any(), anyString(), any()))
                .thenThrow(new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "no channel route candidate"));

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE.getCode());
        assertThat(resultDTO.getFailure().getReasonCode()).isEqualTo("ROUTE_FAILED");
        verify(attemptMapper).markResultCas(anyString(), eq("FAILED"), eq("RESULT_RENDERED"),
                isNull(), isNull(), isNull(), eq("ROUTE_FAILED"), isNull(),
                eq("Payment could not be completed. Please try another card or contact your bank."),
                anyString(), eq(0), any());
        verify(sessionMapper).markFailedCas(anyString(), eq("PAYABLE_FAILED_RETRYABLE"),
                eq("RESULT_RENDERED"), eq(0), any());
        verify(paymentTransactionService).preparePayment(any());
        verify(paymentTransactionService).failPreparedTransaction(any(), eq("ROUTE_FAILED"), anyString());
    }

    @Test
    void shouldNotReopenPaymentWhenCoreFailsAfterChannelSubmissionWasMarked() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(sessionDO));
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(submittedAttempt);
        when(paymentTransactionService.submitPreparedTransaction(any()))
                .thenThrow(new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "unknown funding result"));

        assertThatThrownBy(() -> service.submitPayment(submitCommand()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("unknown funding result");

        verify(attemptMapper).markChannelSubmittedCas(anyString(), eq("CHANNEL_SUBMITTED"),
                eq("SUBMIT_CHANNEL"), eq(0), any());
        verify(attemptMapper, never()).markResultCas(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(sessionMapper, never()).markFailedCas(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldReturnThreeDsHtmlWithoutSubmittingFundsBeforeAuthenticationPasses() {
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
        PaymentCheckoutAttemptDO initiatedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_INITIATED,
                PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER);
        PaymentCheckoutAttemptDO requiredAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(attemptMapper.markThreeDsRequiredCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(initiatedAttempt, requiredAttempt);
        when(sessionMapper.markAuthenticatingCas(anyString(), anyString(), any(), any())).thenReturn(1);
        when(threeDsService.authenticate(any(), any(), any(), anyString(), any())).thenReturn(challengeThreeDsResult());

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        assertThat(resultDTO.getThreeDsAction().getPhase()).isEqualTo(ChannelThreeDsPhase.AUTHENTICATE.name());
        assertThat(resultDTO.getThreeDsAction().getHtml()).contains("acs.example.test/challenge");
        verify(threeDsService).authenticate(any(), any(), any(), anyString(), any());
        verify(paymentTransactionService).preparePayment(any());
        verify(paymentTransactionService, never()).submitPreparedTransaction(any());
        verify(paymentTransactionService, never()).resumePreparedTransaction(any());
    }

    @Test
    void shouldSubmitFundsOnlyAfterServerConfirmedThreeDsPass() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO succeededSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO passedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(activeToken(sessionDO));
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, payingSessionDO, succeededSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString()))
                .thenReturn(passedAttempt, submittedAttempt, succeededAttempt);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(threeDsService.authenticate(any(), any(), any(), anyString(), any())).thenReturn(passedThreeDsResult());
        ArgumentCaptor<PaymentCreateCommandDTO> commandCaptor = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        ArgumentCaptor<PaymentInitialPreparationResultDTO> preparationCaptor =
                ArgumentCaptor.forClass(PaymentInitialPreparationResultDTO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.SUCCEEDED.getCode());
        verify(paymentTransactionService).preparePayment(commandCaptor.capture());
        verify(paymentTransactionService).submitPreparedTransaction(preparationCaptor.capture());
        assertThat(commandCaptor.getValue().getThreeDsRequired()).isTrue();
        assertThat(commandCaptor.getValue().getThreeDsInfo().getAuthenticationStatus()).isEqualTo("PASSED");
        assertThat(commandCaptor.getValue().getThreeDsInfo().getAuthenticationTransactionId())
                .isEqualTo("3DS2607271200000000000047");
        assertThat(commandCaptor.getValue().getChannelIdentity().getChannelOrderNo())
                .isEqualTo("2607271200000000000047");
        assertThat(commandCaptor.getValue().getChannelIdentity().getChannelTransactionId())
                .isEqualTo("FUNDS-TX-001");
        assertThat(preparationCaptor.getValue().getCommandDTO().getChannelIdentity().getChannelOrderNo())
                .isEqualTo("2607271200000000000047");
        assertThat(preparationCaptor.getValue().getCommandDTO().getChannelIdentity().getChannelTransactionId())
                .isEqualTo("FUNDS-TX-001");
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
        when(paymentTransactionService.submitPreparedTransaction(any())).thenReturn(processingPaymentResult());
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
        when(paymentTransactionService.submitPreparedTransaction(any())).thenReturn(processingPaymentResult());
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
    void shouldFailTimedOutThreeDsReturnWithoutCallingChannelOrPaymentCore() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutSessionDO retryableSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE);
        PaymentCheckoutAttemptDO challengeAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        challengeAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        challengeAttempt.setAuthenticationStartTime(LocalDateTime.now().minusSeconds(601));
        PaymentCheckoutAttemptDO returnedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL);
        returnedAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        returnedAttempt.setAuthenticationStartTime(challengeAttempt.getAuthenticationStartTime());
        returnedAttempt.setVersion(1);
        PaymentCheckoutAttemptDO authenticationFailedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        authenticationFailedAttempt.setChannelRequestId(null);
        authenticationFailedAttempt.setVersion(2);
        PaymentCheckoutAttemptDO failedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        failedAttempt.setFailureReasonCode("THREE_DS_AUTHENTICATION_FAILED");
        failedAttempt.setVersion(3);

        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(challengeAttempt, returnedAttempt, authenticationFailedAttempt, failedAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession, authenticatingSession, retryableSession);
        when(attemptMapper.markThreeDsReturnedCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(sessionMapper.markFailedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE.getCode());
        assertThat(resultDTO.getFailure().getReasonCode()).isEqualTo("THREE_DS_AUTHENTICATION_FAILED");
        verifyNoInteractions(threeDsService);
        verify(paymentTransactionService).markThreeDsIndicator(anyString(), any(), anyString());
        ArgumentCaptor<PaymentCreateCommandDTO> failureCommandCaptor =
                ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);
        verify(paymentTransactionService).failPreparedTransaction(
                failureCommandCaptor.capture(), eq("THREE_DS_AUTHENTICATION_FAILED"), anyString());
        assertThat(failureCommandCaptor.getValue().getTransactionId())
                .isEqualTo(authenticationFailedAttempt.getTransactionId());
        assertThat(failureCommandCaptor.getValue().getTransactionDateTime())
                .isEqualTo(authenticationFailedAttempt.getTransactionDateTime());
        verify(attemptMapper).markAuthenticationResultCas(anyString(), eq("THREE_DS_FAILED"), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
        attemptDO.setThreeDsStatus("CHALLENGE_REQUIRED");
        attemptDO.setAttemptRequestId("ATTEMPT-001");
        attemptDO.setOperationId("OP-001");
        attemptDO.setChannelCode("MPGS");
        attemptDO.setChannelMidConfigId(1001L);
        PaymentCheckoutAttemptDO returnedAttempt = processingAttempt(sessionDO);
        returnedAttempt.setAttemptStatus(PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED.getCode());
        returnedAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        returnedAttempt.setAttemptRequestId("ATTEMPT-001");
        returnedAttempt.setOperationId("OP-001");
        returnedAttempt.setChannelCode("MPGS");
        returnedAttempt.setChannelMidConfigId(1001L);
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId())).thenReturn(attemptDO, returnedAttempt);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId())).thenReturn(sessionDO, processingSessionDO);
        when(attemptMapper.markThreeDsReturnedCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        PaymentCheckoutThreeDsResultDTO processingThreeDs = new PaymentCheckoutThreeDsResultDTO();
        processingThreeDs.setStatus("PROCESSING");
        processingThreeDs.setChannelCode("MPGS");
        processingThreeDs.setChannelMidConfigId(1001L);
        when(threeDsService.continueAuthentication(any(), any(), any(), anyString(), any())).thenReturn(processingThreeDs);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verify(sessionMapper, never()).markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        verify(paymentTransactionService).markThreeDsIndicator(anyString(), any(), anyString());
        verify(paymentTransactionService, never()).resumePreparedTransaction(any());
    }

    @Test
    void shouldAuthenticatePayerOnlyAfterThreeDsMethodReturn() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        commandDTO.setBillingCardHolderInfo(submitCommand().getBillingCardHolderInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutAttemptDO methodAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        methodAttempt.setThreeDsStatus("METHOD_REQUIRED");
        methodAttempt.setAttemptRequestId("ATTEMPT-001");
        methodAttempt.setOperationId("OP-001");
        methodAttempt.setChannelCode("MPGS");
        methodAttempt.setChannelMidConfigId(1001L);
        PaymentCheckoutAttemptDO returnedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER);
        returnedAttempt.setThreeDsStatus("METHOD_REQUIRED");
        returnedAttempt.setAttemptRequestId("ATTEMPT-001");
        returnedAttempt.setOperationId("OP-001");
        returnedAttempt.setChannelCode("MPGS");
        returnedAttempt.setChannelMidConfigId(1001L);
        PaymentCheckoutAttemptDO challengeAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        challengeAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");

        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(methodAttempt, returnedAttempt, challengeAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession, authenticatingSession);
        when(attemptMapper.markThreeDsReturnedCas(anyString(), eq("AUTHENTICATE_PAYER"), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(attemptMapper.markThreeDsRequiredCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(sessionMapper.markAuthenticatingCas(anyString(), anyString(), any(), any())).thenReturn(1);
        when(threeDsService.continueAuthentication(any(), any(), any(), anyString(), eq(ChannelThreeDsPhase.AUTHENTICATE)))
                .thenReturn(challengeThreeDsResult());

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        assertThat(resultDTO.getThreeDsAction().getPhase()).isEqualTo(ChannelThreeDsPhase.AUTHENTICATE.name());
        verify(threeDsService).continueAuthentication(any(), any(), any(), anyString(), eq(ChannelThreeDsPhase.AUTHENTICATE));
        verify(paymentTransactionService, never()).resumePreparedTransaction(any());
    }

    @Test
    void shouldVerifyChallengeAndSubmitFundsOnlyAfterServerConfirmedPass() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        commandDTO.setBillingCardHolderInfo(submitCommand().getBillingCardHolderInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutSessionDO payingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO succeededSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO challengeAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        challengeAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        challengeAttempt.setAttemptRequestId("ATTEMPT-001");
        challengeAttempt.setOperationId("OP-001");
        challengeAttempt.setChannelCode("MPGS");
        challengeAttempt.setChannelMidConfigId(1001L);
        PaymentCheckoutAttemptDO returnedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL);
        returnedAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        returnedAttempt.setAttemptRequestId("ATTEMPT-001");
        returnedAttempt.setOperationId("OP-001");
        returnedAttempt.setChannelCode("MPGS");
        returnedAttempt.setChannelMidConfigId(1001L);
        PaymentCheckoutAttemptDO passedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);

        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(challengeAttempt, returnedAttempt, passedAttempt, submittedAttempt, succeededAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession, payingSession, succeededSession);
        when(attemptMapper.markThreeDsReturnedCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(threeDsService.continueAuthentication(any(), any(), any(), anyString(), eq(ChannelThreeDsPhase.VERIFY)))
                .thenReturn(passedThreeDsResult());
        when(paymentTransactionService.resumePreparedTransaction(any())).thenReturn(successPaymentResult());
        ArgumentCaptor<PaymentCreateCommandDTO> createCommandCaptor = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.SUCCEEDED.getCode());
        verify(threeDsService).continueAuthentication(any(), any(), any(), anyString(), eq(ChannelThreeDsPhase.VERIFY));
        verify(paymentTransactionService).resumePreparedTransaction(createCommandCaptor.capture());
        assertThat(createCommandCaptor.getValue().getThreeDsRequired()).isTrue();
        assertThat(createCommandCaptor.getValue().getThreeDsInfo().getAuthenticationStatus()).isEqualTo("PASSED");
        assertThat(createCommandCaptor.getValue().getThreeDsInfo().getAuthenticationTransactionId())
                .isEqualTo("3DS2607271200000000000047");
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelCode()).isEqualTo("MPGS");
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelMidConfigId()).isEqualTo(1001L);
        assertThat(commandDTO.getCardInfo()).isNull();
    }

    @Test
    void shouldNotRepeatVerificationWhenThreeDsReturnWasAlreadyAccepted() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        commandDTO.setBillingCardHolderInfo(submitCommand().getBillingCardHolderInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutAttemptDO returnedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL);
        returnedAttempt.setThreeDsStatus("CHALLENGE_REQUIRED");
        returnedAttempt.setAttemptRequestId("ATTEMPT-001");
        returnedAttempt.setOperationId("OP-001");
        returnedAttempt.setChannelCode("MPGS");
        returnedAttempt.setChannelMidConfigId(1001L);
        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(returnedAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verify(attemptMapper, never()).markThreeDsReturnedCas(anyString(), anyString(), any(), any());
        verifyNoInteractions(threeDsService);
        verify(paymentTransactionService, never()).resumePreparedTransaction(any());
    }

    @Test
    void shouldNotRepeatAuthenticatePayerWhenMethodReturnWasAlreadyAccepted() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutAttemptDO returnedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER);
        returnedAttempt.setThreeDsStatus("METHOD_REQUIRED");
        returnedAttempt.setAttemptRequestId("ATTEMPT-001");
        returnedAttempt.setOperationId("OP-001");
        returnedAttempt.setChannelCode("MPGS");
        returnedAttempt.setChannelMidConfigId(1001L);
        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId())).thenReturn(returnedAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
        verifyNoInteractions(threeDsService);
        verify(paymentTransactionService, never()).resumePreparedTransaction(any());
    }

    @Test
    void shouldResumePaymentWhenThreeDsPassWasAlreadyPersisted() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        commandDTO.setBillingCardHolderInfo(submitCommand().getBillingCardHolderInfo());

        PaymentCheckoutSessionDO authenticatingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutSessionDO payingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO succeededSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO passedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        passedAttempt.setThreeDsStatus("AUTHENTICATION_SUCCESSFUL");
        passedAttempt.setThreeDsTransactionId("3DS2607271200000000000047");
        passedAttempt.setThreeDsVersion("3DS2");
        passedAttempt.setDsTransactionId("ds-tx-001");
        passedAttempt.setEci("05");
        passedAttempt.setAttemptRequestId("ATTEMPT-001");
        passedAttempt.setOperationId("OP-001");
        passedAttempt.setChannelCode("MPGS");
        passedAttempt.setChannelMidConfigId(1001L);
        passedAttempt.setChannelOrderNo("2607271200000000000047");
        passedAttempt.setChannelTransactionId("3DS2607271200000000000047");
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(authenticatingSession,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);

        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(passedAttempt, submittedAttempt, succeededAttempt);
        when(sessionMapper.selectByCheckoutSessionId(authenticatingSession.getCheckoutSessionId()))
                .thenReturn(authenticatingSession, payingSession, succeededSession);
        when(attemptMapper.markChannelSubmittedCas(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(paymentTransactionService.resumePreparedTransaction(any())).thenReturn(successPaymentResult());
        ArgumentCaptor<PaymentCreateCommandDTO> createCommandCaptor = ArgumentCaptor.forClass(PaymentCreateCommandDTO.class);

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.SUCCEEDED.getCode());
        verifyNoInteractions(threeDsService);
        verify(paymentTransactionService).resumePreparedTransaction(createCommandCaptor.capture());
        assertThat(createCommandCaptor.getValue().getThreeDsInfo().getAuthenticationStatus()).isEqualTo("PASSED");
        assertThat(createCommandCaptor.getValue().getThreeDsInfo().getAuthenticationTransactionId())
                .isEqualTo("3DS2607271200000000000047");
        assertThat(createCommandCaptor.getValue().getChannelIdentity().getChannelMidConfigId()).isEqualTo(1001L);
    }

    @Test
    void shouldResumeIdempotentCorePaymentWhenChannelSubmissionWasAlreadyMarked() {
        PaymentCheckoutThreeDsReturnCommandDTO commandDTO = new PaymentCheckoutThreeDsReturnCommandDTO();
        commandDTO.setCheckoutSessionId("2607271200000000000010");
        commandDTO.setCheckoutAttemptId("2607271200000000000038");
        commandDTO.setThreeDsReturnTokenHash("return-token-hash");
        commandDTO.setCardInfo(submitCommand().getCardInfo());
        commandDTO.setBillingCardHolderInfo(submitCommand().getBillingCardHolderInfo());

        PaymentCheckoutSessionDO payingSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO succeededSession = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO submittedAttempt = attemptWithStatus(payingSession,
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        submittedAttempt.setThreeDsStatus("AUTHENTICATION_SUCCESSFUL");
        submittedAttempt.setThreeDsTransactionId("3DS2607271200000000000047");
        submittedAttempt.setThreeDsVersion("3DS2");
        submittedAttempt.setDsTransactionId("ds-tx-001");
        submittedAttempt.setEci("05");
        submittedAttempt.setAttemptRequestId("ATTEMPT-001");
        submittedAttempt.setOperationId("OP-001");
        submittedAttempt.setChannelCode("MPGS");
        submittedAttempt.setChannelMidConfigId(1001L);
        submittedAttempt.setChannelOrderNo("2607271200000000000047");
        submittedAttempt.setChannelTransactionId("3DS2607271200000000000047");
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(payingSession,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);

        when(attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId()))
                .thenReturn(submittedAttempt, succeededAttempt);
        when(sessionMapper.selectByCheckoutSessionId(payingSession.getCheckoutSessionId()))
                .thenReturn(payingSession, payingSession, succeededSession);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(paymentTransactionService.resumePreparedTransaction(any())).thenReturn(successPaymentResult());

        PaymentCheckoutPaymentResultDTO resultDTO = service.handleThreeDsReturn(commandDTO);

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.SUCCEEDED.getCode());
        verifyNoInteractions(threeDsService);
        verify(attemptMapper, never()).markChannelSubmittedCas(anyString(), anyString(), anyString(), any(), any());
        verify(paymentTransactionService).resumePreparedTransaction(any());
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

    private PaymentCheckoutPaymentStatusCommandDTO paymentStatusCommand(PaymentCheckoutSessionDO sessionDO,
                                                                        PaymentCheckoutAttemptDO attemptDO) {
        PaymentCheckoutPaymentStatusCommandDTO commandDTO = new PaymentCheckoutPaymentStatusCommandDTO();
        commandDTO.setTokenHash("token-hash");
        commandDTO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        commandDTO.setCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
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
        resultDTO.setChannelTransactionId("3DS2607271200000000000047");
        resultDTO.setThreeDsStatus("AUTHENTICATION_SUCCESSFUL");
        resultDTO.setThreeDsVersion("3DS2");
        resultDTO.setDsTransactionId("ds-tx-001");
        resultDTO.setEci("05");
        resultDTO.setCavv("masked-token");
        resultDTO.setChannelCode("MPGS");
        resultDTO.setChannelId(101L);
        resultDTO.setChannelMidConfigId(1001L);
        return resultDTO;
    }

    private PaymentCheckoutThreeDsResultDTO notRequiredThreeDsResult() {
        PaymentCheckoutThreeDsResultDTO resultDTO = new PaymentCheckoutThreeDsResultDTO();
        resultDTO.setStatus("NOT_REQUIRED");
        resultDTO.setChannelCode("MPGS");
        resultDTO.setChannelId(101L);
        resultDTO.setChannelMidConfigId(1001L);
        resultDTO.setThreeDsPolicyAction("NONE");
        return resultDTO;
    }

    private PaymentCheckoutThreeDsResultDTO challengeThreeDsResult() {
        PaymentCheckoutThreeDsResultDTO resultDTO = passedThreeDsResult();
        resultDTO.setPhase(ChannelThreeDsPhase.AUTHENTICATE.name());
        resultDTO.setStatus("CHALLENGE_REQUIRED");
        resultDTO.setRedirectHtml("<html><body><form action=\"https://acs.example.test/challenge\"></form></body></html>");
        return resultDTO;
    }

    /** 构造已经提交本地事务、尚未请求 PSP 的核心交易准备结果。 */
    private PaymentInitialPreparationResultDTO preparedCoreTransaction(PaymentCreateCommandDTO commandDTO) {
        commandDTO.setTransactionAmount(commandDTO.getAmount());
        commandDTO.setTransactionCurrency(commandDTO.getCurrency());
        PaymentCreateResultDTO resultDTO = processingPaymentResult();
        resultDTO.setProcessStage("CHANNEL_REQUESTING");
        resultDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
        routeResultDTO.setChannelId(101L);
        routeResultDTO.setMidConfigId(1001L);
        routeResultDTO.setRequestedCurrency(commandDTO.getCurrency());
        routeResultDTO.setRoutedCurrency(commandDTO.getCurrency());
        routeResultDTO.setThreeDsSupported(true);
        PaymentPreparedChannelRequestDTO preparedRequestDTO = new PaymentPreparedChannelRequestDTO();
        preparedRequestDTO.setRequestId("CR-" + resultDTO.getTransactionId());
        preparedRequestDTO.setChannelOrderNo(resultDTO.getTransactionId());
        preparedRequestDTO.setChannelTransactionId("FUNDS-TX-001");
        PaymentInitialPreparationResultDTO preparation = new PaymentInitialPreparationResultDTO();
        preparation.setCallChannel(true);
        preparation.setCommandDTO(commandDTO);
        preparation.setRouteResultDTO(routeResultDTO);
        preparation.setPreparedChannelRequestDTO(preparedRequestDTO);
        preparation.setResultDTO(resultDTO);
        preparation.setIdempotencyKey("CHECKOUT:" + commandDTO.getMerchantOrderId());
        preparation.setCurrencyExponent(2);
        return preparation;
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

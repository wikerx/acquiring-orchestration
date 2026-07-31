package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
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
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.support.PaymentCheckoutTokenSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

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
    void shouldSubmitFrictionlessPaymentWithMaskedCardAndCorePayment() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO processingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PROCESSING);
        PaymentCheckoutSessionDO succeededSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.SUCCEEDED);
        PaymentCheckoutAttemptDO threeDsPassedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL);
        PaymentCheckoutAttemptDO succeededAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED);
        PaymentCheckoutThreeDsResultDTO threeDsResult = passedThreeDsResult();
        PaymentCreateResultDTO coreResult = successPaymentResult();
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, processingSessionDO, succeededSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), isNull(), any(), any(),
                isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markResultCas(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(threeDsPassedAttempt, succeededAttempt);
        when(sessionMapper.markProcessingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(sessionMapper.markSucceededCas(anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(0), any()))
                .thenReturn(1);
        when(threeDsService.authenticate(any(), any(), any(), anyString())).thenReturn(threeDsResult);
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
        verify(paymentTransactionService).createPayment(any());
    }

    @Test
    void shouldReturnRealThreeDsHtmlWhenChallengeRequired() {
        PaymentCheckoutSessionDO sessionDO = payableSession();
        PaymentCheckoutSessionDO payingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.PAYING);
        PaymentCheckoutSessionDO authenticatingSessionDO = sessionWithStatus(PaymentCheckoutSessionStatusEnum.AUTHENTICATING);
        PaymentCheckoutAttemptDO initiatedAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_INITIATED,
                PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER);
        PaymentCheckoutAttemptDO challengeAttempt = attemptWithStatus(sessionDO,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS);
        PaymentCheckoutThreeDsResultDTO challengeResult = challengeThreeDsResult();
        PaymentCheckoutTokenDO tokenDO = activeToken(sessionDO);
        when(tokenMapper.selectByTokenHash("token-hash")).thenReturn(tokenDO);
        when(sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId()))
                .thenReturn(sessionDO, payingSessionDO, payingSessionDO, authenticatingSessionDO);
        when(attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId())).thenReturn(0);
        when(sessionMapper.markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markAuthenticationResultCas(anyString(), anyString(), anyString(), isNull(), any(), any(),
                isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull(), eq(0), any()))
                .thenReturn(1);
        when(attemptMapper.markThreeDsRequiredCas(anyString(), anyString(), anyString(), any(), any(), any(), eq(0), any()))
                .thenReturn(1);
        when(sessionMapper.markAuthenticatingCas(anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(attemptMapper.selectByCheckoutAttemptId(anyString())).thenReturn(initiatedAttempt, challengeAttempt);
        when(threeDsService.authenticate(any(), any(), any(), anyString())).thenReturn(challengeResult);

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getPageState()).isEqualTo(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        assertThat(resultDTO.getThreeDsAction()).isNotNull();
        assertThat(resultDTO.getThreeDsAction().getHtml()).contains("acs.example.test");
        assertThat(resultDTO.getThreeDsAction().getHtml()).doesNotContain("3DS integration pending");
        assertThat(resultDTO.getThreeDsAction().getReturnUrl()).startsWith("https://pay.example.com/checkout/api/v1/3ds/bridge");
        verify(paymentTransactionService, never()).createPayment(any());
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

        PaymentCheckoutPaymentResultDTO resultDTO = service.submitPayment(submitCommand());

        assertThat(resultDTO.getCheckoutAttemptId()).isEqualTo(attemptDO.getCheckoutAttemptId());
        verify(attemptMapper, never()).insert(any(PaymentCheckoutAttemptDO.class));
        verify(sessionMapper, never()).markSubmittedCas(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any(), any());
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

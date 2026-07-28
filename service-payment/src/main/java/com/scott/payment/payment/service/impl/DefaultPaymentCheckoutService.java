package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.domain.state.PaymentCheckoutAttemptStatusEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutEventResultEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutPageStateEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutSecurityDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutSessionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutTokenStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutEventDO;
import com.scott.payment.payment.entity.PaymentCheckoutSecurityEventDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.entity.PaymentCheckoutTokenDO;
import com.scott.payment.payment.mapper.PaymentCheckoutAttemptMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSecurityEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutTokenMapper;
import com.scott.payment.payment.service.PaymentCheckoutService;
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.support.PaymentCheckoutTokenSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Hosted Checkout 默认内部服务。
 */
@Service
public class DefaultPaymentCheckoutService implements PaymentCheckoutService {

    private static final String CHANNEL_MPGS = "MPGS";
    private static final String PAYMENT_METHOD_BANK_CARD = "BANK_CARD";
    private static final String EVENT_SESSION_CREATED = "SESSION_CREATED";
    private static final String EVENT_SESSION_REISSUED = "SESSION_TOKEN_REISSUED";
    private static final String EVENT_SESSION_OPENED = "SESSION_OPENED";
    private static final String EVENT_PAYMENT_SUBMITTED = "PAYMENT_SUBMITTED";
    private static final String EVENT_THREE_DS_CHALLENGE_REQUIRED = "THREE_DS_CHALLENGE_REQUIRED";
    private static final String EVENT_THREE_DS_AUTHENTICATED = "THREE_DS_AUTHENTICATED";
    private static final String EVENT_THREE_DS_FAILED = "THREE_DS_FAILED";
    private static final String EVENT_PAYMENT_STATUS_QUERIED = "PAYMENT_STATUS_QUERIED";
    private static final String EVENT_THREE_DS_RETURNED = "THREE_DS_RETURNED";
    private static final String SECURITY_INVALID_TOKEN = "INVALID_TOKEN";
    private static final String SECURITY_EXPIRED_TOKEN = "EXPIRED_TOKEN";
    private static final String SECURITY_SESSION_MISMATCH = "SESSION_MISMATCH";
    private static final String THREE_DS_ACTION_HTML = "HTML";
    private static final String FAILURE_THREE_DS_AUTHENTICATION_FAILED = "THREE_DS_AUTHENTICATION_FAILED";
    private static final String FAILURE_CHANNEL_PROCESSING = "CHANNEL_PROCESSING";
    private static final String FAILURE_PAYMENT_DECLINED = "PAYMENT_DECLINED";
    private static final String DEFAULT_PAYER_FAILURE_MESSAGE = "Payment could not be completed. Please try another card or contact your bank.";
    private static final int THREE_DS_RETURN_TOKEN_BYTES = 32;
    private static final int THREE_DS_TIMEOUT_SECONDS = 600;

    private final PaymentCheckoutSessionMapper sessionMapper;
    private final PaymentCheckoutTokenMapper tokenMapper;
    private final PaymentCheckoutAttemptMapper attemptMapper;
    private final PaymentCheckoutEventMapper eventMapper;
    private final PaymentCheckoutSecurityEventMapper securityEventMapper;
    private final GlobalIdGenerator globalIdGenerator;
    private final PaymentCheckoutProperties properties;
    private final PaymentCheckoutThreeDsService threeDsService;
    private final PaymentTransactionService paymentTransactionService;
    private final TransactionOperations transactionOperations;

    @Autowired
    public DefaultPaymentCheckoutService(PaymentCheckoutSessionMapper sessionMapper,
                                         PaymentCheckoutTokenMapper tokenMapper,
                                         PaymentCheckoutAttemptMapper attemptMapper,
                                         PaymentCheckoutEventMapper eventMapper,
                                         PaymentCheckoutSecurityEventMapper securityEventMapper,
                                         GlobalIdGenerator globalIdGenerator,
                                         PaymentCheckoutProperties properties,
                                         PaymentCheckoutThreeDsService threeDsService,
                                         PaymentTransactionService paymentTransactionService,
                                         PlatformTransactionManager transactionManager) {
        this.sessionMapper = sessionMapper;
        this.tokenMapper = tokenMapper;
        this.attemptMapper = attemptMapper;
        this.eventMapper = eventMapper;
        this.securityEventMapper = securityEventMapper;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
        this.threeDsService = threeDsService;
        this.paymentTransactionService = paymentTransactionService;
        this.transactionOperations = new TransactionTemplate(transactionManager);
    }

    DefaultPaymentCheckoutService(PaymentCheckoutSessionMapper sessionMapper,
                                  PaymentCheckoutTokenMapper tokenMapper,
                                  PaymentCheckoutAttemptMapper attemptMapper,
                                  PaymentCheckoutEventMapper eventMapper,
                                  PaymentCheckoutSecurityEventMapper securityEventMapper,
                                  GlobalIdGenerator globalIdGenerator,
                                  PaymentCheckoutProperties properties,
                                  PaymentCheckoutThreeDsService threeDsService,
                                  PaymentTransactionService paymentTransactionService,
                                  TransactionOperations transactionOperations) {
        this.sessionMapper = sessionMapper;
        this.tokenMapper = tokenMapper;
        this.attemptMapper = attemptMapper;
        this.eventMapper = eventMapper;
        this.securityEventMapper = securityEventMapper;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
        this.threeDsService = threeDsService;
        this.paymentTransactionService = paymentTransactionService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCheckoutSessionCreateResultDTO createSession(PaymentCheckoutSessionCreateCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutSessionDO existed = sessionMapper.selectByMerchantRequest(
                commandDTO.getMerchantId(), commandDTO.getMerchantRequestId());
        if (existed != null) {
            validateIdempotentFingerprint(existed, commandDTO);
            IssuedCheckoutToken issuedToken = createToken(existed.getCheckoutSessionId(), existed.getMerchantId(),
                    existed.getExpireTime(), "IDEMPOTENT_REISSUE", now);
            tokenMapper.insert(issuedToken.tokenDO());
            insertEvent(event(existed, null, EVENT_SESSION_REISSUED, PaymentCheckoutProcessStageEnum.SESSION_CREATED,
                    PaymentCheckoutEventResultEnum.SUCCESS, commandDTO.getTraceId(), null, now));
            return createResult(existed, issuedToken, true);
        }

        PaymentCheckoutSessionDO sessionDO = buildSession(commandDTO, now);
        sessionMapper.insert(sessionDO);
        IssuedCheckoutToken issuedToken = createToken(sessionDO.getCheckoutSessionId(), sessionDO.getMerchantId(),
                sessionDO.getExpireTime(), "SESSION_CREATE", now);
        tokenMapper.insert(issuedToken.tokenDO());
        insertEvent(event(sessionDO, null, EVENT_SESSION_CREATED, PaymentCheckoutProcessStageEnum.SESSION_CREATED,
                PaymentCheckoutEventResultEnum.SUCCESS, commandDTO.getTraceId(), null, now));
        return createResult(sessionDO, issuedToken, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCheckoutSessionQueryResultDTO querySession(PaymentCheckoutSessionQueryCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutTokenDO tokenDO = tokenMapper.selectByTokenHash(commandDTO.getTokenHash());
        if (!isUsableToken(tokenDO, now)) {
            recordSecurityEvent(commandDTO.getTokenHash(), null, null, SECURITY_INVALID_TOKEN,
                    PaymentCheckoutSecurityDecisionEnum.BLOCK, commandDTO.getTraceId(),
                    commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), commandDTO.getOriginHash(),
                    commandDTO.getRefererHash(), commandDTO.getDeviceIdHash(), now);
            return blockedSessionResult();
        }
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(tokenDO.getCheckoutSessionId());
        if (sessionDO == null) {
            recordSecurityEvent(commandDTO.getTokenHash(), null, null, SECURITY_SESSION_MISMATCH,
                    PaymentCheckoutSecurityDecisionEnum.BLOCK, commandDTO.getTraceId(),
                    commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), commandDTO.getOriginHash(),
                    commandDTO.getRefererHash(), commandDTO.getDeviceIdHash(), now);
            return blockedSessionResult();
        }
        if (isExpired(sessionDO, tokenDO, now)) {
            recordSecurityEvent(commandDTO.getTokenHash(), sessionDO.getCheckoutSessionId(), null, SECURITY_EXPIRED_TOKEN,
                    PaymentCheckoutSecurityDecisionEnum.BLOCK, commandDTO.getTraceId(),
                    commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), commandDTO.getOriginHash(),
                    commandDTO.getRefererHash(), commandDTO.getDeviceIdHash(), now);
            return expiredSessionResult(sessionDO);
        }
        tokenMapper.markUsed(commandDTO.getTokenHash(), commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), now);
        sessionMapper.markOpened(sessionDO.getCheckoutSessionId(), now);
        insertEvent(event(sessionDO, null, EVENT_SESSION_OPENED, PaymentCheckoutProcessStageEnum.WAITING_PAYER,
                PaymentCheckoutEventResultEnum.SUCCESS, commandDTO.getTraceId(), null, now));
        return sessionResult(sessionDO);
    }

    /**
     * 提交一次收银台付款尝试，事务内先锁定会话和尝试号，事务外再调用 3DS/支付核心。
     *
     * @param commandDTO 付款人提交的卡信息、账单信息和 attemptRequestId
     * @return 收银台页面可直接渲染的下一步状态
     */
    @Override
    public PaymentCheckoutPaymentResultDTO submitPayment(PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        PaymentSubmissionContext context = transactionOperations.execute(status -> createPaymentSubmission(commandDTO));
        if (context == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout payment submission failed");
        }
        if (context.duplicate()) {
            return paymentResult(context.sessionDO(), context.attemptDO());
        }

        PaymentCheckoutThreeDsResultDTO threeDsResult = threeDsService.authenticate(
                context.sessionDO(), context.attemptDO(), commandDTO, context.threeDsReturnUrl());
        threeDsResult = safeThreeDsResult(threeDsResult);
        if (threeDsResult.challengeRequired()) {
            return applyThreeDsChallengeRequired(context, threeDsResult);
        }
        if (threeDsResult.failed()) {
            return applyThreeDsFailed(context, threeDsResult);
        }
        if (threeDsResult.processing()) {
            return applyThreeDsProcessing(context, threeDsResult);
        }
        PaymentCheckoutAttemptDO authenticatedAttempt = applyThreeDsPassed(context, threeDsResult);
        PaymentCreateResultDTO paymentResultDTO = createCorePayment(context.sessionDO(), authenticatedAttempt, commandDTO, threeDsResult);
        return applyPaymentCoreResult(context.sessionDO(), authenticatedAttempt, paymentResultDTO);
    }

    /**
     * 查询最新付款状态；该接口支撑前端处理中页面轮询，不推动资金状态流转。
     *
     * @param commandDTO token 摘要、会话号和可选尝试号
     * @return 当前尝试对应的结果页视图
     */
    @Override
    public PaymentCheckoutPaymentResultDTO queryPaymentStatus(PaymentCheckoutPaymentStatusCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutTokenDO tokenDO = validateTokenAndSession(commandDTO.getTokenHash(), commandDTO.getCheckoutSessionId(), now,
                commandDTO.getTraceId(), commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), null, null, null);
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(tokenDO.getCheckoutSessionId());
        PaymentCheckoutAttemptDO attemptDO = resolveAttempt(sessionDO, commandDTO.getCheckoutAttemptId());
        insertEvent(event(sessionDO, attemptDO, EVENT_PAYMENT_STATUS_QUERIED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.SUCCESS,
                commandDTO.getTraceId(), null, now));
        return paymentResult(sessionDO, attemptDO);
    }

    /**
     * 处理 3DS bridge 回跳，只把尝试从 WAITING_3DS 推进到等待渠道结果。
     *
     * @param commandDTO 已由 OpenAPI 层转换为摘要的 3DS return 命令
     * @return 回跳后的处理中、拦截或既有终态结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCheckoutPaymentResultDTO handleThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO attemptDO = attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId());
        if (attemptDO == null
                || !Objects.equals(commandDTO.getCheckoutSessionId(), attemptDO.getCheckoutSessionId())
                || !Objects.equals(commandDTO.getThreeDsReturnTokenHash(), attemptDO.getThreeDsReturnTokenHash())) {
            recordSecurityEvent(null, commandDTO.getCheckoutSessionId(), commandDTO.getCheckoutAttemptId(),
                    SECURITY_SESSION_MISMATCH, PaymentCheckoutSecurityDecisionEnum.BLOCK, commandDTO.getTraceId(),
                    commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), null, null, null, now);
            return blockedPaymentResult(commandDTO.getCheckoutSessionId(), commandDTO.getCheckoutAttemptId());
        }
        if (!PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode().equals(attemptDO.getAttemptStatus())) {
            PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(attemptDO.getCheckoutSessionId());
            return paymentResult(sessionDO, attemptDO);
        }
        // 3DS 回跳只证明浏览器完成认证跳转，不能把它当作扣款成功信号。
        attemptMapper.markThreeDsReturnedCas(attemptDO.getCheckoutAttemptId(),
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode(), attemptDO.getVersion(), now);
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(attemptDO.getCheckoutSessionId());
        if (sessionDO != null) {
            sessionMapper.markProcessingCas(sessionDO.getCheckoutSessionId(),
                    PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode(), sessionDO.getVersion(), now);
            sessionDO = sessionMapper.selectByCheckoutSessionId(attemptDO.getCheckoutSessionId());
        }
        insertEvent(event(sessionDO, latestAttempt == null ? attemptDO : latestAttempt, EVENT_THREE_DS_RETURNED,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL, PaymentCheckoutEventResultEnum.SUCCESS,
                commandDTO.getTraceId(), null, now));
        return paymentResult(sessionDO, latestAttempt == null ? attemptDO : latestAttempt);
    }

    /**
     * 将商户创建请求固化为会话快照，后续页面展示不再依赖商户实时配置变化。
     */
    private PaymentCheckoutSessionDO buildSession(PaymentCheckoutSessionCreateCommandDTO commandDTO, LocalDateTime now) {
        PaymentCheckoutSessionDO sessionDO = new PaymentCheckoutSessionDO();
        sessionDO.setCheckoutSessionId(globalIdGenerator.nextId());
        sessionDO.setMerchantId(commandDTO.getMerchantId());
        sessionDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        sessionDO.setMerchantRequestId(commandDTO.getMerchantRequestId());
        sessionDO.setRequestFingerprint(commandDTO.getRequestFingerprint());
        sessionDO.setPaymentAction(defaultIfBlank(commandDTO.getPaymentAction(), properties.getDefaultPaymentAction()));
        sessionDO.setIntegrationType(properties.getIntegrationType());
        sessionDO.setCheckoutStatus(PaymentCheckoutSessionStatusEnum.PAYABLE.getCode());
        sessionDO.setProcessStage(PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode());
        sessionDO.setLastStatusTime(now);
        sessionDO.setTransactionDateTime(now);
        sessionDO.setLabelCurrency(normalizeCurrency(commandDTO.getCurrency()));
        sessionDO.setLabelAmount(commandDTO.getAmount());
        sessionDO.setCurrencyExponent(commandDTO.getCurrencyExponent());
        sessionDO.setOrderSubject(commandDTO.getOrderSubject());
        sessionDO.setOrderDescription(commandDTO.getOrderDescription());
        sessionDO.setOrderItemsJson(commandDTO.getOrderItemsJson());
        sessionDO.setAllowedPaymentMethodsJson(JsonUtils.toJsonString(defaultAllowedPaymentMethods(commandDTO.getAllowedPaymentMethods())));
        sessionDO.setSelectedPaymentMethod(PAYMENT_METHOD_BANK_CARD);
        sessionDO.setSelectedPaymentBrand(null);
        sessionDO.setChannelCode(resolveChannelCode(commandDTO.getAllowedPaymentMethods()));
        sessionDO.setMerchantDisplayName(commandDTO.getMerchantDisplayName());
        sessionDO.setMerchantLogoUrl(commandDTO.getMerchantLogoUrl());
        sessionDO.setMerchantReturnUrl(commandDTO.getMerchantReturnUrl());
        sessionDO.setMerchantCancelUrl(commandDTO.getMerchantCancelUrl());
        sessionDO.setMerchantNotifyUrlHash(commandDTO.getMerchantNotifyUrlHash());
        sessionDO.setLocale(defaultIfBlank(commandDTO.getLocale(), "en-US"));
        sessionDO.setPayerCountry(commandDTO.getPayerCountry());
        sessionDO.setPayerEmailMasked(commandDTO.getPayerEmailMasked());
        sessionDO.setPayerEmailHash(commandDTO.getPayerEmailHash());
        sessionDO.setRetryAllowed(commandDTO.getRetryAllowed() == null ? 1 : commandDTO.getRetryAllowed());
        sessionDO.setMaxAttemptCount(resolveMaxAttemptCount(commandDTO.getMaxAttemptCount()));
        sessionDO.setAttemptCount(0);
        sessionDO.setCheckoutDomain(commandDTO.getCheckoutDomain());
        sessionDO.setExpireTime(resolveExpireTime(commandDTO.getExpireTime(), now));
        sessionDO.setVersion(0);
        sessionDO.setDeleted(0);
        sessionDO.setCreateTime(now);
        sessionDO.setUpdateTime(now);
        return sessionDO;
    }

    /**
     * 签发收银台访问 token；raw token 只返回到 URL，库内仅保存 HMAC 摘要。
     */
    private IssuedCheckoutToken createToken(String checkoutSessionId, String merchantId, LocalDateTime sessionExpireTime,
                                            String issueReason, LocalDateTime now) {
        String opaqueToken = PaymentCheckoutTokenSupport.newUrlSafeToken(properties.getOpaqueTokenBytes());
        PaymentCheckoutTokenDO tokenDO = new PaymentCheckoutTokenDO();
        tokenDO.setCheckoutTokenId(globalIdGenerator.nextId());
        tokenDO.setCheckoutSessionId(checkoutSessionId);
        tokenDO.setMerchantId(merchantId);
        tokenDO.setTokenHash(PaymentCheckoutTokenSupport.hmacSha256Hex(opaqueToken, properties.getTokenPepper()));
        tokenDO.setTokenHashAlg(PaymentCheckoutTokenSupport.TOKEN_HASH_ALG);
        tokenDO.setTokenKeyVersion(properties.getTokenKeyVersion());
        tokenDO.setTokenStatus(PaymentCheckoutTokenStatusEnum.ACTIVE.getCode());
        tokenDO.setIssueReason(issueReason);
        tokenDO.setExpireTime(sessionExpireTime);
        tokenDO.setUseCount(0);
        tokenDO.setVersion(0);
        tokenDO.setDeleted(0);
        tokenDO.setCreateTime(now);
        tokenDO.setUpdateTime(now);
        return new IssuedCheckoutToken(tokenDO, opaqueToken);
    }

    /**
     * 组装商户创建接口响应，幂等复用时也会返回新签发的付款人 URL。
     */
    private PaymentCheckoutSessionCreateResultDTO createResult(PaymentCheckoutSessionDO sessionDO,
                                                               IssuedCheckoutToken issuedToken,
                                                               boolean idempotentHit) {
        PaymentCheckoutSessionCreateResultDTO resultDTO = new PaymentCheckoutSessionCreateResultDTO();
        resultDTO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        resultDTO.setCheckoutTokenId(issuedToken.tokenDO().getCheckoutTokenId());
        resultDTO.setCheckoutUrl(buildCheckoutUrl(sessionDO.getCheckoutDomain(), issuedToken.opaqueToken()));
        resultDTO.setCheckoutStatus(sessionDO.getCheckoutStatus());
        resultDTO.setExpireTime(sessionDO.getExpireTime());
        resultDTO.setIdempotentHit(idempotentHit);
        return resultDTO;
    }

    /**
     * 生成付款人入口地址，cover 只是 URL 形态遮盖串，不参与查询、支付或幂等。
     */
    private String buildCheckoutUrl(String checkoutDomain, String opaqueToken) {
        String domain = checkoutDomain.endsWith("/") ? checkoutDomain.substring(0, checkoutDomain.length() - 1) : checkoutDomain;
        String cover = PaymentCheckoutTokenSupport.newUrlSafeToken(properties.getCoverBytes());
        return domain + "/checkout/" + opaqueToken + "/" + cover;
    }

    /**
     * 创建一次付款尝试快照，只保存卡 BIN、后四位、掩码和账户摘要，不保存 CVV。
     */
    private PaymentCheckoutAttemptDO buildAttempt(PaymentCheckoutSessionDO sessionDO,
                                                  PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                  LocalDateTime now) {
        PaymentCheckoutAttemptDO attemptDO = new PaymentCheckoutAttemptDO();
        attemptDO.setCheckoutAttemptId(globalIdGenerator.nextId());
        attemptDO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        attemptDO.setMerchantId(sessionDO.getMerchantId());
        attemptDO.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        attemptDO.setAttemptNo(nextAttemptNo(sessionDO));
        attemptDO.setAttemptRequestId(commandDTO.getAttemptRequestId());
        attemptDO.setRequestFingerprint(commandDTO.getRequestFingerprint());
        attemptDO.setAttemptStatus(PaymentCheckoutAttemptStatusEnum.CARD_SUBMITTED.getCode());
        attemptDO.setProcessStage(PaymentCheckoutProcessStageEnum.CARD_SUBMITTED.getCode());
        attemptDO.setPaymentMethod(commandDTO.getPaymentMethod());
        attemptDO.setPaymentBrand(resolveCardBrand(commandDTO.getCardInfo()));
        attemptDO.setLabelCurrency(sessionDO.getLabelCurrency());
        attemptDO.setLabelAmount(sessionDO.getLabelAmount());
        attemptDO.setChannelRequestCurrency(sessionDO.getLabelCurrency());
        attemptDO.setChannelRequestAmount(sessionDO.getLabelAmount());
        attemptDO.setOperationId("OP" + globalIdGenerator.nextId());
        attemptDO.setTransactionId(globalIdGenerator.nextId());
        attemptDO.setTransactionDateTime(now);
        attemptDO.setChannelCode(defaultIfBlank(sessionDO.getChannelCode(), CHANNEL_MPGS));
        fillMaskedCardInfo(attemptDO, commandDTO.getCardInfo());
        attemptDO.setThreeDsRequired(0);
        attemptDO.setBrowserInfoJson(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getBrowserInfoJson()));
        attemptDO.setDeviceInfoJson(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getDeviceInfoJson()));
        attemptDO.setSubmitTime(now);
        attemptDO.setResultSnapshot("{\"pageState\":\"PAYING\"}");
        attemptDO.setVersion(0);
        attemptDO.setDeleted(0);
        attemptDO.setCreateTime(now);
        attemptDO.setUpdateTime(now);
        return attemptDO;
    }

    /**
     * 在本地事务内创建付款尝试并 CAS 标记会话为 PAYING，避免重复点击生成多笔资金交易。
     */
    private PaymentSubmissionContext createPaymentSubmission(PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutTokenDO tokenDO = validateTokenAndSession(commandDTO.getTokenHash(), commandDTO.getCheckoutSessionId(), now,
                commandDTO.getTraceId(), commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(),
                commandDTO.getOriginHash(), commandDTO.getRefererHash(), null);
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(tokenDO.getCheckoutSessionId());
        PaymentCheckoutAttemptDO existedAttempt = attemptMapper.selectByAttemptRequest(
                sessionDO.getCheckoutSessionId(), commandDTO.getAttemptRequestId());
        if (existedAttempt != null) {
            return new PaymentSubmissionContext(sessionDO, existedAttempt, null, true);
        }

        ensureSessionPayable(sessionDO, now);
        ensurePaymentMethodAllowed(sessionDO, commandDTO);
        int remainingAttempts = remainingAttempts(sessionDO);
        if (remainingAttempts <= 0) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(), "checkout max attempt count exceeded");
        }
        PaymentCheckoutAttemptDO attemptDO = buildAttempt(sessionDO, commandDTO, now);
        String threeDsReturnToken = PaymentCheckoutTokenSupport.newUrlSafeToken(THREE_DS_RETURN_TOKEN_BYTES);
        attemptDO.setThreeDsReturnTokenHash(PaymentCheckoutTokenSupport.hmacSha256Hex(threeDsReturnToken, properties.getTokenPepper()));
        attemptMapper.insert(attemptDO);
        int updated = sessionMapper.markSubmittedCas(sessionDO.getCheckoutSessionId(),
                PaymentCheckoutSessionStatusEnum.PAYING.getCode(),
                PaymentCheckoutProcessStageEnum.CARD_SUBMITTED.getCode(),
                attemptDO.getTransactionId(),
                attemptDO.getOperationId(),
                attemptDO.getTransactionDateTime(),
                attemptDO.getCheckoutAttemptId(),
                sessionDO.getVersion(),
                now);
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout session status changed, please retry");
        }
        PaymentCheckoutSessionDO latestSession = sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId());
        insertEvent(event(latestSession == null ? sessionDO : latestSession, attemptDO, EVENT_PAYMENT_SUBMITTED,
                PaymentCheckoutProcessStageEnum.CARD_SUBMITTED, PaymentCheckoutEventResultEnum.SUCCESS,
                commandDTO.getTraceId(), commandDTO.getAttemptRequestId(), now));
        return new PaymentSubmissionContext(latestSession == null ? sessionDO : latestSession,
                attemptDO, buildThreeDsReturnUrl(latestSession == null ? sessionDO : latestSession,
                attemptDO, threeDsReturnToken), false);
    }

    /**
     * 处理 3DS 质询场景，只返回认证页面动作，资金扣款必须等浏览器回跳和后续渠道结果。
     */
    private PaymentCheckoutPaymentResultDTO applyThreeDsChallengeRequired(PaymentSubmissionContext context,
                                                                         PaymentCheckoutThreeDsResultDTO threeDsResult) {
        LocalDateTime now = LocalDateTime.now();
        updateAuthenticationSummary(context.attemptDO(), threeDsResult,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_INITIATED,
                PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER,
                null,
                now);
        PaymentCheckoutAttemptDO initiatedAttempt = attemptMapper.selectByCheckoutAttemptId(context.attemptDO().getCheckoutAttemptId());
        PaymentCheckoutAttemptDO currentAttempt = initiatedAttempt == null ? context.attemptDO() : initiatedAttempt;
        int updatedAttempt = attemptMapper.markThreeDsRequiredCas(currentAttempt.getCheckoutAttemptId(),
                PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode(),
                PaymentCheckoutProcessStageEnum.WAITING_3DS.getCode(),
                threeDsResult.getThreeDsStatus(),
                currentAttempt.getThreeDsReturnTokenHash(),
                sha256Hex(threeDsResult.getRedirectHtml()),
                currentAttempt.getVersion(),
                now);
        if (updatedAttempt != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout 3DS state changed, please retry");
        }
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(context.sessionDO().getCheckoutSessionId());
        if (sessionDO != null) {
            sessionMapper.markAuthenticatingCas(sessionDO.getCheckoutSessionId(),
                    PaymentCheckoutProcessStageEnum.WAITING_3DS.getCode(), sessionDO.getVersion(), now);
            sessionDO = sessionMapper.selectByCheckoutSessionId(context.sessionDO().getCheckoutSessionId());
        }
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(currentAttempt.getCheckoutAttemptId());
        insertEvent(event(sessionDO, latestAttempt, EVENT_THREE_DS_CHALLENGE_REQUIRED,
                PaymentCheckoutProcessStageEnum.WAITING_3DS, PaymentCheckoutEventResultEnum.SUCCESS,
                null, context.attemptDO().getAttemptRequestId(), now));
        PaymentCheckoutPaymentResultDTO resultDTO = paymentResult(sessionDO, latestAttempt);
        resultDTO.setPageState(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        resultDTO.setThreeDsAction(threeDsAction(threeDsResult.getRedirectHtml(), context.threeDsReturnUrl()));
        return resultDTO;
    }

    /**
     * 处理 3DS 明确失败，结果可重试还是终态失败由会话剩余尝试次数决定。
     */
    private PaymentCheckoutPaymentResultDTO applyThreeDsFailed(PaymentSubmissionContext context,
                                                              PaymentCheckoutThreeDsResultDTO threeDsResult) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO latestAttempt = updateAuthenticationSummary(context.attemptDO(), threeDsResult,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                now,
                now);
        latestAttempt = markAttemptResult(latestAttempt,
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                threeDsResult.getThreeDsStatus(),
                threeDsResult.getFailureCode(),
                firstText(threeDsResult.getFailureMessage(), DEFAULT_PAYER_FAILURE_MESSAGE),
                FAILURE_THREE_DS_AUTHENTICATION_FAILED,
                firstText(threeDsResult.getFailureMessage(), DEFAULT_PAYER_FAILURE_MESSAGE),
                DEFAULT_PAYER_FAILURE_MESSAGE,
                paymentSnapshot(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE, null),
                now);
        PaymentCheckoutSessionDO sessionDO = failSession(context.sessionDO(), latestAttempt, now);
        insertEvent(event(sessionDO, latestAttempt, EVENT_THREE_DS_FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.FAILED,
                null, latestAttempt == null ? null : latestAttempt.getAttemptRequestId(), now));
        return paymentResult(sessionDO, latestAttempt);
    }

    /**
     * 处理 3DS 或渠道仍在处理的情况，前端进入等待页并继续查询状态。
     */
    private PaymentCheckoutPaymentResultDTO applyThreeDsProcessing(PaymentSubmissionContext context,
                                                                  PaymentCheckoutThreeDsResultDTO threeDsResult) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO latestAttempt = updateAuthenticationSummary(context.attemptDO(), threeDsResult,
                PaymentCheckoutAttemptStatusEnum.PROCESSING,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL,
                null,
                now);
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(context.sessionDO().getCheckoutSessionId());
        if (sessionDO != null) {
            sessionMapper.markProcessingCas(sessionDO.getCheckoutSessionId(),
                    PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode(), sessionDO.getVersion(), now);
            sessionDO = sessionMapper.selectByCheckoutSessionId(context.sessionDO().getCheckoutSessionId());
        }
        return paymentResult(sessionDO, latestAttempt);
    }

    /**
     * 记录 3DS 认证通过摘要，随后才允许把卡信息和认证结果提交给支付核心。
     */
    private PaymentCheckoutAttemptDO applyThreeDsPassed(PaymentSubmissionContext context,
                                                       PaymentCheckoutThreeDsResultDTO threeDsResult) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO latestAttempt = updateAuthenticationSummary(context.attemptDO(), threeDsResult,
                PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL,
                now,
                now);
        insertEvent(event(context.sessionDO(), latestAttempt, EVENT_THREE_DS_AUTHENTICATED,
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL, PaymentCheckoutEventResultEnum.SUCCESS,
                null, latestAttempt == null ? null : latestAttempt.getAttemptRequestId(), now));
        return latestAttempt == null ? context.attemptDO() : latestAttempt;
    }

    /**
     * 渠道 3DS 返回空结果时按处理中兜底，避免把未知状态误判为成功或失败。
     */
    private PaymentCheckoutThreeDsResultDTO safeThreeDsResult(PaymentCheckoutThreeDsResultDTO threeDsResult) {
        if (threeDsResult != null) {
            return threeDsResult;
        }
        PaymentCheckoutThreeDsResultDTO fallback = new PaymentCheckoutThreeDsResultDTO();
        fallback.setStatus("PROCESSING");
        fallback.setFailureCode(FAILURE_CHANNEL_PROCESSING);
        return fallback;
    }

    /**
     * 将已通过认证的收银台尝试转换为支付核心命令，资金交易仍由支付核心状态机负责。
     */
    private PaymentCreateResultDTO createCorePayment(PaymentCheckoutSessionDO sessionDO,
                                                     PaymentCheckoutAttemptDO attemptDO,
                                                     PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                     PaymentCheckoutThreeDsResultDTO threeDsResult) {
        PaymentCreateCommandDTO createCommand = new PaymentCreateCommandDTO();
        createCommand.setMerchantId(sessionDO.getMerchantId());
        createCommand.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        createCommand.setMerchantOrderId(attemptDO.getAttemptRequestId());
        createCommand.setTransactionId(attemptDO.getTransactionId());
        createCommand.setPaymentMethod(attemptDO.getPaymentMethod());
        createCommand.setAmount(sessionDO.getLabelAmount());
        createCommand.setCurrency(sessionDO.getLabelCurrency());
        createCommand.setLabelAmount(sessionDO.getLabelAmount());
        createCommand.setLabelCurrency(sessionDO.getLabelCurrency());
        createCommand.setTransactionDateTime(attemptDO.getTransactionDateTime());
        createCommand.setRequestFingerprint(commandDTO.getRequestFingerprint());
        createCommand.setCardInfo(toCoreCardInfo(commandDTO.getCardInfo()));
        createCommand.setBillingCardHolderInfo(toCoreBillingInfo(commandDTO.getBillingCardHolderInfo()));
        createCommand.setThreeDsInfo(toCoreThreeDsInfo(threeDsResult));
        createCommand.setChannelIdentity(toCoreChannelIdentity(threeDsResult));
        createCommand.setTransactionInfo(toCoreTransactionInfo(sessionDO, attemptDO));
        createCommand.setSourceUrl(commandDTO.getOriginHash());
        createCommand.setPayerIp(commandDTO.getClientIpHash());
        createCommand.setUserAgent(commandDTO.getUserAgentHash());
        // 卡号和 CVV 只在当前内存调用链传递给支付核心，不落库、不进入事件或日志明文。
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(normalizePaymentAction(sessionDO.getPaymentAction()))) {
            return paymentTransactionService.createAuthorization(createCommand);
        }
        return paymentTransactionService.createPayment(createCommand);
    }

    /**
     * 将支付核心结果映射回收银台页面状态，终态更新必须通过 CAS 避免覆盖并发结果。
     */
    private PaymentCheckoutPaymentResultDTO applyPaymentCoreResult(PaymentCheckoutSessionDO sourceSessionDO,
                                                                   PaymentCheckoutAttemptDO sourceAttemptDO,
                                                                   PaymentCreateResultDTO paymentResultDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO latestAttempt;
        if (isSuccess(paymentResultDTO)) {
            latestAttempt = markAttemptResult(sourceAttemptDO,
                    PaymentCheckoutAttemptStatusEnum.SUCCEEDED,
                    PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                    paymentResultDTO.getStatus(),
                    paymentResultDTO.getMerchantResponseCode(),
                    paymentResultDTO.getMerchantResponseMessage(),
                    null,
                    null,
                    null,
                    paymentSnapshot(PaymentCheckoutPageStateEnum.SUCCEEDED, paymentResultDTO),
                    now);
            PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
            if (sessionDO != null) {
                sessionMapper.markSucceededCas(sessionDO.getCheckoutSessionId(),
                        PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode(),
                        latestAttempt == null ? sourceAttemptDO.getCheckoutAttemptId() : latestAttempt.getCheckoutAttemptId(),
                        sourceAttemptDO.getTransactionId(),
                        sourceAttemptDO.getOperationId(),
                        sourceAttemptDO.getTransactionDateTime(),
                        sessionDO.getVersion(),
                        now);
                sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
            }
            return paymentResult(sessionDO, latestAttempt);
        }
        if (isFailed(paymentResultDTO)) {
            latestAttempt = markAttemptResult(sourceAttemptDO,
                    PaymentCheckoutAttemptStatusEnum.FAILED,
                    PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                    paymentResultDTO.getStatus(),
                    paymentResultDTO.getMerchantResponseCode(),
                    paymentResultDTO.getMerchantResponseMessage(),
                    firstText(paymentResultDTO.getFailReasonCode(), FAILURE_PAYMENT_DECLINED),
                    firstText(paymentResultDTO.getFailReasonMessage(), paymentResultDTO.getMerchantResponseMessage()),
                    DEFAULT_PAYER_FAILURE_MESSAGE,
                    paymentSnapshot(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE, paymentResultDTO),
                    now);
            PaymentCheckoutSessionDO sessionDO = failSession(sourceSessionDO, latestAttempt, now);
            return paymentResult(sessionDO, latestAttempt);
        }
        latestAttempt = markAttemptResult(sourceAttemptDO,
                PaymentCheckoutAttemptStatusEnum.PROCESSING,
                PaymentCheckoutProcessStageEnum.WAITING_CHANNEL,
                paymentResultDTO == null ? null : paymentResultDTO.getStatus(),
                paymentResultDTO == null ? null : paymentResultDTO.getMerchantResponseCode(),
                paymentResultDTO == null ? null : paymentResultDTO.getMerchantResponseMessage(),
                FAILURE_CHANNEL_PROCESSING,
                null,
                null,
                paymentSnapshot(PaymentCheckoutPageStateEnum.PROCESSING, paymentResultDTO),
                now);
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
        if (sessionDO != null) {
            sessionMapper.markProcessingCas(sessionDO.getCheckoutSessionId(),
                    PaymentCheckoutProcessStageEnum.WAITING_CHANNEL.getCode(), sessionDO.getVersion(), now);
            sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
        }
        return paymentResult(sessionDO, latestAttempt);
    }

    /**
     * 组装收银台展示视图，使用创建时快照，避免商户后续配置变更影响已创建会话。
     */
    private PaymentCheckoutSessionQueryResultDTO sessionResult(PaymentCheckoutSessionDO sessionDO) {
        PaymentCheckoutSessionQueryResultDTO resultDTO = new PaymentCheckoutSessionQueryResultDTO();
        resultDTO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        resultDTO.setPageState(toPageState(sessionDO).getCode());
        PaymentCheckoutSessionQueryResultDTO.MerchantDTO merchantDTO = new PaymentCheckoutSessionQueryResultDTO.MerchantDTO();
        merchantDTO.setDisplayName(sessionDO.getMerchantDisplayName());
        merchantDTO.setLogoUrl(sessionDO.getMerchantLogoUrl());
        resultDTO.setMerchant(merchantDTO);
        PaymentCheckoutSessionQueryResultDTO.OrderDTO orderDTO = new PaymentCheckoutSessionQueryResultDTO.OrderDTO();
        orderDTO.setOrderNo(sessionDO.getMerchantOrderNo());
        orderDTO.setSubject(sessionDO.getOrderSubject());
        orderDTO.setDescription(sessionDO.getOrderDescription());
        orderDTO.setAmount(sessionDO.getLabelAmount());
        orderDTO.setCurrency(sessionDO.getLabelCurrency());
        orderDTO.setCurrencyExponent(sessionDO.getCurrencyExponent());
        orderDTO.setItemsJson(sessionDO.getOrderItemsJson());
        resultDTO.setOrder(orderDTO);
        resultDTO.setPaymentMethods(parsePaymentMethods(sessionDO.getAllowedPaymentMethodsJson()));
        PaymentCheckoutSessionQueryResultDTO.CheckoutDTO checkoutDTO = new PaymentCheckoutSessionQueryResultDTO.CheckoutDTO();
        checkoutDTO.setExpireTime(sessionDO.getExpireTime());
        checkoutDTO.setRetryAllowed(sessionDO.getRetryAllowed() != null && sessionDO.getRetryAllowed() == 1);
        checkoutDTO.setRemainingAttemptCount(Math.max(0, remainingAttempts(sessionDO)));
        checkoutDTO.setPollingIntervalSeconds(properties.getPollingIntervalSeconds());
        resultDTO.setCheckout(checkoutDTO);
        return resultDTO;
    }

    /**
     * 组装付款结果页视图，3DS_REQUIRED 优先使用尝试状态覆盖会话状态。
     */
    private PaymentCheckoutPaymentResultDTO paymentResult(PaymentCheckoutSessionDO sessionDO, PaymentCheckoutAttemptDO attemptDO) {
        PaymentCheckoutPaymentResultDTO resultDTO = new PaymentCheckoutPaymentResultDTO();
        resultDTO.setCheckoutSessionId(sessionDO == null ? null : sessionDO.getCheckoutSessionId());
        resultDTO.setCheckoutAttemptId(attemptDO == null ? null : attemptDO.getCheckoutAttemptId());
        resultDTO.setPageState(sessionDO == null ? PaymentCheckoutPageStateEnum.BLOCKED.getCode() : toPageState(sessionDO).getCode());
        resultDTO.setResult(paymentResultDetail(sessionDO, attemptDO));
        resultDTO.setFailure(failureDetail(sessionDO, attemptDO));
        resultDTO.setPolling(pollingDetail());
        resultDTO.setActions(actionDetail(sessionDO));
        if (attemptDO != null && PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode().equals(attemptDO.getAttemptStatus())) {
            resultDTO.setPageState(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        }
        return resultDTO;
    }

    /**
     * 输出付款人可见的交易摘要，只包含掩码卡号和平台交易标识。
     */
    private PaymentCheckoutPaymentResultDTO.PaymentResultDTO paymentResultDetail(PaymentCheckoutSessionDO sessionDO,
                                                                                 PaymentCheckoutAttemptDO attemptDO) {
        if (sessionDO == null) {
            return null;
        }
        PaymentCheckoutPaymentResultDTO.PaymentResultDTO resultDTO = new PaymentCheckoutPaymentResultDTO.PaymentResultDTO();
        resultDTO.setAmount(sessionDO.getLabelAmount());
        resultDTO.setCurrency(sessionDO.getLabelCurrency());
        resultDTO.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        resultDTO.setPaymentMethod(attemptDO == null ? sessionDO.getSelectedPaymentMethod() : attemptDO.getPaymentMethod());
        resultDTO.setCardBrand(attemptDO == null ? sessionDO.getSelectedPaymentBrand() : attemptDO.getPaymentBrand());
        resultDTO.setCardNumberMasked(attemptDO == null ? null : attemptDO.getCardNumberMasked());
        resultDTO.setTransactionId(attemptDO == null ? sessionDO.getLatestTransactionId() : attemptDO.getTransactionId());
        resultDTO.setTransactionDateTime(attemptDO == null ? sessionDO.getTransactionDateTime() : attemptDO.getTransactionDateTime());
        resultDTO.setAuthCode(attemptDO == null ? null : attemptDO.getChannelResponseCode());
        return resultDTO;
    }

    /**
     * 输出付款人失败提示，内部渠道原因不直接暴露给浏览器。
     */
    private PaymentCheckoutPaymentResultDTO.FailureDTO failureDetail(PaymentCheckoutSessionDO sessionDO,
                                                                     PaymentCheckoutAttemptDO attemptDO) {
        if (sessionDO == null || attemptDO == null || attemptDO.getFailureReasonCode() == null) {
            return null;
        }
        PaymentCheckoutPaymentResultDTO.FailureDTO failureDTO = new PaymentCheckoutPaymentResultDTO.FailureDTO();
        failureDTO.setReasonCode(attemptDO.getFailureReasonCode());
        failureDTO.setMessage(attemptDO.getPayerVisibleMessage());
        failureDTO.setRetryAllowed(sessionDO.getRetryAllowed() != null && sessionDO.getRetryAllowed() == 1);
        failureDTO.setRemainingAttemptCount(Math.max(0, remainingAttempts(sessionDO)));
        return failureDTO;
    }

    /**
     * 返回前端轮询建议；轮询只读状态，不替代渠道回调或支付核心状态更新。
     */
    private PaymentCheckoutPaymentResultDTO.PollingDTO pollingDetail() {
        PaymentCheckoutPaymentResultDTO.PollingDTO pollingDTO = new PaymentCheckoutPaymentResultDTO.PollingDTO();
        pollingDTO.setStatusUrl("/checkout/api/v1/payment/status");
        pollingDTO.setIntervalSeconds(properties.getPollingIntervalSeconds());
        pollingDTO.setMaxIntervalSeconds(properties.getMaxPollingIntervalSeconds());
        return pollingDTO;
    }

    /**
     * 返回付款人页面跳转地址；returnUrl 是浏览器回跳商户页面，不是异步通知地址。
     */
    private PaymentCheckoutPaymentResultDTO.ActionDTO actionDetail(PaymentCheckoutSessionDO sessionDO) {
        if (sessionDO == null) {
            return null;
        }
        PaymentCheckoutPaymentResultDTO.ActionDTO actionDTO = new PaymentCheckoutPaymentResultDTO.ActionDTO();
        actionDTO.setReturnUrl(sessionDO.getMerchantReturnUrl());
        actionDTO.setCancelUrl(sessionDO.getMerchantCancelUrl());
        return actionDTO;
    }

    /**
     * 封装 3DS 质询动作，HTML 由渠道返回并在收银台 iframe 内展示。
     */
    private PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO threeDsAction(String html, String returnUrl) {
        PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO actionDTO = new PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO();
        actionDTO.setActionType(THREE_DS_ACTION_HTML);
        actionDTO.setHtml(html);
        actionDTO.setReturnUrl(returnUrl);
        actionDTO.setTimeoutSeconds(THREE_DS_TIMEOUT_SECONDS);
        return actionDTO;
    }

    /**
     * 按 CAS 写入 3DS 认证摘要，保留责任转移和卡组织认证字段供交易详情追溯。
     */
    private PaymentCheckoutAttemptDO updateAuthenticationSummary(PaymentCheckoutAttemptDO attemptDO,
                                                                 PaymentCheckoutThreeDsResultDTO threeDsResult,
                                                                 PaymentCheckoutAttemptStatusEnum nextStatus,
                                                                 PaymentCheckoutProcessStageEnum nextStage,
                                                                 LocalDateTime authenticationCompleteTime,
                                                                 LocalDateTime now) {
        attemptMapper.markAuthenticationResultCas(attemptDO.getCheckoutAttemptId(),
                nextStatus.getCode(),
                nextStage.getCode(),
                threeDsResult.getChannelMidConfigId(),
                threeDsResult.getChannelOrderNo(),
                threeDsResult.getChannelTransactionId(),
                threeDsResult.getChannelRequestId(),
                threeDsResult.challengeRequired() ? 1 : 0,
                threeDsResult.getThreeDsStatus(),
                threeDsResult.getThreeDsVersion(),
                firstText(threeDsResult.getAuthenticationTransactionId(), threeDsResult.getThreeDsTransactionId()),
                threeDsResult.getThreeDsServerTransactionId(),
                threeDsResult.getAcsTransactionId(),
                threeDsResult.getDsTransactionId(),
                threeDsResult.getEci(),
                hasLiabilityShift(threeDsResult) ? 1 : 0,
                authenticationCompleteTime,
                attemptDO.getVersion(),
                now);
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        return latestAttempt == null ? attemptDO : latestAttempt;
    }

    /**
     * 按 CAS 写入付款尝试结果，避免异步回调或重复查询覆盖较新的尝试状态。
     */
    private PaymentCheckoutAttemptDO markAttemptResult(PaymentCheckoutAttemptDO attemptDO,
                                                       PaymentCheckoutAttemptStatusEnum nextStatus,
                                                       PaymentCheckoutProcessStageEnum nextStage,
                                                       String channelStatus,
                                                       String channelResponseCode,
                                                       String channelResponseMessage,
                                                       String failureReasonCode,
                                                       String failureReasonMessage,
                                                       String payerVisibleMessage,
                                                       String resultSnapshot,
                                                       LocalDateTime completeTime) {
        if (attemptDO == null) {
            return null;
        }
        attemptMapper.markResultCas(attemptDO.getCheckoutAttemptId(),
                nextStatus.getCode(),
                nextStage.getCode(),
                channelStatus,
                channelResponseCode,
                channelResponseMessage,
                failureReasonCode,
                failureReasonMessage,
                payerVisibleMessage,
                resultSnapshot,
                attemptDO.getVersion(),
                completeTime);
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        return latestAttempt == null ? attemptDO : latestAttempt;
    }

    /**
     * 根据剩余尝试次数决定失败后是否允许重新支付。
     */
    private PaymentCheckoutSessionDO failSession(PaymentCheckoutSessionDO sourceSessionDO,
                                                PaymentCheckoutAttemptDO latestAttempt,
                                                LocalDateTime now) {
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
        if (sessionDO == null) {
            return sourceSessionDO;
        }
        PaymentCheckoutSessionStatusEnum nextStatus = remainingAttempts(sessionDO) > 0
                && sessionDO.getRetryAllowed() != null
                && sessionDO.getRetryAllowed() == 1
                ? PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE
                : PaymentCheckoutSessionStatusEnum.FAILED_FINAL;
        sessionMapper.markFailedCas(sessionDO.getCheckoutSessionId(),
                nextStatus.getCode(),
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode(),
                sessionDO.getVersion(),
                now);
        PaymentCheckoutSessionDO latestSession = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
        return latestSession == null ? sessionDO : latestSession;
    }

    /**
     * 仅为本次支付核心调用转换卡信息，调用完成后不在收银台侧持久化敏感字段。
     */
    private PaymentCreateCommandDTO.CardInfoDTO toCoreCardInfo(PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.CardInfoDTO target = new PaymentCreateCommandDTO.CardInfoDTO();
        target.setCardNo(source.getCardNo());
        target.setExpirationMonth(source.getExpirationMonth());
        target.setExpirationYear(source.getExpirationYear());
        target.setSecurityCode(source.getSecurityCode());
        return target;
    }

    /**
     * 转换账单持卡人信息，用于渠道风控和 3DS 认证上下文。
     */
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO toCoreBillingInfo(
            PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO target = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 将收银台 3DS 认证摘要透传给支付核心，用于责任转移和渠道身份复用。
     */
    private PaymentCreateCommandDTO.ThreeDsInfoDTO toCoreThreeDsInfo(PaymentCheckoutThreeDsResultDTO source) {
        PaymentCreateCommandDTO.ThreeDsInfoDTO target = new PaymentCreateCommandDTO.ThreeDsInfoDTO();
        target.setAuthenticationTransactionId(source.getAuthenticationTransactionId());
        target.setEci(source.getEci());
        target.setCavv(source.getCavv());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        return target;
    }

    /**
     * 复用 3DS 阶段生成的渠道订单身份，避免授权阶段重新生成不一致的渠道订单。
     */
    private PaymentCreateCommandDTO.ChannelIdentityDTO toCoreChannelIdentity(PaymentCheckoutThreeDsResultDTO source) {
        PaymentCreateCommandDTO.ChannelIdentityDTO target = new PaymentCreateCommandDTO.ChannelIdentityDTO();
        target.setChannelOrderNo(source.getChannelOrderNo());
        return target;
    }

    /**
     * 将收银台尝试号映射为支付核心交易标识，保持结果页、回调和交易详情可关联。
     */
    private PaymentCreateCommandDTO.TransactionInfoDTO toCoreTransactionInfo(PaymentCheckoutSessionDO sessionDO,
                                                                            PaymentCheckoutAttemptDO attemptDO) {
        PaymentCreateCommandDTO.TransactionInfoDTO target = new PaymentCreateCommandDTO.TransactionInfoDTO();
        target.setTransactionId(attemptDO.getTransactionId());
        target.setDescription(sessionDO.getOrderDescription());
        return target;
    }

    /**
     * 简化判断 3DS 是否具备责任转移证据；更细的卡组织规则由后续渠道能力扩展。
     */
    private boolean hasLiabilityShift(PaymentCheckoutThreeDsResultDTO threeDsResult) {
        return StringUtils.hasText(threeDsResult.getEci()) || StringUtils.hasText(threeDsResult.getCavv());
    }

    /**
     * 只把支付核心明确 SUCCESS 映射为收银台成功终态。
     */
    private boolean isSuccess(PaymentCreateResultDTO resultDTO) {
        return resultDTO != null && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
    }

    /**
     * 只把支付核心明确 FAILED 映射为失败页，其余状态保持处理中。
     */
    private boolean isFailed(PaymentCreateResultDTO resultDTO) {
        return resultDTO != null && PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }

    /**
     * 规范化付款动作，缺省值来自收银台配置。
     */
    private String normalizePaymentAction(String paymentAction) {
        return defaultIfBlank(paymentAction, properties.getDefaultPaymentAction()).trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 构造 MPGS 3DS 完成后的平台 bridge 地址，return token 只在该地址中出现一次。
     */
    private String buildThreeDsReturnUrl(PaymentCheckoutSessionDO sessionDO,
                                         PaymentCheckoutAttemptDO attemptDO,
                                         String threeDsReturnToken) {
        String domain = sessionDO.getCheckoutDomain().endsWith("/")
                ? sessionDO.getCheckoutDomain().substring(0, sessionDO.getCheckoutDomain().length() - 1)
                : sessionDO.getCheckoutDomain();
        return domain + "/checkout/api/v1/3ds/bridge"
                + "?checkoutSessionId=" + urlEncode(attemptDO.getCheckoutSessionId())
                + "&checkoutAttemptId=" + urlEncode(attemptDO.getCheckoutAttemptId())
                + "&threeDsReturnToken=" + urlEncode(threeDsReturnToken);
    }

    /**
     * URL 编码 3DS bridge 查询参数，避免 token 中的 URL safe 字符被中间层误解析。
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 保存可审计的结果摘要，先脱敏再写入尝试快照。
     */
    private String paymentSnapshot(PaymentCheckoutPageStateEnum pageState, PaymentCreateResultDTO paymentResultDTO) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("pageState", pageState.getCode());
        snapshot.put("transactionStatus", paymentResultDTO == null ? null : paymentResultDTO.getStatus());
        snapshot.put("merchantResponseCode", paymentResultDTO == null ? null : paymentResultDTO.getMerchantResponseCode());
        snapshot.put("merchantResponseMessage", paymentResultDTO == null ? null : paymentResultDTO.getMerchantResponseMessage());
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(snapshot));
    }

    /**
     * 选择可展示文本时优先保留渠道/核心给出的明确说明。
     */
    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 对 HTML、notifyUrl 等不需要明文回显的内容生成审计摘要。
     */
    private String sha256Hex(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout digest failed", exception);
        }
    }

    /**
     * 商户幂等号命中时必须校验请求摘要一致，防止同一 orderId 被复用为不同订单。
     */
    private void validateIdempotentFingerprint(PaymentCheckoutSessionDO existed, PaymentCheckoutSessionCreateCommandDTO commandDTO) {
        if (!Objects.equals(existed.getRequestFingerprint(), commandDTO.getRequestFingerprint())) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "checkout session idempotency conflict");
        }
    }

    /**
     * 校验 token 与会话绑定关系；任何错绑都记录安全事件并阻断浏览器继续支付。
     */
    private PaymentCheckoutTokenDO validateTokenAndSession(String tokenHash,
                                                           String checkoutSessionId,
                                                           LocalDateTime now,
                                                           String traceId,
                                                           String clientIpHash,
                                                           String userAgentHash,
                                                           String originHash,
                                                           String refererHash,
                                                           String deviceIdHash) {
        PaymentCheckoutTokenDO tokenDO = tokenMapper.selectByTokenHash(tokenHash);
        if (!isUsableToken(tokenDO, now) || !Objects.equals(checkoutSessionId, tokenDO.getCheckoutSessionId())) {
            recordSecurityEvent(tokenHash, checkoutSessionId, null, SECURITY_SESSION_MISMATCH,
                    PaymentCheckoutSecurityDecisionEnum.BLOCK, traceId, clientIpHash, userAgentHash,
                    originHash, refererHash, deviceIdHash, now);
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "checkout token is invalid");
        }
        return tokenDO;
    }

    /**
     * 限制只有可支付或可重试失败状态能再次提交卡信息。
     */
    private void ensureSessionPayable(PaymentCheckoutSessionDO sessionDO, LocalDateTime now) {
        if (sessionDO == null) {
            throw new ServiceException(ApiResultEnum.QUERY_RESULT_NOT_FOUND);
        }
        if (sessionDO.getExpireTime() != null && !sessionDO.getExpireTime().isAfter(now)) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "checkout session expired");
        }
        String status = sessionDO.getCheckoutStatus();
        if (!PaymentCheckoutSessionStatusEnum.PAYABLE.getCode().equals(status)
                && !PaymentCheckoutSessionStatusEnum.PAYABLE_FAILED_RETRYABLE.getCode().equals(status)) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(), "checkout session is not payable");
        }
    }

    /**
     * 校验商户创建会话时允许的支付方式快照，当前 V1 只放行 BANK_CARD + MPGS。
     */
    private void ensurePaymentMethodAllowed(PaymentCheckoutSessionDO sessionDO,
                                            PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        if (!PAYMENT_METHOD_BANK_CARD.equals(commandDTO.getPaymentMethod())) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(),
                    "hosted checkout v1 only supports BANK_CARD");
        }
        boolean allowed = parsePaymentMethods(sessionDO.getAllowedPaymentMethodsJson()).stream()
                .anyMatch(method -> PAYMENT_METHOD_BANK_CARD.equals(method.getPaymentMethod())
                        && CHANNEL_MPGS.equals(method.getChannelCode()));
        if (!allowed) {
            throw new ServiceException(ApiResultEnum.CARD_NOT_SUPPORTED.getCode(),
                    "checkout payment method is not allowed");
        }
    }

    /**
     * 优先查询前端指定的尝试号，缺省时回落到会话最近一次尝试。
     */
    private PaymentCheckoutAttemptDO resolveAttempt(PaymentCheckoutSessionDO sessionDO, String checkoutAttemptId) {
        if (sessionDO == null) {
            return null;
        }
        String targetAttemptId = StringUtils.hasText(checkoutAttemptId) ? checkoutAttemptId : sessionDO.getLastAttemptId();
        return StringUtils.hasText(targetAttemptId) ? attemptMapper.selectByCheckoutAttemptId(targetAttemptId) : null;
    }

    /**
     * token 必须存在、激活且未过期，才允许用于页面查询或付款提交。
     */
    private boolean isUsableToken(PaymentCheckoutTokenDO tokenDO, LocalDateTime now) {
        return tokenDO != null
                && PaymentCheckoutTokenStatusEnum.ACTIVE.getCode().equals(tokenDO.getTokenStatus())
                && tokenDO.getExpireTime() != null
                && tokenDO.getExpireTime().isAfter(now);
    }

    /**
     * 会话和 token 任一过期都视为收银台不可继续支付。
     */
    private boolean isExpired(PaymentCheckoutSessionDO sessionDO, PaymentCheckoutTokenDO tokenDO, LocalDateTime now) {
        return sessionDO.getExpireTime() == null
                || !sessionDO.getExpireTime().isAfter(now)
                || tokenDO.getExpireTime() == null
                || !tokenDO.getExpireTime().isAfter(now);
    }

    /**
     * 将内部会话状态映射为前端页面状态，未知状态按拦截处理。
     */
    private PaymentCheckoutPageStateEnum toPageState(PaymentCheckoutSessionDO sessionDO) {
        if (sessionDO == null || sessionDO.getCheckoutStatus() == null) {
            return PaymentCheckoutPageStateEnum.BLOCKED;
        }
        return switch (sessionDO.getCheckoutStatus()) {
            case "PAYABLE" -> PaymentCheckoutPageStateEnum.PAYABLE;
            case "PAYING", "PROCESSING" -> PaymentCheckoutPageStateEnum.PROCESSING;
            case "AUTHENTICATING" -> PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED;
            case "PAYABLE_FAILED_RETRYABLE" -> PaymentCheckoutPageStateEnum.FAILED_RETRYABLE;
            case "SUCCEEDED" -> PaymentCheckoutPageStateEnum.SUCCEEDED;
            case "FAILED_FINAL" -> PaymentCheckoutPageStateEnum.FAILED_FINAL;
            case "EXPIRED" -> PaymentCheckoutPageStateEnum.EXPIRED;
            case "CANCELLED" -> PaymentCheckoutPageStateEnum.CANCELLED;
            default -> PaymentCheckoutPageStateEnum.BLOCKED;
        };
    }

    /**
     * 返回会话查询拦截视图，不泄露商户名、订单号或 token 细节。
     */
    private PaymentCheckoutSessionQueryResultDTO blockedSessionResult() {
        PaymentCheckoutSessionQueryResultDTO resultDTO = new PaymentCheckoutSessionQueryResultDTO();
        resultDTO.setPageState(PaymentCheckoutPageStateEnum.BLOCKED.getCode());
        return resultDTO;
    }

    /**
     * 返回过期视图，付款按钮必须关闭且不允许继续重试。
     */
    private PaymentCheckoutSessionQueryResultDTO expiredSessionResult(PaymentCheckoutSessionDO sessionDO) {
        PaymentCheckoutSessionQueryResultDTO resultDTO = new PaymentCheckoutSessionQueryResultDTO();
        resultDTO.setCheckoutSessionId(sessionDO.getCheckoutSessionId());
        resultDTO.setPageState(PaymentCheckoutPageStateEnum.EXPIRED.getCode());
        PaymentCheckoutSessionQueryResultDTO.CheckoutDTO checkoutDTO = new PaymentCheckoutSessionQueryResultDTO.CheckoutDTO();
        checkoutDTO.setExpireTime(sessionDO.getExpireTime());
        checkoutDTO.setRetryAllowed(false);
        checkoutDTO.setRemainingAttemptCount(0);
        checkoutDTO.setPollingIntervalSeconds(properties.getPollingIntervalSeconds());
        resultDTO.setCheckout(checkoutDTO);
        return resultDTO;
    }

    /**
     * 返回支付阶段拦截视图，保留会话和尝试号便于内部排查。
     */
    private PaymentCheckoutPaymentResultDTO blockedPaymentResult(String checkoutSessionId, String checkoutAttemptId) {
        PaymentCheckoutPaymentResultDTO resultDTO = new PaymentCheckoutPaymentResultDTO();
        resultDTO.setCheckoutSessionId(checkoutSessionId);
        resultDTO.setCheckoutAttemptId(checkoutAttemptId);
        resultDTO.setPageState(PaymentCheckoutPageStateEnum.BLOCKED.getCode());
        return resultDTO;
    }

    /**
     * 构造收银台业务事件，记录页面动作和状态流转摘要。
     */
    private PaymentCheckoutEventDO event(PaymentCheckoutSessionDO sessionDO,
                                         PaymentCheckoutAttemptDO attemptDO,
                                         String eventType,
                                         PaymentCheckoutProcessStageEnum stage,
                                         PaymentCheckoutEventResultEnum result,
                                         String traceId,
                                         String requestId,
                                         LocalDateTime now) {
        PaymentCheckoutEventDO eventDO = new PaymentCheckoutEventDO();
        eventDO.setCheckoutEventId(globalIdGenerator.nextId());
        eventDO.setCheckoutSessionId(sessionDO == null ? null : sessionDO.getCheckoutSessionId());
        eventDO.setCheckoutAttemptId(attemptDO == null ? null : attemptDO.getCheckoutAttemptId());
        eventDO.setMerchantId(sessionDO == null ? null : sessionDO.getMerchantId());
        eventDO.setEventType(eventType);
        eventDO.setEventStage(stage.getCode());
        eventDO.setEventResult(result.getCode());
        eventDO.setCheckoutStatusAfter(sessionDO == null ? null : sessionDO.getCheckoutStatus());
        eventDO.setAttemptStatusAfter(attemptDO == null ? null : attemptDO.getAttemptStatus());
        eventDO.setOperationId(attemptDO == null ? sessionDO == null ? null : sessionDO.getOperationId() : attemptDO.getOperationId());
        eventDO.setTransactionId(attemptDO == null ? sessionDO == null ? null : sessionDO.getLatestTransactionId() : attemptDO.getTransactionId());
        eventDO.setTransactionDateTime(attemptDO == null ? sessionDO == null ? null : sessionDO.getTransactionDateTime() : attemptDO.getTransactionDateTime());
        eventDO.setTraceId(traceId);
        eventDO.setRequestId(requestId);
        eventDO.setEventTime(now);
        eventDO.setCreateTime(now);
        return eventDO;
    }

    /**
     * 写入业务事件；事件失败应跟随当前事务回滚，避免审计记录和状态不一致。
     */
    private void insertEvent(PaymentCheckoutEventDO eventDO) {
        eventMapper.insert(eventDO);
    }

    /**
     * 记录 token 无效、过期或错绑等安全事件，字段全部使用摘要或掩码。
     */
    private void recordSecurityEvent(String tokenHash,
                                     String checkoutSessionId,
                                     String checkoutAttemptId,
                                     String eventType,
                                     PaymentCheckoutSecurityDecisionEnum decision,
                                     String traceId,
                                     String clientIpHash,
                                     String userAgentHash,
                                     String originHash,
                                     String refererHash,
                                     String deviceIdHash,
                                     LocalDateTime now) {
        PaymentCheckoutSecurityEventDO eventDO = new PaymentCheckoutSecurityEventDO();
        eventDO.setSecurityEventId(globalIdGenerator.nextId());
        eventDO.setCheckoutSessionId(checkoutSessionId);
        eventDO.setCheckoutAttemptId(checkoutAttemptId);
        eventDO.setTokenHash(tokenHash);
        eventDO.setSecurityEventType(eventType);
        eventDO.setSecurityDecision(decision.getCode());
        eventDO.setBlockReasonCode(eventType);
        eventDO.setHttpStatus(401);
        eventDO.setClientIpHash(clientIpHash);
        eventDO.setUserAgentHash(userAgentHash);
        eventDO.setOriginHash(originHash);
        eventDO.setRefererHash(refererHash);
        eventDO.setDeviceIdHash(deviceIdHash);
        eventDO.setTraceId(traceId);
        eventDO.setEventTime(now);
        eventDO.setCreateTime(now);
        securityEventMapper.insert(eventDO);
    }

    /**
     * 生成允许支付方式快照；未显式传入时默认 BANK_CARD + MPGS。
     */
    private List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> defaultAllowedPaymentMethods(
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> input) {
        if (input != null && !input.isEmpty()) {
            validateAllowedPaymentMethods(input);
            return input;
        }
        PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO methodDTO =
                new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        methodDTO.setPaymentMethod(PAYMENT_METHOD_BANK_CARD);
        methodDTO.setChannelCode(CHANNEL_MPGS);
        methodDTO.setBrands(List.of("VISA", "MASTERCARD", "AMEX", "JCB"));
        methodDTO.setThreeDsMode("AUTO");
        return List.of(methodDTO);
    }

    /**
     * 解析创建会话时保存的支付方式快照，用于页面展示和提交校验。
     */
    private List<PaymentCheckoutSessionQueryResultDTO.PaymentMethodDTO> parsePaymentMethods(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods =
                JsonUtils.parseObject(json, new TypeReference<>() {
                });
        if (methods == null) {
            return Collections.emptyList();
        }
        List<PaymentCheckoutSessionQueryResultDTO.PaymentMethodDTO> result = new ArrayList<>();
        for (PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO method : methods) {
            PaymentCheckoutSessionQueryResultDTO.PaymentMethodDTO dto = new PaymentCheckoutSessionQueryResultDTO.PaymentMethodDTO();
            dto.setPaymentMethod(method.getPaymentMethod());
            dto.setChannelCode(method.getChannelCode());
            dto.setBrands(method.getBrands());
            dto.setThreeDsMode(method.getThreeDsMode());
            result.add(dto);
        }
        return result;
    }

    /**
     * 从允许支付方式快照中确定渠道编码，当前默认 MPGS。
     */
    private String resolveChannelCode(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods) {
        return defaultAllowedPaymentMethods(methods).stream()
                .map(PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO::getChannelCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(CHANNEL_MPGS);
    }

    /**
     * 创建会话时提前拒绝 V1 尚不支持的支付方式组合。
     */
    private void validateAllowedPaymentMethods(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods) {
        for (PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO method : methods) {
            if (!PAYMENT_METHOD_BANK_CARD.equals(method.getPaymentMethod())
                    || !CHANNEL_MPGS.equals(method.getChannelCode())) {
                throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(),
                        "hosted checkout v1 only supports BANK_CARD + MPGS");
            }
        }
    }

    /**
     * 解析最大尝试次数，避免商户传入非法值导致无限重试。
     */
    private int resolveMaxAttemptCount(Integer input) {
        if (input == null || input <= 0) {
            return properties.getDefaultMaxAttemptCount();
        }
        return input;
    }

    /**
     * 解析会话过期时间，非法或过去时间按平台默认有效期处理。
     */
    private LocalDateTime resolveExpireTime(LocalDateTime input, LocalDateTime now) {
        if (input != null && input.isAfter(now)) {
            return input;
        }
        return now.plusMinutes(properties.getDefaultExpireMinutes());
    }

    /**
     * 计算剩余可付款次数，用于失败后是否展示重新支付入口。
     */
    private int remainingAttempts(PaymentCheckoutSessionDO sessionDO) {
        int maxAttemptCount = sessionDO.getMaxAttemptCount() == null ? properties.getDefaultMaxAttemptCount() : sessionDO.getMaxAttemptCount();
        int attemptCount = sessionDO.getAttemptCount() == null ? 0 : sessionDO.getAttemptCount();
        return maxAttemptCount - attemptCount;
    }

    /**
     * 生成会话内递增尝试序号，真实幂等仍由 attemptRequestId 唯一约束兜底。
     */
    private int nextAttemptNo(PaymentCheckoutSessionDO sessionDO) {
        Integer maxAttemptNo = attemptMapper.selectMaxAttemptNo(sessionDO.getCheckoutSessionId());
        return (maxAttemptNo == null ? 0 : maxAttemptNo) + 1;
    }

    /**
     * 填充可用于排查和风控的卡信息摘要，禁止保存完整卡号和 CVV。
     */
    private void fillMaskedCardInfo(PaymentCheckoutAttemptDO attemptDO,
                                    PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfoDTO) {
        if (cardInfoDTO == null || !StringUtils.hasText(cardInfoDTO.getCardNo())) {
            return;
        }
        String cardNo = cardInfoDTO.getCardNo().trim();
        attemptDO.setCardBin(cardNo.length() >= 6 ? cardNo.substring(0, 6) : cardNo);
        attemptDO.setCardLast4(cardNo.length() >= 4 ? cardNo.substring(cardNo.length() - 4) : null);
        attemptDO.setCardNumberMasked(SensitiveDataMaskUtils.maskPan(cardNo));
        attemptDO.setCardholderNameMasked(maskName(cardInfoDTO.getCardholderName()));
        attemptDO.setPaymentAccountHash(PaymentCheckoutTokenSupport.hmacSha256Hex(cardNo, properties.getTokenPepper()));
    }

    /**
     * 基于卡号前缀粗略识别卡组织，最终品牌以后续 BIN 或渠道结果为准。
     */
    private String resolveCardBrand(PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfoDTO) {
        if (cardInfoDTO == null || !StringUtils.hasText(cardInfoDTO.getCardNo())) {
            return null;
        }
        String cardNo = cardInfoDTO.getCardNo().trim();
        if (cardNo.startsWith("4")) {
            return "VISA";
        }
        if (cardNo.startsWith("34") || cardNo.startsWith("37")) {
            return "AMEX";
        }
        if (cardNo.startsWith("35")) {
            return "JCB";
        }
        if (cardNo.startsWith("5") || cardNo.startsWith("22")) {
            return "MASTERCARD";
        }
        return "UNKNOWN";
    }

    /**
     * 掩码持卡人姓名，仅用于收银台尝试记录。
     */
    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return "*";
        }
        return trimmed.charAt(0) + "***";
    }

    /**
     * 规范化币种编码，金额精度由上游币种字典解析。
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 对可选文本字段应用平台默认值。
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private record IssuedCheckoutToken(PaymentCheckoutTokenDO tokenDO, String opaqueToken) {
    }

    private record PaymentSubmissionContext(PaymentCheckoutSessionDO sessionDO,
                                            PaymentCheckoutAttemptDO attemptDO,
                                            String threeDsReturnUrl,
                                            boolean duplicate) {
    }
}

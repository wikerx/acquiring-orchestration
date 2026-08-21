package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.config.MerchantNotificationProperties;
import com.scott.payment.payment.domain.state.PaymentCheckoutAttemptStatusEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutEventResultEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutPageStateEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutSecurityDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutSessionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentCheckoutTokenStatusEnum;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutEventDO;
import com.scott.payment.payment.entity.PaymentCheckoutSecurityEventDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.entity.PaymentCheckoutTokenDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.mapper.PaymentCheckoutAttemptMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSecurityEventMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutTokenMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.security.PaymentCheckoutCardEnvelopeService;
import com.scott.payment.payment.security.PaymentCheckoutCardVaultPublisher;
import com.scott.payment.payment.service.PaymentCheckoutService;
import com.scott.payment.payment.service.PaymentAuthenticationRecordService;
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.support.PaymentCheckoutTokenSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentCheckoutService
 * @date : 2026-08-08 15:05
 * @email : scott_x@163.com
 * @description : 编排 Hosted Checkout 会话、支付尝试、3DS、交易提交及付款人状态查询，并保护令牌和卡数据安全边界
 * @status : create
 */
@Service
@Slf4j
public class DefaultPaymentCheckoutService implements PaymentCheckoutService {

    /** 当前收银台支持的银行卡支付方式编码。 */
    private static final String PAYMENT_METHOD_BANK_CARD = "BANK_CARD";
    /** 会话首次创建事件类型。 */
    private static final String EVENT_SESSION_CREATED = "SESSION_CREATED";
    /** 幂等创建请求重新签发访问令牌事件类型。 */
    private static final String EVENT_SESSION_REISSUED = "SESSION_TOKEN_REISSUED";
    /** 付款人打开会话事件类型。 */
    private static final String EVENT_SESSION_OPENED = "SESSION_OPENED";
    /** 付款人提交支付事件类型。 */
    private static final String EVENT_PAYMENT_SUBMITTED = "PAYMENT_SUBMITTED";
    /** 需要付款人完成 3DS 挑战事件类型。 */
    private static final String EVENT_THREE_DS_CHALLENGE_REQUIRED = "THREE_DS_CHALLENGE_REQUIRED";
    /** 3DS 服务端认证通过事件类型。 */
    private static final String EVENT_THREE_DS_AUTHENTICATED = "THREE_DS_AUTHENTICATED";
    /** 3DS 认证失败事件类型。 */
    private static final String EVENT_THREE_DS_FAILED = "THREE_DS_FAILED";
    /** 渠道提交前路由、策略或认证编排失败事件类型。 */
    private static final String EVENT_PRE_CHANNEL_FAILED = "PRE_CHANNEL_FAILED";
    /** 浏览器查询支付状态事件类型。 */
    private static final String EVENT_PAYMENT_STATUS_QUERIED = "PAYMENT_STATUS_QUERIED";
    /** 未在付款期限内提交支付的会话到期事件类型。 */
    private static final String EVENT_SESSION_EXPIRED = "SESSION_EXPIRED";
    /** 浏览器 3DS 回跳到达事件类型。 */
    private static final String EVENT_THREE_DS_RETURNED = "THREE_DS_RETURNED";
    /** 访问令牌不存在、撤销或格式非法的安全事件类型。 */
    private static final String SECURITY_INVALID_TOKEN = "INVALID_TOKEN";
    /** 访问令牌或会话已过期的安全事件类型。 */
    private static final String SECURITY_EXPIRED_TOKEN = "EXPIRED_TOKEN";
    /** 令牌、会话或尝试绑定关系不一致的安全事件类型。 */
    private static final String SECURITY_SESSION_MISMATCH = "SESSION_MISMATCH";
    /** 前端使用受控 HTML 桥接执行 3DS 的动作类型。 */
    private static final String THREE_DS_ACTION_HTML = "HTML";
    /** 3DS 明确失败的稳定原因码。 */
    private static final String FAILURE_THREE_DS_AUTHENTICATION_FAILED = "THREE_DS_AUTHENTICATION_FAILED";
    /** 3DS 浏览器阶段超过服务端截止时间的内部稳定原因码。 */
    private static final String FAILURE_THREE_DS_AUTHENTICATION_TIMEOUT = "THREE_DS_AUTHENTICATION_TIMEOUT";
    /** 3DS 浏览器阶段超时的内部说明，不直接向付款人暴露。 */
    private static final String MESSAGE_THREE_DS_AUTHENTICATION_TIMEOUT = "3DS authentication timed out";
    /** 渠道结果未确定的稳定原因码。 */
    private static final String FAILURE_CHANNEL_PROCESSING = "CHANNEL_PROCESSING";
    /** 支付渠道明确拒绝的稳定原因码。 */
    private static final String FAILURE_PAYMENT_DECLINED = "PAYMENT_DECLINED";
    /** 不暴露渠道原文或内部异常的付款人默认失败提示。 */
    private static final String DEFAULT_PAYER_FAILURE_MESSAGE = "Payment could not be completed. Please try another card or contact your bank.";
    /** 收银台超时通知任务编号前缀。 */
    private static final String MERCHANT_NOTIFICATION_PREFIX = "TMN";
    /** 支付业务默认时区。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /** 支付核心和风控共同识别的可信 Hosted Checkout 请求来源。 */
    private static final String REQUEST_SOURCE_HOSTED_CHECKOUT = "HOSTED_CHECKOUT";
    /** 单次 3DS 挑战最长等待时间，单位秒。 */
    private static final int THREE_DS_TIMEOUT_SECONDS = 600;
    /** 收银台首次支付和失败重试的统一提交窗口上限，固定为 24 小时。 */
    private static final int PAYMENT_SUBMISSION_DEADLINE_MINUTES = 24 * 60;
    /** 兼容历史非空列；支付资格判断不读取该值，实际边界由重试策略和会话有效期控制。 */
    private static final int LEGACY_UNBOUNDED_MAX_ATTEMPT_COUNT = Integer.MAX_VALUE;

    /** Hosted Checkout 会话数据库访问组件。 */
    private final PaymentCheckoutSessionMapper sessionMapper;
    /** 不透明访问令牌摘要数据库访问组件。 */
    private final PaymentCheckoutTokenMapper tokenMapper;
    /** 支付尝试数据库访问组件。 */
    private final PaymentCheckoutAttemptMapper attemptMapper;
    /** 会话与尝试审计事件数据库访问组件。 */
    private final PaymentCheckoutEventMapper eventMapper;
    /** 安全事件数据库访问组件。 */
    private final PaymentCheckoutSecurityEventMapper securityEventMapper;
    /** 会话、令牌、尝试和事件业务号生成器。 */
    private final GlobalIdGenerator globalIdGenerator;
    /** 会话有效期、卡数据安全和轮询等运行参数。 */
    private final PaymentCheckoutProperties properties;
    /** 3DS 路由与渠道认证服务。 */
    private final PaymentCheckoutThreeDsService threeDsService;
    /** 支付交易核心服务，负责数据库幂等和交易状态机。 */
    private final PaymentTransactionService paymentTransactionService;
    /** 3DS 认证阶段安全审计服务。 */
    private final PaymentAuthenticationRecordService authenticationRecordService;
    /** 划分本地提交事务与外部 3DS/渠道调用边界的事务执行器。 */
    private final TransactionOperations transactionOperations;
    /** 复用交易商户通知表，由 service-data 统一投递。 */
    private final TransactionMerchantNotificationMapper merchantNotificationMapper;
    /** 商户通知最大重试次数配置。 */
    private final MerchantNotificationProperties merchantNotificationProperties;
    /** 收银台超时通知首次五秒延时事件调度服务。 */
    private MerchantNotificationInitialDeliveryService merchantNotificationInitialDeliveryService;
    /** 商户 MID 能力聚合与 BIN 品牌解析服务。 */
    private PaymentCheckoutCardCapabilityService cardCapabilityService;
    /** 浏览器卡数据密文解密与一次性 nonce 防重放服务。 */
    private PaymentCheckoutCardEnvelopeService cardEnvelopeService;
    /** 无 CVV 卡资料可靠消息发布器，配置关闭时不写 Outbox。 */
    private PaymentCheckoutCardVaultPublisher cardVaultPublisher;

    /**
     * 创建 Hosted Checkout 默认服务。
     *
     * <p>数据库 Mapper 是会话、令牌、尝试和事件的事实来源；外部 3DS 与支付调用在本地提交事务外执行。</p>
     */
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
                                         PaymentAuthenticationRecordService authenticationRecordService,
                                         TransactionMerchantNotificationMapper merchantNotificationMapper,
                                         MerchantNotificationProperties merchantNotificationProperties,
                                         PaymentCheckoutCardCapabilityService cardCapabilityService,
                                         PaymentCheckoutCardEnvelopeService cardEnvelopeService,
                                         PaymentCheckoutCardVaultPublisher cardVaultPublisher,
                                         MerchantNotificationInitialDeliveryService merchantNotificationInitialDeliveryService,
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
        this.authenticationRecordService = authenticationRecordService;
        this.merchantNotificationMapper = merchantNotificationMapper;
        this.merchantNotificationProperties = merchantNotificationProperties;
        this.cardCapabilityService = cardCapabilityService;
        this.cardEnvelopeService = cardEnvelopeService;
        this.cardVaultPublisher = cardVaultPublisher;
        this.merchantNotificationInitialDeliveryService = merchantNotificationInitialDeliveryService;
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
        this(sessionMapper, tokenMapper, attemptMapper, eventMapper, securityEventMapper, globalIdGenerator,
                properties, threeDsService, paymentTransactionService, null,
                new MerchantNotificationProperties(), null, transactionOperations);
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
                                  TransactionMerchantNotificationMapper merchantNotificationMapper,
                                  MerchantNotificationProperties merchantNotificationProperties,
                                  TransactionOperations transactionOperations) {
        this(sessionMapper, tokenMapper, attemptMapper, eventMapper, securityEventMapper,
                globalIdGenerator, properties, threeDsService, paymentTransactionService,
                merchantNotificationMapper, merchantNotificationProperties, null, transactionOperations);
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
                                  TransactionMerchantNotificationMapper merchantNotificationMapper,
                                  MerchantNotificationProperties merchantNotificationProperties,
                                  PaymentAuthenticationRecordService authenticationRecordService,
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
        this.authenticationRecordService = authenticationRecordService;
        this.transactionOperations = transactionOperations;
        this.merchantNotificationMapper = merchantNotificationMapper;
        this.merchantNotificationProperties = merchantNotificationProperties;
    }

    /**
     * 创建或按商户请求号幂等返回 Hosted Checkout 会话。
     *
     * <p>相同请求号必须匹配原请求指纹；幂等命中会重新签发短期不透明令牌，
     * 数据库中只保存令牌摘要，绝不保存令牌明文。</p>
     *
     * @param commandDTO 会话创建命令
     * @return 新建或幂等命中的会话结果
     */
    @Override
    @DS(DataSourceName.MASTER)
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

    /**
     * 校验令牌摘要并返回会话展示快照。
     *
     * <p>令牌无效、过期或绑定会话不存在时记录脱敏安全事件并阻断；校验通过后原子记录使用次数。</p>
     *
     * @param commandDTO 会话查询命令
     * @return 可公开展示、过期或阻断页面结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
        sessionDO = expireSessionIfDue(sessionDO, now);
        tokenMapper.markUsed(commandDTO.getTokenHash(), commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), now);
        sessionMapper.markOpened(sessionDO.getCheckoutSessionId(), now);
        insertEvent(event(sessionDO, null, EVENT_SESSION_OPENED, PaymentCheckoutProcessStageEnum.WAITING_PAYER,
                PaymentCheckoutEventResultEnum.SUCCESS, commandDTO.getTraceId(), null, now));
        return sessionResult(sessionDO, now);
    }

    /**
     * 批量推进超过付款期限的未支付会话；CAS 使调度重入和多实例并发保持幂等。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int expireDue(LocalDateTime now, int limit) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        int effectiveLimit = Math.max(1, Math.min(limit, 1000));
        List<PaymentCheckoutSessionDO> candidates = sessionMapper.selectExpireDue(effectiveNow, effectiveLimit);
        int expired = 0;
        for (PaymentCheckoutSessionDO candidate : candidates) {
            if (markSessionExpired(candidate, effectiveNow)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * 根据当前会话的 MID 品牌快照解析 BIN，并返回品牌识别与支持状态。
     *
     * @param commandDTO 包含令牌摘要、会话编号和 6 至 11 位 BIN 的查询命令
     * @return 当前会话维度的卡品牌识别和支持结果
     * @throws ServiceException 令牌、会话或绑定关系无效时抛出
     */
    @Override
    public PaymentCheckoutCardBinResultDTO resolveCardBin(PaymentCheckoutCardBinCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutTokenDO tokenDO = validateTokenAndSession(commandDTO.getTokenHash(),
                commandDTO.getCheckoutSessionId(), now, commandDTO.getTraceId(),
                null, null, null, null, null);
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(tokenDO.getCheckoutSessionId());
        if (sessionDO == null) {
            throw new ServiceException(ApiResultEnum.QUERY_RESULT_NOT_FOUND);
        }
        String brand = cardCapabilityService == null
                ? PaymentCardBrandRuleMatcher.resolve(commandDTO.getCardBin())
                : cardCapabilityService.resolveCardBrand(commandDTO.getCardBin());
        PaymentCheckoutCardBinResultDTO resultDTO = new PaymentCheckoutCardBinResultDTO();
        resultDTO.setCardBrand(brand);
        resultDTO.setRecognized(!"UNKNOWN".equals(brand));
        resultDTO.setSupported(isCardBrandAllowed(sessionDO, brand));
        return resultDTO;
    }

    /**
     * 提交一次收银台付款尝试，事务内先锁定会话和尝试号，事务外执行路由后 3DS 策略与资金动作。
     *
     * @param commandDTO 付款人提交的卡信息、账单信息和 attemptRequestId
     * @return 收银台页面可直接渲染的下一步状态
     */
    @Override
    public PaymentCheckoutPaymentResultDTO submitPayment(PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        try {
            PaymentSubmissionContext context = transactionOperations.execute(status -> createPaymentSubmission(commandDTO));
            if (context == null) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout payment submission failed");
            }
            if (context.duplicate()) {
                return paymentResult(context.sessionDO(), context.attemptDO());
            }

            PaymentInitialPreparationResultDTO corePreparation;
            try {
                corePreparation = prepareCorePayment(context.sessionDO(), context.attemptDO(), commandDTO);
                if (corePreparation == null || corePreparation.getResultDTO() == null) {
                    throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "payment core preparation returned no transaction");
                }
            } catch (RuntimeException exception) {
                logPreChannelFailure(context, "CORE_PREPARE", exception);
                return applyPreChannelFailure(context);
            }
            if (corePreparation.isDuplicate() || !corePreparation.isCallChannel()) {
                return applyPaymentCoreResult(context.sessionDO(), context.attemptDO(), corePreparation.getResultDTO());
            }
            PaymentCheckoutAttemptDO preparedAttempt = markCorePrepared(
                    context.sessionDO(), context.attemptDO(), corePreparation);
            context = new PaymentSubmissionContext(context.sessionDO(), preparedAttempt,
                    context.threeDsReturnUrl(), context.threeDsReturnTokenHash(), false);

            String threeDsReturnToken = PaymentCheckoutTokenSupport.newUrlSafeToken(properties.getOpaqueTokenBytes());
            context = new PaymentSubmissionContext(context.sessionDO(), context.attemptDO(),
                    buildThreeDsReturnUrl(context.sessionDO(), context.attemptDO(), threeDsReturnToken),
                    PaymentCheckoutTokenSupport.hmacSha256Hex(threeDsReturnToken, properties.getTokenPepper()),
                    false);
            PaymentCheckoutThreeDsResultDTO threeDsResult;
            try {
                threeDsResult = safeThreeDsResult(threeDsService.authenticate(
                        context.sessionDO(), context.attemptDO(), commandDTO, context.threeDsReturnUrl(),
                        corePreparation.getRouteResultDTO()));
            } catch (RuntimeException exception) {
                logPreChannelFailure(context, "THREE_DS_PREPARE", exception);
                return applyPreChannelFailure(context);
            }
            if (threeDsResult.challengeRequired() || threeDsResult.methodRequired()) {
                return applyThreeDsChallengeRequired(context, threeDsResult);
            }
            if (threeDsResult.failed()) {
                return applyThreeDsFailed(context, threeDsResult);
            }
            if (threeDsResult.processing() || (!threeDsResult.notRequired() && !threeDsResult.passed())) {
                return applyThreeDsProcessing(context, threeDsResult);
            }
            PaymentCheckoutAttemptDO fundsReadyAttempt = context.attemptDO();
            if (threeDsResult.passed()) {
                fundsReadyAttempt = applyThreeDsPassed(context, threeDsResult);
            }
            PaymentCheckoutAttemptDO submittedAttempt = markChannelSubmission(fundsReadyAttempt);
            PaymentCreateResultDTO paymentResultDTO = submitPreparedCorePayment(
                    corePreparation, submittedAttempt, commandDTO, threeDsResult);
            return applyPaymentCoreResult(context.sessionDO(), submittedAttempt, paymentResultDTO);
        } finally {
            commandDTO.setCardInfo(null);
        }
    }

    /**
     * 查询最新付款状态；处理中页面轮询只允许收敛已超过服务端截止时间的前置 3DS 状态，不能推进资金交易。
     *
     * @param commandDTO token 摘要、会话号和可选尝试号
     * @return 当前尝试对应的结果页视图
     */
    @Override
    public PaymentCheckoutPaymentResultDTO queryPaymentStatus(PaymentCheckoutPaymentStatusCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutTokenDO tokenDO = validateTokenAndSession(commandDTO.getTokenHash(), commandDTO.getCheckoutSessionId(), now,
                commandDTO.getTraceId(), commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), null, null, null);
        PaymentCheckoutSessionDO loadedSession = sessionMapper.selectByCheckoutSessionId(tokenDO.getCheckoutSessionId());
        PaymentCheckoutAttemptDO resolvedAttempt = resolveAttempt(loadedSession, commandDTO.getCheckoutAttemptId());
        PaymentStatusContext statusContext = transactionOperations.execute(
                status -> convergeTimedOutThreeDs(loadedSession, resolvedAttempt, now));
        PaymentCheckoutSessionDO sessionDO = statusContext == null ? loadedSession : statusContext.sessionDO();
        PaymentCheckoutAttemptDO attemptDO = statusContext == null ? resolvedAttempt : statusContext.attemptDO();
        if (statusContext != null && statusContext.coreConvergenceRequired()) {
            recordThreeDsTimeout(attemptDO);
            failPreparedCoreTransaction(attemptDO, FAILURE_THREE_DS_AUTHENTICATION_TIMEOUT,
                    MESSAGE_THREE_DS_AUTHENTICATION_TIMEOUT);
        }
        insertEvent(event(sessionDO, attemptDO, EVENT_PAYMENT_STATUS_QUERIED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.SUCCESS,
                commandDTO.getTraceId(), null, now));
        return paymentResult(sessionDO, attemptDO);
    }

    /** 轮询仅把超过截止时间的前置 3DS 状态收敛为失败，不查询或创建支付核心交易。 */
    private PaymentStatusContext convergeTimedOutThreeDs(PaymentCheckoutSessionDO sessionDO,
                                                         PaymentCheckoutAttemptDO attemptDO,
                                                         LocalDateTime now) {
        if (!isThreeDsTimedOut(attemptDO, now)) {
            return new PaymentStatusContext(sessionDO, attemptDO, requiresCoreTimeoutConvergence(attemptDO));
        }
        int updated = attemptMapper.markThreeDsTimedOutCas(
                attemptDO.getCheckoutAttemptId(),
                FAILURE_THREE_DS_AUTHENTICATION_TIMEOUT,
                MESSAGE_THREE_DS_AUTHENTICATION_TIMEOUT,
                FAILURE_THREE_DS_AUTHENTICATION_FAILED,
                MESSAGE_THREE_DS_AUTHENTICATION_TIMEOUT,
                DEFAULT_PAYER_FAILURE_MESSAGE,
                paymentSnapshot(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE, null),
                now.minusSeconds(THREE_DS_TIMEOUT_SECONDS),
                attemptDO.getVersion(),
                now);
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(
                attemptDO.getCheckoutAttemptId());
        if (updated != 1) {
            PaymentCheckoutSessionDO latestSession = sessionMapper.selectByCheckoutSessionId(
                    sessionDO.getCheckoutSessionId());
            return new PaymentStatusContext(
                    latestSession == null ? sessionDO : latestSession,
                    latestAttempt == null ? attemptDO : latestAttempt,
                    requiresCoreTimeoutConvergence(latestAttempt));
        }
        latestAttempt = latestAttempt == null ? attemptDO : latestAttempt;
        PaymentCheckoutSessionDO latestSession = failSession(sessionDO, latestAttempt, now);
        insertEvent(event(latestSession, latestAttempt, EVENT_THREE_DS_FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.FAILED,
                null, latestAttempt.getAttemptRequestId(), now));
        return new PaymentStatusContext(latestSession, latestAttempt, true);
    }

    /** 已由 checkout 超时 CAS 标记的尝试需要在主库事务提交后幂等收敛交易核心。 */
    private boolean requiresCoreTimeoutConvergence(PaymentCheckoutAttemptDO attemptDO) {
        return attemptDO != null
                && FAILURE_THREE_DS_AUTHENTICATION_TIMEOUT.equals(attemptDO.getChannelResponseCode())
                && PaymentCheckoutAttemptStatusEnum.FAILED.getCode().equals(attemptDO.getAttemptStatus());
    }

    /** 超时 CAS 成功后补记认证失败摘要；审计异常不反向阻断核心交易失败收敛。 */
    private void recordThreeDsTimeout(PaymentCheckoutAttemptDO attemptDO) {
        if (authenticationRecordService == null) {
            return;
        }
        try {
            authenticationRecordService.recordTimeout(attemptDO);
        } catch (RuntimeException exception) {
            log.warn("event: THREE_DS_AUDIT_WRITE_FAILED transactionId: {} phase: TIMEOUT errorType: {}",
                    attemptDO == null ? null : attemptDO.getTransactionId(),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 处理 3DS bridge 回跳，只把尝试从 WAITING_3DS 推进到等待渠道结果。
     *
     * @param commandDTO 已由 OpenAPI 层转换为摘要的 3DS return 命令
     * @return 回跳后的处理中、拦截或既有终态结果
     */
    @Override
    public PaymentCheckoutPaymentResultDTO handleThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        try {
            ThreeDsReturnContext returnContext = transactionOperations.execute(status -> acceptThreeDsReturn(commandDTO));
            if (returnContext == null) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout 3DS return failed");
            }
            if (returnContext.immediateResult() != null) {
                return returnContext.immediateResult();
            }
            if (isThreeDsTimedOut(returnContext.attemptDO(), LocalDateTime.now())) {
                PaymentSubmissionContext timeoutContext = new PaymentSubmissionContext(
                        returnContext.sessionDO(), returnContext.attemptDO(), null, null, false);
                return applyThreeDsFailed(timeoutContext, timedOutThreeDsResult(returnContext.attemptDO()));
            }
            decryptThreeDsReturnCardData(commandDTO, returnContext.attemptDO());
            PaymentCheckoutPaymentSubmitCommandDTO continuationCommand = toContinuationCommand(commandDTO);
            boolean channelSubmissionAlreadyMarked = PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED.getCode()
                    .equals(returnContext.attemptDO().getAttemptStatus());
            boolean authenticationAlreadyPassed = channelSubmissionAlreadyMarked
                    || PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED.getCode()
                    .equals(returnContext.attemptDO().getAttemptStatus());
            ThreeDsBrowserContext browserContext = authenticationAlreadyPassed
                    ? new ThreeDsBrowserContext(null, null)
                    : newThreeDsBrowserContext(returnContext.sessionDO(), returnContext.attemptDO());
            PaymentCheckoutThreeDsResultDTO threeDsResult;
            if (authenticationAlreadyPassed) {
                threeDsResult = restorePassedThreeDsResult(returnContext.attemptDO());
            } else {
                com.scott.payment.channel.payment.enums.ChannelThreeDsPhase phase = continuationPhase(returnContext.attemptDO());
                threeDsResult = safeThreeDsResult(threeDsService.continueAuthentication(
                        returnContext.sessionDO(), returnContext.attemptDO(), continuationCommand,
                        browserContext.returnUrl(), phase));
            }
            PaymentSubmissionContext submissionContext = new PaymentSubmissionContext(
                    returnContext.sessionDO(), returnContext.attemptDO(), browserContext.returnUrl(),
                    browserContext.returnTokenHash(), false);
            if (threeDsResult.challengeRequired() || threeDsResult.methodRequired()) {
                return applyThreeDsChallengeRequired(submissionContext, threeDsResult);
            }
            if (threeDsResult.failed()) {
                return applyThreeDsFailed(submissionContext, threeDsResult);
            }
            if (!threeDsResult.passed()) {
                return applyThreeDsProcessing(submissionContext, threeDsResult);
            }
            PaymentCheckoutAttemptDO passedAttempt = authenticationAlreadyPassed
                    ? returnContext.attemptDO()
                    : applyThreeDsPassed(submissionContext, threeDsResult);
            PaymentCheckoutAttemptDO submittedAttempt = channelSubmissionAlreadyMarked
                    ? passedAttempt
                    : markChannelSubmission(passedAttempt);
            PaymentCreateResultDTO paymentResultDTO = resumePreparedCorePayment(
                    returnContext.sessionDO(), submittedAttempt, continuationCommand, threeDsResult);
            return applyPaymentCoreResult(returnContext.sessionDO(), submittedAttempt, paymentResultDTO);
        } finally {
            commandDTO.setCardInfo(null);
        }
    }

    /** 在短事务内校验一次性回跳令牌并消费 THREE_DS_REQUIRED 状态。 */
    private ThreeDsReturnContext acceptThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        LocalDateTime now = LocalDateTime.now();
        PaymentCheckoutAttemptDO attemptDO = attemptMapper.selectByCheckoutAttemptId(commandDTO.getCheckoutAttemptId());
        if (attemptDO == null
                || !Objects.equals(commandDTO.getCheckoutSessionId(), attemptDO.getCheckoutSessionId())
                || !Objects.equals(commandDTO.getThreeDsReturnTokenHash(), attemptDO.getThreeDsReturnTokenHash())) {
            recordSecurityEvent(null, commandDTO.getCheckoutSessionId(), commandDTO.getCheckoutAttemptId(),
                    SECURITY_SESSION_MISMATCH, PaymentCheckoutSecurityDecisionEnum.BLOCK, commandDTO.getTraceId(),
                    commandDTO.getClientIpHash(), commandDTO.getUserAgentHash(), null, null, null, now);
            return new ThreeDsReturnContext(null, null,
                    blockedPaymentResult(commandDTO.getCheckoutSessionId(), commandDTO.getCheckoutAttemptId()));
        }
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(attemptDO.getCheckoutSessionId());
        if (PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED.getCode().equals(attemptDO.getAttemptStatus())) {
            return new ThreeDsReturnContext(sessionDO, attemptDO, paymentResult(sessionDO, attemptDO));
        }
        if (PaymentCheckoutAttemptStatusEnum.THREE_DS_PASSED.getCode().equals(attemptDO.getAttemptStatus())
                || PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED.getCode().equals(attemptDO.getAttemptStatus())) {
            return new ThreeDsReturnContext(sessionDO, attemptDO, null);
        }
        if (!PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode().equals(attemptDO.getAttemptStatus())) {
            return new ThreeDsReturnContext(sessionDO, attemptDO, paymentResult(sessionDO, attemptDO));
        }
        PaymentCheckoutProcessStageEnum nextStage = "METHOD_REQUIRED".equals(attemptDO.getThreeDsStatus())
                ? PaymentCheckoutProcessStageEnum.AUTHENTICATE_PAYER
                : PaymentCheckoutProcessStageEnum.WAITING_CHANNEL;
        int updated = attemptMapper.markThreeDsReturnedCas(attemptDO.getCheckoutAttemptId(),
                nextStage.getCode(), attemptDO.getVersion(), now);
        if (updated != 1) {
            PaymentCheckoutAttemptDO latest = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
            return new ThreeDsReturnContext(sessionDO, latest,
                    paymentResult(sessionDO, latest == null ? attemptDO : latest));
        }
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        latestAttempt = latestAttempt == null ? attemptDO : latestAttempt;
        insertEvent(event(sessionDO, latestAttempt, EVENT_THREE_DS_RETURNED,
                nextStage, PaymentCheckoutEventResultEnum.SUCCESS, commandDTO.getTraceId(), null, now));
        return new ThreeDsReturnContext(sessionDO, latestAttempt, null);
    }

    /** 仅对尚未认证通过或提交渠道的 3DS 前置阶段执行服务端超时判定。 */
    private boolean isThreeDsTimedOut(PaymentCheckoutAttemptDO attemptDO, LocalDateTime now) {
        if (attemptDO == null || attemptDO.getChannelSubmitTime() != null) {
            return false;
        }
        String status = attemptDO.getAttemptStatus();
        boolean explicitThreeDsState = PaymentCheckoutAttemptStatusEnum.THREE_DS_INITIATED.getCode().equals(status)
                || PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode().equals(status)
                || PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED.getCode().equals(status);
        boolean processingThreeDs = PaymentCheckoutAttemptStatusEnum.PROCESSING.getCode().equals(status)
                && Integer.valueOf(1).equals(attemptDO.getThreeDsRequired());
        if (!explicitThreeDsState && !processingThreeDs) {
            return false;
        }
        LocalDateTime startedAt = attemptDO.getAuthenticationStartTime();
        if (startedAt == null) {
            startedAt = attemptDO.getSubmitTime();
        }
        if (startedAt == null) {
            startedAt = attemptDO.getCreateTime();
        }
        return startedAt != null && !now.isBefore(startedAt.plusSeconds(THREE_DS_TIMEOUT_SECONDS));
    }

    /** 构造超时失败结果并保留已持久化的渠道与 3DS 审计标识。 */
    private PaymentCheckoutThreeDsResultDTO timedOutThreeDsResult(PaymentCheckoutAttemptDO attemptDO) {
        PaymentCheckoutThreeDsResultDTO result = new PaymentCheckoutThreeDsResultDTO();
        result.setStatus("FAILED");
        result.setFailureCode(FAILURE_THREE_DS_AUTHENTICATION_TIMEOUT);
        result.setFailureMessage(MESSAGE_THREE_DS_AUTHENTICATION_TIMEOUT);
        result.setChannelCode(attemptDO.getChannelCode());
        result.setChannelMidConfigId(attemptDO.getChannelMidConfigId());
        result.setChannelOrderNo(attemptDO.getChannelOrderNo());
        result.setAuthenticationTransactionId(attemptDO.getThreeDsTransactionId());
        result.setThreeDsVersion(attemptDO.getThreeDsVersion());
        result.setThreeDsServerTransactionId(attemptDO.getThreeDsServerTransactionId());
        result.setAcsTransactionId(attemptDO.getAcsTransactionId());
        result.setDsTransactionId(attemptDO.getDsTransactionId());
        result.setEci(attemptDO.getEci());
        return result;
    }

    /** 回跳后的资金动作仍需要新 nonce 对应的卡数据，服务端绝不从数据库恢复 PAN/CVV。 */
    private void decryptThreeDsReturnCardData(PaymentCheckoutThreeDsReturnCommandDTO commandDTO,
                                              PaymentCheckoutAttemptDO attemptDO) {
        if (commandDTO.getCardInfo() != null) {
            return;
        }
        if (cardEnvelopeService == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout card envelope service is unavailable");
        }
        commandDTO.setCardInfo(cardEnvelopeService.decryptAndConsume(commandDTO.getCardDataEnvelope(),
                attemptDO.getCheckoutSessionId(), attemptDO.getAttemptRequestId()));
    }

    /** 将浏览器续传命令转换为统一 3DS/支付内存命令。 */
    private PaymentCheckoutPaymentSubmitCommandDTO toContinuationCommand(PaymentCheckoutThreeDsReturnCommandDTO source) {
        PaymentCheckoutPaymentSubmitCommandDTO target = new PaymentCheckoutPaymentSubmitCommandDTO();
        target.setCardInfo(source.getCardInfo());
        target.setBillingCardHolderInfo(source.getBillingCardHolderInfo());
        target.setBrowserInfoJson(source.getBrowserInfoJson());
        target.setClientIpHash(source.getClientIpHash());
        target.setPayerIp(source.getPayerIp());
        target.setUserAgentHash(source.getUserAgentHash());
        return target;
    }

    /** 从已 CAS 持久化的认证通过事实恢复资金提交所需的最小 3DS 摘要。 */
    private PaymentCheckoutThreeDsResultDTO restorePassedThreeDsResult(PaymentCheckoutAttemptDO attemptDO) {
        PaymentCheckoutThreeDsResultDTO result = new PaymentCheckoutThreeDsResultDTO();
        result.setStatus("PASSED");
        result.setAuthenticationTransactionId(attemptDO.getThreeDsTransactionId());
        result.setChannelCode(attemptDO.getChannelCode());
        result.setChannelMidConfigId(attemptDO.getChannelMidConfigId());
        result.setChannelOrderNo(attemptDO.getChannelOrderNo());
        result.setThreeDsPolicyAction("FORCE_3DS");
        result.setThreeDsStatus(attemptDO.getThreeDsStatus());
        result.setThreeDsVersion(attemptDO.getThreeDsVersion());
        result.setThreeDsTransactionId(attemptDO.getThreeDsTransactionId());
        result.setThreeDsServerTransactionId(attemptDO.getThreeDsServerTransactionId());
        result.setAcsTransactionId(attemptDO.getAcsTransactionId());
        result.setDsTransactionId(attemptDO.getDsTransactionId());
        result.setEci(attemptDO.getEci());
        return result;
    }

    /** Method 返回后继续 AUTHENTICATE；Challenge 返回后只允许服务端 VERIFY。 */
    private com.scott.payment.channel.payment.enums.ChannelThreeDsPhase continuationPhase(
            PaymentCheckoutAttemptDO attemptDO) {
        if ("METHOD_REQUIRED".equals(attemptDO.getThreeDsStatus())) {
            return com.scott.payment.channel.payment.enums.ChannelThreeDsPhase.AUTHENTICATE;
        }
        if ("CHALLENGE_REQUIRED".equals(attemptDO.getThreeDsStatus())) {
            return com.scott.payment.channel.payment.enums.ChannelThreeDsPhase.VERIFY;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "checkout 3DS phase cannot continue");
    }

    /** 为下一个浏览器阶段签发独立的一次性回跳令牌。 */
    private ThreeDsBrowserContext newThreeDsBrowserContext(PaymentCheckoutSessionDO sessionDO,
                                                           PaymentCheckoutAttemptDO attemptDO) {
        String token = PaymentCheckoutTokenSupport.newUrlSafeToken(properties.getOpaqueTokenBytes());
        return new ThreeDsBrowserContext(buildThreeDsReturnUrl(sessionDO, attemptDO, token),
                PaymentCheckoutTokenSupport.hmacSha256Hex(token, properties.getTokenPepper()));
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
        sessionDO.setCheckoutStatus(PaymentCheckoutSessionStatusEnum.PENDING.getCode());
        sessionDO.setProcessStage(PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode());
        sessionDO.setLastStatusTime(now);
        sessionDO.setTransactionDateTime(now);
        sessionDO.setLabelCurrency(normalizeCurrency(commandDTO.getCurrency()));
        sessionDO.setLabelAmount(commandDTO.getAmount());
        sessionDO.setCurrencyExponent(commandDTO.getCurrencyExponent());
        sessionDO.setOrderSubject(commandDTO.getOrderSubject());
        sessionDO.setOrderDescription(commandDTO.getOrderDescription());
        sessionDO.setOrderItemsJson(commandDTO.getOrderItemsJson());
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> allowedMethods = cardCapabilityService == null
                ? defaultAllowedPaymentMethods(commandDTO.getAllowedPaymentMethods())
                : cardCapabilityService.resolveAllowedMethods(commandDTO.getMerchantId(), commandDTO.getAllowedPaymentMethods());
        sessionDO.setAllowedPaymentMethodsJson(JsonUtils.toJsonString(allowedMethods));
        sessionDO.setSelectedPaymentMethod(PAYMENT_METHOD_BANK_CARD);
        sessionDO.setSelectedPaymentBrand(null);
        sessionDO.setChannelCode(resolveChannelCode(allowedMethods));
        sessionDO.setMerchantDisplayName(commandDTO.getMerchantDisplayName());
        sessionDO.setMerchantLogoUrl(commandDTO.getMerchantLogoUrl());
        sessionDO.setMerchantNotifyUrl(commandDTO.getMerchantNotifyUrl());
        sessionDO.setSubMerchantInfoJson(commandDTO.getSubMerchantInfoJson());
        sessionDO.setPayerInfoJson(commandDTO.getPayerInfoJson());
        sessionDO.setBillingInfoJson(commandDTO.getBillingInfoJson());
        sessionDO.setShippingInfoJson(commandDTO.getShippingInfoJson());
        sessionDO.setRedirectUrl(commandDTO.getRedirectUrl());
        sessionDO.setLocale(defaultIfBlank(commandDTO.getLocale(), "en-US"));
        sessionDO.setPayerCountry(commandDTO.getPayerCountry());
        sessionDO.setPayerEmail(commandDTO.getPayerEmail());
        sessionDO.setPayerEmailHash(commandDTO.getPayerEmailHash());
        sessionDO.setRetryAllowed(commandDTO.getRetryAllowed() == null ? 1 : commandDTO.getRetryAllowed());
        sessionDO.setMaxAttemptCount(LEGACY_UNBOUNDED_MAX_ATTEMPT_COUNT);
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
        tokenDO.setExpireTime(null);
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
        attemptDO.setChannelCode(sessionDO.getChannelCode());
        fillMaskedCardInfo(attemptDO, commandDTO.getCardInfo());
        attemptDO.setThreeDsRequired(0);
        attemptDO.setBrowserInfoJson(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getBrowserInfoJson()));
        attemptDO.setDeviceInfoJson(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getDeviceInfoJson()));
        attemptDO.setSubmitTime(now);
        attemptDO.setResultSnapshot("{\"pageState\":\"PROCESSING\"}");
        attemptDO.setVersion(0);
        attemptDO.setDeleted(0);
        attemptDO.setCreateTime(now);
        attemptDO.setUpdateTime(now);
        return attemptDO;
    }

    /**
     * 在本地事务内创建付款尝试并 CAS 标记会话为 PROCESSING，避免重复点击生成多笔资金交易。
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
            return new PaymentSubmissionContext(sessionDO, existedAttempt, null, null, true);
        }

        ensureSessionPayable(sessionDO, now);
        decryptCardDataIfRequired(commandDTO, sessionDO);
        ensurePaymentMethodAllowed(sessionDO, commandDTO);
        PaymentCheckoutAttemptDO attemptDO = buildAttempt(sessionDO, commandDTO, now);
        attemptMapper.insert(attemptDO);
        int updated = sessionMapper.markSubmittedCas(sessionDO.getCheckoutSessionId(),
                PaymentCheckoutSessionStatusEnum.PROCESSING.getCode(),
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
        if (cardVaultPublisher != null) {
            cardVaultPublisher.publishIfEnabled(
                    latestSession == null ? sessionDO : latestSession, attemptDO, commandDTO.getCardInfo());
        }
        return new PaymentSubmissionContext(latestSession == null ? sessionDO : latestSession,
                attemptDO, null, null, false);
    }

    /** 重复 attempt 已在上方直接返回；新 attempt 必须先原子消费 nonce，再在当前调用栈解密卡数据。 */
    private void decryptCardDataIfRequired(PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                           PaymentCheckoutSessionDO sessionDO) {
        if (commandDTO.getCardInfo() != null) {
            return;
        }
        if (cardEnvelopeService == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout card envelope service is unavailable");
        }
        commandDTO.setCardInfo(cardEnvelopeService.decryptAndConsume(
                commandDTO.getCardDataEnvelope(),
                sessionDO.getCheckoutSessionId(),
                commandDTO.getAttemptRequestId()));
    }

    /**
     * 在调用支付核心前记录一步式支付已提交事实，避免渠道异常时收银台仍停留在卡信息阶段。
     */
    private PaymentCheckoutAttemptDO markChannelSubmission(PaymentCheckoutAttemptDO attemptDO) {
        LocalDateTime now = LocalDateTime.now();
        int updated = attemptMapper.markChannelSubmittedCas(attemptDO.getCheckoutAttemptId(),
                PaymentCheckoutAttemptStatusEnum.CHANNEL_SUBMITTED.getCode(),
                PaymentCheckoutProcessStageEnum.SUBMIT_CHANNEL.getCode(),
                attemptDO.getVersion(),
                now);
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout payment attempt status changed, please retry");
        }
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        return latestAttempt == null ? attemptDO : latestAttempt;
    }

    /** 先提交核心交易事实，付款人点击支付后即拥有可查询的 PROCESSING/FAILED 交易。 */
    private PaymentInitialPreparationResultDTO prepareCorePayment(PaymentCheckoutSessionDO sessionDO,
                                                                  PaymentCheckoutAttemptDO attemptDO,
                                                                  PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        PaymentCreateCommandDTO createCommand = corePaymentCommand(sessionDO, attemptDO, commandDTO, null);
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(normalizePaymentAction(sessionDO.getPaymentAction()))) {
            return paymentTransactionService.prepareAuthorization(createCommand);
        }
        return paymentTransactionService.preparePayment(createCommand);
    }

    /** 把核心生成的权威路由和渠道请求身份同步到收银台尝试，供 3DS 和回跳恢复。 */
    private PaymentCheckoutAttemptDO markCorePrepared(PaymentCheckoutSessionDO sessionDO,
                                                      PaymentCheckoutAttemptDO attemptDO,
                                                      PaymentInitialPreparationResultDTO preparation) {
        String authoritativeOperationId = preparation.getResultDTO().getOperationId();
        String authoritativeTransactionId = preparation.getResultDTO().getTransactionId();
        LocalDateTime authoritativeTransactionTime = preparation.getCommandDTO().getTransactionDateTime();
        String authoritativeChannelCode = preparation.getRouteResultDTO().getChannelCode();
        Long authoritativeMidConfigId = preparation.getRouteResultDTO().getMidConfigId();
        LocalDateTime now = LocalDateTime.now();
        int updated = attemptMapper.markCorePreparedCas(
                attemptDO.getCheckoutAttemptId(),
                authoritativeOperationId,
                authoritativeTransactionId,
                authoritativeTransactionTime,
                authoritativeChannelCode,
                authoritativeMidConfigId,
                preparation.getPreparedChannelRequestDTO().getChannelOrderNo(),
                preparation.getPreparedChannelRequestDTO().getChannelTransactionId(),
                preparation.getPreparedChannelRequestDTO().getRequestId(),
                preparation.getCommandDTO().getTransactionCurrency(),
                preparation.getCommandDTO().getTransactionAmount(),
                attemptDO.getVersion(),
                now);
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout core preparation state changed");
        }
        int sessionUpdated = sessionMapper.syncPreparedIdentityCas(
                sessionDO.getCheckoutSessionId(),
                attemptDO.getCheckoutAttemptId(),
                attemptDO.getTransactionId(),
                attemptDO.getOperationId(),
                authoritativeTransactionId,
                authoritativeOperationId,
                authoritativeTransactionTime,
                authoritativeChannelCode,
                authoritativeMidConfigId,
                now);
        if (sessionUpdated != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout session core identity changed");
        }
        PaymentCheckoutAttemptDO latest = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        if (latest != null) {
            return latest;
        }
        attemptDO.setOperationId(authoritativeOperationId);
        attemptDO.setTransactionId(authoritativeTransactionId);
        attemptDO.setTransactionDateTime(authoritativeTransactionTime);
        attemptDO.setChannelCode(authoritativeChannelCode);
        attemptDO.setChannelMidConfigId(authoritativeMidConfigId);
        attemptDO.setChannelOrderNo(preparation.getPreparedChannelRequestDTO().getChannelOrderNo());
        attemptDO.setChannelTransactionId(preparation.getPreparedChannelRequestDTO().getChannelTransactionId());
        attemptDO.setChannelRequestId(preparation.getPreparedChannelRequestDTO().getRequestId());
        attemptDO.setChannelRequestCurrency(preparation.getCommandDTO().getTransactionCurrency());
        attemptDO.setChannelRequestAmount(preparation.getCommandDTO().getTransactionAmount());
        return attemptDO;
    }

    /** 使用内存中的已准备上下文提交资金渠道，并附加服务端确认的 3DS 结果。 */
    private PaymentCreateResultDTO submitPreparedCorePayment(PaymentInitialPreparationResultDTO preparation,
                                                             PaymentCheckoutAttemptDO attemptDO,
                                                             PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                             PaymentCheckoutThreeDsResultDTO threeDsResult) {
        PaymentCreateCommandDTO preparedCommand = preparation.getCommandDTO();
        PaymentCreateCommandDTO enriched = corePaymentCommand(null, attemptDO, commandDTO, threeDsResult);
        preparedCommand.setCardInfo(enriched.getCardInfo());
        preparedCommand.setBillingCardHolderInfo(enriched.getBillingCardHolderInfo());
        preparedCommand.setThreeDsInfo(enriched.getThreeDsInfo());
        preparedCommand.setThreeDsRequired(enriched.getThreeDsRequired());
        preparedCommand.setChannelIdentity(preparedFundsIdentity(preparation, enriched.getChannelIdentity()));
        return paymentTransactionService.submitPreparedTransaction(preparation);
    }

    /** 初次资金提交只使用核心准备阶段生成的订单号和交易号，3DS 响应不得改写资金身份。 */
    private PaymentCreateCommandDTO.ChannelIdentityDTO preparedFundsIdentity(
            PaymentInitialPreparationResultDTO preparation,
            PaymentCreateCommandDTO.ChannelIdentityDTO fallback) {
        PaymentCreateCommandDTO.ChannelIdentityDTO target = fallback == null
                ? new PaymentCreateCommandDTO.ChannelIdentityDTO() : fallback;
        if (preparation.getRouteResultDTO() != null) {
            target.setChannelCode(preparation.getRouteResultDTO().getChannelCode());
            target.setChannelId(preparation.getRouteResultDTO().getChannelId());
            target.setChannelMidConfigId(preparation.getRouteResultDTO().getMidConfigId());
        }
        if (preparation.getPreparedChannelRequestDTO() != null) {
            target.setChannelOrderNo(preparation.getPreparedChannelRequestDTO().getChannelOrderNo());
            target.setChannelTransactionId(preparation.getPreparedChannelRequestDTO().getChannelTransactionId());
        }
        return target;
    }

    /** 3DS 浏览器回跳跨请求后，从核心数据库恢复同一笔交易并提交资金渠道。 */
    private PaymentCreateResultDTO resumePreparedCorePayment(PaymentCheckoutSessionDO sessionDO,
                                                             PaymentCheckoutAttemptDO attemptDO,
                                                             PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                             PaymentCheckoutThreeDsResultDTO threeDsResult) {
        return paymentTransactionService.resumePreparedTransaction(
                corePaymentCommand(sessionDO, attemptDO, commandDTO, threeDsResult));
    }

    /** 将 3DS 明确失败或超时写入同一笔核心交易；并发资金提交已抢占时不会覆盖其结果。 */
    private void failPreparedCoreTransaction(PaymentCheckoutAttemptDO attemptDO,
                                             String failureCode,
                                             String failureMessage) {
        if (attemptDO == null || !StringUtils.hasText(attemptDO.getTransactionId())
                || attemptDO.getTransactionDateTime() == null) {
            return;
        }
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setTransactionId(attemptDO.getTransactionId());
        command.setTransactionDateTime(attemptDO.getTransactionDateTime());
        paymentTransactionService.failPreparedTransaction(command, failureCode, failureMessage);
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
                threeDsResult.getStatus(),
                context.threeDsReturnTokenHash(),
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
        resultDTO.setThreeDsAction(threeDsAction(
                threeDsResult.getPhase(), threeDsResult.getRedirectHtml(), context.threeDsReturnUrl()));
        if (cardEnvelopeService != null) {
            resultDTO.getThreeDsAction().setCardEncryption(
                    cardEnvelopeService.issue(context.sessionDO().getCheckoutSessionId()));
        }
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
        failPreparedCoreTransaction(latestAttempt, FAILURE_THREE_DS_AUTHENTICATION_FAILED,
                firstText(threeDsResult.getFailureMessage(), DEFAULT_PAYER_FAILURE_MESSAGE));
        latestAttempt = markAttemptResult(latestAttempt,
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                threeDsResult.getStatus(),
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
     * 收敛渠道提交前的路由、策略或 3DS 编排异常，避免付款尝试永久停留在 CARD_SUBMITTED。
     */
    private PaymentCheckoutPaymentResultDTO applyPreChannelFailure(PaymentSubmissionContext context) {
        LocalDateTime now = LocalDateTime.now();
        failPreparedCoreTransaction(context.attemptDO(), PaymentFailureReasonEnum.ROUTE_FAILED.getCode(),
                "checkout routing or 3DS preparation failed");
        PaymentCheckoutAttemptDO latestAttempt = markAttemptResult(context.attemptDO(),
                PaymentCheckoutAttemptStatusEnum.FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED,
                null,
                null,
                null,
                PaymentFailureReasonEnum.ROUTE_FAILED.getCode(),
                null,
                DEFAULT_PAYER_FAILURE_MESSAGE,
                paymentSnapshot(PaymentCheckoutPageStateEnum.FAILED_RETRYABLE, null),
                now);
        PaymentCheckoutSessionDO sessionDO = failSession(context.sessionDO(), latestAttempt, now);
        insertEvent(event(sessionDO, latestAttempt, EVENT_PRE_CHANNEL_FAILED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.FAILED,
                null, context.attemptDO().getAttemptRequestId(), now));
        return paymentResult(sessionDO, latestAttempt);
    }

    /** 记录渠道提交前失败的内部原因，敏感支付资料不进入日志。 */
    private void logPreChannelFailure(PaymentSubmissionContext context,
                                      String phase,
                                      RuntimeException exception) {
        PaymentCheckoutSessionDO sessionDO = context == null ? null : context.sessionDO();
        PaymentCheckoutAttemptDO attemptDO = context == null ? null : context.attemptDO();
        log.warn("event: CHECKOUT_PRE_CHANNEL_FAILED phase: {} checkoutSessionId: {} checkoutAttemptId: {} transactionId: {} exceptionType: {} reason: {}",
                phase,
                sessionDO == null ? null : sessionDO.getCheckoutSessionId(),
                attemptDO == null ? null : attemptDO.getCheckoutAttemptId(),
                attemptDO == null ? null : attemptDO.getTransactionId(),
                exception == null ? null : exception.getClass().getSimpleName(),
                safeLogReason(exception));
    }

    private String safeLogReason(RuntimeException exception) {
        return exception == null ? "-" : exception.getClass().getSimpleName();
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
    private PaymentCreateCommandDTO corePaymentCommand(PaymentCheckoutSessionDO sessionDO,
                                                       PaymentCheckoutAttemptDO attemptDO,
                                                       PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                       PaymentCheckoutThreeDsResultDTO threeDsResult) {
        PaymentCreateCommandDTO createCommand = new PaymentCreateCommandDTO();
        createCommand.setMerchantId(sessionDO == null ? attemptDO.getMerchantId() : sessionDO.getMerchantId());
        createCommand.setMerchantOrderNo(sessionDO == null ? attemptDO.getMerchantOrderNo() : sessionDO.getMerchantOrderNo());
        createCommand.setMerchantOrderId(attemptDO.getAttemptRequestId());
        createCommand.setTransactionId(attemptDO.getTransactionId());
        createCommand.setPaymentMethod(attemptDO.getPaymentMethod());
        createCommand.setAmount(sessionDO == null ? attemptDO.getLabelAmount() : sessionDO.getLabelAmount());
        createCommand.setCurrency(sessionDO == null ? attemptDO.getLabelCurrency() : sessionDO.getLabelCurrency());
        createCommand.setLabelAmount(sessionDO == null ? attemptDO.getLabelAmount() : sessionDO.getLabelAmount());
        createCommand.setLabelCurrency(sessionDO == null ? attemptDO.getLabelCurrency() : sessionDO.getLabelCurrency());
        createCommand.setTransactionDateTime(attemptDO.getTransactionDateTime());
        createCommand.setRequestFingerprint(commandDTO.getRequestFingerprint());
        createCommand.setRequestSource(REQUEST_SOURCE_HOSTED_CHECKOUT);
        createCommand.setCardInfo(toCoreCardInfo(commandDTO.getCardInfo()));
        createCommand.setBillingCardHolderInfo(toCoreBillingInfo(commandDTO.getBillingCardHolderInfo()));
        PaymentCreateCommandDTO.PayerInfoDTO payerInfo = sessionDO == null ? null : parseCheckoutSnapshot(
                sessionDO.getPayerInfoJson(), PaymentCreateCommandDTO.PayerInfoDTO.class);
        createCommand.setPayerInfo(payerInfo);
        createCommand.setSubMerchantInfo(sessionDO == null ? null : parseCheckoutSnapshot(
                sessionDO.getSubMerchantInfoJson(),
                PaymentCreateCommandDTO.SubMerchantInfoDTO.class));
        createCommand.setShippingInfo(sessionDO == null || !StringUtils.hasText(sessionDO.getShippingInfoJson())
                ? null : JsonUtils.parseObject(sessionDO.getShippingInfoJson(), PaymentCreateCommandDTO.ShippingInfoDTO.class));
        createCommand.setGoodsInfo(sessionDO == null || !StringUtils.hasText(sessionDO.getOrderItemsJson())
                ? List.of() : JsonUtils.parseObject(sessionDO.getOrderItemsJson(), new TypeReference<>() {
                }));
        createCommand.setThreeDsInfo(toCoreThreeDsInfo(threeDsResult));
        createCommand.setThreeDsRequired(threeDsResult != null && !threeDsResult.notRequired());
        createCommand.setChannelIdentity(toCoreChannelIdentity(attemptDO, threeDsResult));
        createCommand.setTransactionInfo(toCoreTransactionInfo(sessionDO, attemptDO));
        createCommand.setCallbackUrl(sessionDO == null ? null : sessionDO.getMerchantNotifyUrl());
        createCommand.setPayerIp(payerInfo == null
                ? commandDTO.getPayerIp() : firstText(payerInfo.getIpAddress(), commandDTO.getPayerIp()));
        createCommand.setUserAgent(payerInfo == null ? null : payerInfo.getUserAgent());
        return createCommand;
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
    private PaymentCheckoutSessionQueryResultDTO sessionResult(PaymentCheckoutSessionDO sessionDO, LocalDateTime serverTime) {
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
        orderDTO.setAmount(displayAmount(sessionDO));
        orderDTO.setCurrency(sessionDO.getLabelCurrency());
        orderDTO.setCurrencyExponent(sessionDO.getCurrencyExponent());
        orderDTO.setItemsJson(sessionDO.getOrderItemsJson());
        resultDTO.setOrder(orderDTO);
        resultDTO.setPaymentMethods(parsePaymentMethods(sessionDO.getAllowedPaymentMethodsJson()));
        PaymentCheckoutSessionQueryResultDTO.CheckoutDTO checkoutDTO = new PaymentCheckoutSessionQueryResultDTO.CheckoutDTO();
        checkoutDTO.setExpireTime(sessionDO.getExpireTime());
        checkoutDTO.setServerTime(serverTime);
        checkoutDTO.setRetryAllowed(mayStartPayment(sessionDO));
        checkoutDTO.setPollingIntervalSeconds(properties.getPollingIntervalSeconds());
        resultDTO.setCheckout(checkoutDTO);
        resultDTO.setPayerInfo(parseCheckoutSnapshot(
                sessionDO.getPayerInfoJson(), PaymentCheckoutSessionQueryResultDTO.PayerInfoDTO.class));
        resultDTO.setBillingInfo(parseCheckoutSnapshot(
                sessionDO.getBillingInfoJson(), PaymentCheckoutSessionQueryResultDTO.BillingInfoDTO.class));
        PaymentCheckoutAttemptDO latestAttempt = resolveAttempt(sessionDO, null);
        if (latestAttempt != null || !PaymentCheckoutSessionStatusEnum.PENDING.getCode().equals(sessionDO.getCheckoutStatus())) {
            resultDTO.setPaymentResult(paymentResult(sessionDO, latestAttempt));
        }
        if (cardEnvelopeService != null && mayStartPayment(sessionDO)) {
            resultDTO.setCardEncryption(cardEnvelopeService.issue(sessionDO.getCheckoutSessionId()));
        }
        return resultDTO;
    }

    /** 只有仍可新建支付尝试的页面状态才下发一次性卡数据 nonce。 */
    private boolean mayStartPayment(PaymentCheckoutSessionDO sessionDO) {
        if (sessionDO == null || sessionDO.getExpireTime() == null
                || !sessionDO.getExpireTime().isAfter(LocalDateTime.now())) {
            return false;
        }
        String status = sessionDO.getCheckoutStatus();
        if (PaymentCheckoutSessionStatusEnum.PENDING.getCode().equals(status)) {
            return sessionDO.getLastSubmitTime() == null
                    && PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode().equals(sessionDO.getProcessStage());
        }
        return PaymentCheckoutSessionStatusEnum.FAILED.getCode().equals(status)
                && sessionDO.getLastSubmitTime() != null
                && Integer.valueOf(1).equals(sessionDO.getRetryAllowed());
    }

    /** 读取允许在运营和收银台展示的明文预填快照。 */
    private <T> T parseCheckoutSnapshot(String json, Class<T> targetType) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JsonUtils.parseObject(json, targetType);
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
        resultDTO.setActions(actionDetail(sessionDO, attemptDO));
        if (attemptDO != null && PaymentCheckoutAttemptStatusEnum.THREE_DS_REQUIRED.getCode().equals(attemptDO.getAttemptStatus())) {
            resultDTO.setPageState(PaymentCheckoutPageStateEnum.THREE_DS_REQUIRED.getCode());
        } else if (attemptDO != null
                && PaymentCheckoutAttemptStatusEnum.THREE_DS_RETURNED.getCode().equals(attemptDO.getAttemptStatus())) {
            resultDTO.setPageState(PaymentCheckoutPageStateEnum.PROCESSING.getCode());
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
        resultDTO.setAmount(displayAmount(sessionDO));
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
     * 按会话创建时固化的 ISO 币种精度输出金额；只调整展示 scale，不改变落库金额或计算精度。
     */
    private BigDecimal displayAmount(PaymentCheckoutSessionDO sessionDO) {
        if (sessionDO == null || sessionDO.getLabelAmount() == null) {
            return null;
        }
        Integer currencyExponent = sessionDO.getCurrencyExponent();
        if (currencyExponent == null || currencyExponent < 0) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout currency exponent is invalid");
        }
        return sessionDO.getLabelAmount().setScale(currencyExponent, RoundingMode.UNNECESSARY);
    }

    /**
     * 输出付款人失败提示，内部渠道原因不直接暴露给浏览器。
     */
    private PaymentCheckoutPaymentResultDTO.FailureDTO failureDetail(PaymentCheckoutSessionDO sessionDO,
                                                                     PaymentCheckoutAttemptDO attemptDO) {
        if (sessionDO == null) {
            return null;
        }
        PaymentCheckoutPaymentResultDTO.FailureDTO failureDTO = new PaymentCheckoutPaymentResultDTO.FailureDTO();
        if (attemptDO == null || attemptDO.getFailureReasonCode() == null) {
            if (!isPaymentTimeout(sessionDO)) {
                return null;
            }
            failureDTO.setReasonCode("PAYMENT_TIMEOUT");
            failureDTO.setMessage("Payment was not completed within the allowed time.");
        } else {
            failureDTO.setReasonCode(attemptDO.getFailureReasonCode());
            failureDTO.setMessage(attemptDO.getPayerVisibleMessage());
        }
        failureDTO.setRetryAllowed(mayStartPayment(sessionDO));
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

    /** 只为已形成 SUCCESS/FAILED 动作终态且配置 redirectUrl 的交易返回 Form POST。 */
    private PaymentCheckoutPaymentResultDTO.ActionDTO actionDetail(PaymentCheckoutSessionDO sessionDO,
                                                                    PaymentCheckoutAttemptDO attemptDO) {
        if (sessionDO == null || attemptDO == null
                || (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(attemptDO.getChannelStatus())
                && !PaymentTransactionStatusEnum.FAILED.getCode().equals(attemptDO.getChannelStatus()))) {
            return null;
        }
        String redirectUrl = sessionDO.getRedirectUrl();
        if (!StringUtils.hasText(redirectUrl)) {
            return null;
        }
        PaymentCheckoutPaymentResultDTO.ActionDTO actionDTO = new PaymentCheckoutPaymentResultDTO.ActionDTO();
        actionDTO.setMethod("POST");
        actionDTO.setRedirectUrl(redirectUrl);
        actionDTO.setDelaySeconds(5);
        PaymentCheckoutPaymentResultDTO.FormFieldsDTO form = new PaymentCheckoutPaymentResultDTO.FormFieldsDTO();
        form.setMerchantId(sessionDO.getMerchantId());
        form.setOrderNo(sessionDO.getMerchantOrderNo());
        form.setOrderId(sessionDO.getMerchantRequestId());
        form.setTransactionId(attemptDO.getTransactionId());
        form.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
        form.setTransactionStatus(attemptDO.getChannelStatus());
        form.setTransactionDateTime(attemptDO.getTransactionDateTime());
        form.setCode(firstText(attemptDO.getChannelResponseCode(),
                PaymentTransactionStatusEnum.SUCCESS.getCode().equals(attemptDO.getChannelStatus())
                        ? ApiResultEnum.PAYMENT_SUCCESS.getCode() : ApiResultEnum.PAYMENT_REJECTED.getCode()));
        form.setMessage(firstText(attemptDO.getChannelResponseMessage(),
                PaymentTransactionStatusEnum.SUCCESS.getCode().equals(attemptDO.getChannelStatus())
                        ? ApiResultEnum.PAYMENT_SUCCESS.getMessage() : ApiResultEnum.PAYMENT_REJECTED.getMessage()));
        actionDTO.setFormFields(form);
        return actionDTO;
    }

    /**
     * 封装 3DS 质询动作，HTML 由渠道返回并在收银台 iframe 内展示。
     */
    private PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO threeDsAction(String phase,
                                                                           String html,
                                                                           String returnUrl) {
        PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO actionDTO = new PaymentCheckoutPaymentResultDTO.ThreeDsActionDTO();
        actionDTO.setActionType(THREE_DS_ACTION_HTML);
        actionDTO.setPhase(phase);
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
        int updated = attemptMapper.markAuthenticationResultCas(attemptDO.getCheckoutAttemptId(),
                nextStatus.getCode(),
                nextStage.getCode(),
                threeDsResult.getChannelCode(),
                threeDsResult.getChannelMidConfigId(),
                threeDsResult.getChannelOrderNo(),
                threeDsResult.notRequired() ? 0 : 1,
                threeDsResult.getStatus(),
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
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout 3DS state changed, please retry");
        }
        PaymentCheckoutAttemptDO latestAttempt = attemptMapper.selectByCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        latestAttempt = latestAttempt == null ? attemptDO : latestAttempt;
        if (!threeDsResult.notRequired()) {
            paymentTransactionService.markThreeDsIndicator(
                    latestAttempt.getTransactionId(), latestAttempt.getTransactionDateTime(),
                    firstText(threeDsResult.getEci(), "REQUIRED"));
        }
        return latestAttempt;
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

    /** 根据会话重试策略决定失败后是否允许重新支付；尝试次数仅用于审计。 */
    private PaymentCheckoutSessionDO failSession(PaymentCheckoutSessionDO sourceSessionDO,
                                                PaymentCheckoutAttemptDO latestAttempt,
                                                LocalDateTime now) {
        PaymentCheckoutSessionDO sessionDO = sessionMapper.selectByCheckoutSessionId(sourceSessionDO.getCheckoutSessionId());
        if (sessionDO == null) {
            return sourceSessionDO;
        }
        sessionMapper.markFailedCas(sessionDO.getCheckoutSessionId(),
                PaymentCheckoutSessionStatusEnum.FAILED.getCode(),
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
        target.setCardholderName(source.getCardholderName());
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
        if (source == null || !source.passed()) {
            return null;
        }
        PaymentCreateCommandDTO.ThreeDsInfoDTO target = new PaymentCreateCommandDTO.ThreeDsInfoDTO();
        target.setAuthenticationStatus(source.getStatus());
        target.setAuthenticationTransactionId(source.getAuthenticationTransactionId());
        target.setEci(source.getEci());
        target.setCavv(source.getCavv());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        return target;
    }

    /**
     * 复用 3DS 阶段选择的渠道、MID 和渠道订单号。
     * 认证交易号只通过 threeDsInfo.authenticationTransactionId 引用；PAY/AUTHORIZE 必须生成独立渠道交易号。
     */
    private PaymentCreateCommandDTO.ChannelIdentityDTO toCoreChannelIdentity(
            PaymentCheckoutAttemptDO attemptDO,
            PaymentCheckoutThreeDsResultDTO threeDsResult) {
        String channelCode = firstText(attemptDO == null ? null : attemptDO.getChannelCode(),
                threeDsResult == null ? null : threeDsResult.getChannelCode());
        Long channelMidConfigId = attemptDO != null && attemptDO.getChannelMidConfigId() != null
                ? attemptDO.getChannelMidConfigId()
                : threeDsResult == null ? null : threeDsResult.getChannelMidConfigId();
        if (!StringUtils.hasText(channelCode) || channelMidConfigId == null) {
            return null;
        }
        PaymentCreateCommandDTO.ChannelIdentityDTO target = new PaymentCreateCommandDTO.ChannelIdentityDTO();
        target.setChannelCode(channelCode);
        target.setChannelId(threeDsResult == null ? null : threeDsResult.getChannelId());
        target.setChannelMidConfigId(channelMidConfigId);
        target.setChannelOrderNo(firstText(attemptDO == null ? null : attemptDO.getChannelOrderNo(),
                threeDsResult == null ? null : threeDsResult.getChannelOrderNo()));
        target.setChannelTransactionId(attemptDO == null ? null : attemptDO.getChannelTransactionId());
        return target;
    }

    /**
     * 将收银台尝试号映射为支付核心交易标识，保持结果页、回调和交易详情可关联。
     */
    private PaymentCreateCommandDTO.TransactionInfoDTO toCoreTransactionInfo(PaymentCheckoutSessionDO sessionDO,
                                                                            PaymentCheckoutAttemptDO attemptDO) {
        PaymentCreateCommandDTO.TransactionInfoDTO target = new PaymentCreateCommandDTO.TransactionInfoDTO();
        target.setTransactionId(attemptDO.getTransactionId());
        target.setCardBrand(attemptDO.getPaymentBrand());
        target.setDescription(sessionDO == null ? null : sessionDO.getOrderDescription());
        target.setRedirectUrl(sessionDO == null ? null : sessionDO.getRedirectUrl());
        target.setLanguage(sessionDO == null ? null : sessionDO.getLocale());
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
        if (sessionDO.getExpireTime() == null || !sessionDO.getExpireTime().isAfter(now)) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "checkout session expired");
        }
        String status = sessionDO.getCheckoutStatus();
        boolean initialSubmission = PaymentCheckoutSessionStatusEnum.PENDING.getCode().equals(status)
                && sessionDO.getLastSubmitTime() == null
                && PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode().equals(sessionDO.getProcessStage());
        boolean retrySubmission = PaymentCheckoutSessionStatusEnum.FAILED.getCode().equals(status)
                && sessionDO.getLastSubmitTime() != null
                && Integer.valueOf(1).equals(sessionDO.getRetryAllowed());
        if (!initialSubmission && !retrySubmission) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(), "checkout session is not payable");
        }
    }

    /** 查询时惰性补偿到期状态，避免调度延迟导致页面仍展示付款表单。 */
    private PaymentCheckoutSessionDO expireSessionIfDue(PaymentCheckoutSessionDO sessionDO, LocalDateTime now) {
        if (sessionDO == null || sessionDO.getExpireTime() == null || sessionDO.getExpireTime().isAfter(now)
                || !PaymentCheckoutSessionStatusEnum.PENDING.getCode().equals(sessionDO.getCheckoutStatus())
                || !PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode().equals(sessionDO.getProcessStage())
                || sessionDO.getLastSubmitTime() != null) {
            return sessionDO;
        }
        markSessionExpired(sessionDO, now);
        PaymentCheckoutSessionDO latest = sessionMapper.selectByCheckoutSessionId(sessionDO.getCheckoutSessionId());
        return latest == null ? sessionDO : latest;
    }

    /** CAS 标记单个会话过期并写入审计事件。 */
    private boolean markSessionExpired(PaymentCheckoutSessionDO sessionDO, LocalDateTime now) {
        if (sessionDO == null || sessionDO.getVersion() == null) {
            return false;
        }
        String snapshot = "{\"pageState\":\"EXPIRED\",\"failureCode\":\"PAYMENT_TIMEOUT\"}";
        int updated = sessionMapper.markPaymentTimeoutCas(
                sessionDO.getCheckoutSessionId(), snapshot, sessionDO.getVersion(), now);
        if (updated != 1) {
            return false;
        }
        sessionDO.setCheckoutStatus(PaymentCheckoutSessionStatusEnum.FAILED.getCode());
        sessionDO.setProcessStage(PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode());
        sessionDO.setResultSnapshot(snapshot);
        sessionDO.setLastStatusTime(now);
        sessionDO.setUpdateTime(now);
        sessionDO.setVersion(sessionDO.getVersion() + 1);
        insertEvent(event(sessionDO, null, EVENT_SESSION_EXPIRED,
                PaymentCheckoutProcessStageEnum.RESULT_RENDERED, PaymentCheckoutEventResultEnum.FAILED,
                null, null, now));
        createTimeoutMerchantNotification(sessionDO, now);
        return true;
    }

    /**
     * 在到期状态事务内创建商户通知任务，实际 HTTP 投递复用 service-data 的重试机制。
     */
    private void createTimeoutMerchantNotification(PaymentCheckoutSessionDO sessionDO, LocalDateTime now) {
        if (merchantNotificationMapper == null) {
            return;
        }
        String callbackUrl = sessionDO.getMerchantNotifyUrl();
        if (!StringUtils.hasText(callbackUrl)) {
            return;
        }
        String transactionId = firstText(sessionDO.getLatestTransactionId(), sessionDO.getCheckoutSessionId());
        String operationId = firstText(sessionDO.getOperationId(), sessionDO.getCheckoutSessionId());
        Map<String, Object> payload = timeoutMerchantPayload(sessionDO, transactionId);
        String payloadJson = JsonUtils.toJsonString(payload);
        TransactionMerchantNotificationDO notificationDO = new TransactionMerchantNotificationDO();
        notificationDO.setNotifyId(PaymentOrderNoGenerator.nextOrderNo(MERCHANT_NOTIFICATION_PREFIX, now));
        notificationDO.setTransactionId(transactionId);
        notificationDO.setOperationId(operationId);
        notificationDO.setMerchantId(sessionDO.getMerchantId());
        notificationDO.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        notificationDO.setNotifyType("PAYMENT_RESULT");
        notificationDO.setEventType("CHECKOUT_PAYMENT_TIMEOUT");
        notificationDO.setNotifyStatus("INIT");
        notificationDO.setCallbackUrl(callbackUrl);
        notificationDO.setPayloadJson(payloadJson);
        notificationDO.setTargetUrlHash(sha256Hex(callbackUrl));
        notificationDO.setTargetUrlMasked(maskUrl(callbackUrl));
        notificationDO.setPayloadJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(payloadJson));
        notificationDO.setLastAttemptNo(0);
        notificationDO.setMaxRetryCount(merchantNotificationProperties.getMaxRetryCount());
        notificationDO.setNextRetryTime(now.plusSeconds(
                MerchantNotificationInitialDeliveryService.INITIAL_DELIVERY_DELAY_SECONDS));
        notificationDO.setTransactionDateTime(now);
        notificationDO.setTransactionUtcTime(now.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        notificationDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        notificationDO.setVersion(0);
        notificationDO.setDeleted(0);
        notificationDO.setCreateTime(now);
        notificationDO.setUpdateTime(now);
        int inserted = merchantNotificationMapper.insertLogical(notificationDO);
        if (inserted == 1 && merchantNotificationInitialDeliveryService != null) {
            merchantNotificationInitialDeliveryService.schedule(notificationDO, 0, now);
        }
    }

    /** 构造与交易回调同一层级结构的收银台超时载荷。 */
    private Map<String, Object> timeoutMerchantPayload(PaymentCheckoutSessionDO sessionDO, String transactionId) {
        Map<String, Object> merchantInfo = new LinkedHashMap<>();
        merchantInfo.put("merchantId", sessionDO.getMerchantId());
        Map<String, Object> orderInfo = new LinkedHashMap<>();
        orderInfo.put("orderNo", sessionDO.getMerchantOrderNo());
        orderInfo.put("orderId", sessionDO.getMerchantRequestId());
        orderInfo.put("amount", sessionDO.getLabelAmount());
        orderInfo.put("currency", sessionDO.getLabelCurrency());
        Map<String, Object> transactionInfo = new LinkedHashMap<>();
        transactionInfo.put("code", "PAYMENT_TIMEOUT");
        transactionInfo.put("message", "Payment was not completed within the allowed time");
        transactionInfo.put("transactionId", transactionId);
        transactionInfo.put("transactionType", PaymentTransactionTypeEnum.PAYMENT.getCode());
        transactionInfo.put("transactionStatus", PaymentTransactionStatusEnum.FAILED.getCode());
        transactionInfo.put("failReasonCode", "PAYMENT_TIMEOUT");
        transactionInfo.put("transactionDateTime", sessionDO.getExpireTime());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantInfo", merchantInfo);
        payload.put("orderInfo", orderInfo);
        payload.put("transactionInfo", transactionInfo);
        payload.put("checkoutSessionId", sessionDO.getCheckoutSessionId());
        return payload;
    }

    /** URL 审计值只保留 query 之前的地址。 */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }

    /**
     * 校验商户创建会话时允许的支付方式快照，当前 V1 只放行银行卡支付。
     */
    private void ensurePaymentMethodAllowed(PaymentCheckoutSessionDO sessionDO,
                                            PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        if (!PAYMENT_METHOD_BANK_CARD.equals(commandDTO.getPaymentMethod())) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(),
                    "hosted checkout v1 only supports BANK_CARD");
        }
        String cardBrand = resolveCardBrand(commandDTO.getCardInfo());
        boolean allowed = parsePaymentMethods(sessionDO.getAllowedPaymentMethodsJson()).stream()
                .anyMatch(method -> PAYMENT_METHOD_BANK_CARD.equals(method.getPaymentMethod())
                        && (method.getBrands() == null || method.getBrands().stream()
                        .map(this::normalizeCode).anyMatch(cardBrand::equals)));
        if (!allowed) {
            throw new ServiceException(ApiResultEnum.CARD_NOT_SUPPORTED.getCode(),
                    "current card brand is not supported by merchant channel MID");
        }
    }

    private boolean isCardBrandAllowed(PaymentCheckoutSessionDO sessionDO, String cardBrand) {
        return StringUtils.hasText(cardBrand) && !"UNKNOWN".equals(cardBrand)
                && parsePaymentMethods(sessionDO.getAllowedPaymentMethodsJson()).stream()
                .filter(method -> PAYMENT_METHOD_BANK_CARD.equals(method.getPaymentMethod()))
                .anyMatch(method -> method.getBrands() != null && method.getBrands().stream()
                        .map(this::normalizeCode).anyMatch(cardBrand::equals));
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
                && (tokenDO.getExpireTime() == null || tokenDO.getExpireTime().isAfter(now));
    }

    /**
     * 将内部会话状态映射为前端页面状态，未知状态按拦截处理。
     */
    private PaymentCheckoutPageStateEnum toPageState(PaymentCheckoutSessionDO sessionDO) {
        if (sessionDO == null || sessionDO.getCheckoutStatus() == null) {
            return PaymentCheckoutPageStateEnum.BLOCKED;
        }
        return switch (sessionDO.getCheckoutStatus()) {
            case "PENDING" -> PaymentCheckoutPageStateEnum.PAYABLE;
            case "PROCESSING" -> PaymentCheckoutPageStateEnum.PROCESSING;
            case "SUCCESS" -> PaymentCheckoutPageStateEnum.SUCCEEDED;
            case "FAILED" -> isPaymentTimeout(sessionDO)
                    ? PaymentCheckoutPageStateEnum.EXPIRED
                    : mayStartPayment(sessionDO)
                    ? PaymentCheckoutPageStateEnum.FAILED_RETRYABLE
                    : PaymentCheckoutPageStateEnum.FAILED_FINAL;
            default -> PaymentCheckoutPageStateEnum.BLOCKED;
        };
    }

    /** 未创建支付尝试且由截止时间任务置为失败时，页面展示链接已过期而不新增业务状态。 */
    private boolean isPaymentTimeout(PaymentCheckoutSessionDO sessionDO) {
        return sessionDO != null
                && PaymentCheckoutSessionStatusEnum.FAILED.getCode().equals(sessionDO.getCheckoutStatus())
                && sessionDO.getLastSubmitTime() == null
                && PaymentCheckoutProcessStageEnum.RESULT_RENDERED.getCode().equals(sessionDO.getProcessStage())
                && sessionDO.getResultSnapshot() != null
                && sessionDO.getResultSnapshot().contains("PAYMENT_TIMEOUT");
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
     * 生成允许支付方式快照；未显式传入时只声明银行卡能力，具体渠道交给支付路由选择。
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
     * 从允许支付方式快照中读取商户显式指定的渠道；未指定时保持为空交给支付路由。
     */
    private String resolveChannelCode(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods) {
        return defaultAllowedPaymentMethods(methods).stream()
                .map(PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO::getChannelCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * 创建会话时提前拒绝 V1 尚不支持的支付方式组合。
     */
    private void validateAllowedPaymentMethods(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods) {
        for (PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO method : methods) {
            if (!PAYMENT_METHOD_BANK_CARD.equals(normalizeCode(method.getPaymentMethod()))) {
                throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(),
                        "hosted checkout v1 only supports BANK_CARD");
            }
        }
    }

    /**
     * 解析付款提交截止时间；非法或过去时间使用 24 小时，内部调用也不能放大该窗口。
     */
    private LocalDateTime resolveExpireTime(LocalDateTime input, LocalDateTime now) {
        LocalDateTime deadline = now.plusMinutes(PAYMENT_SUBMISSION_DEADLINE_MINUTES);
        if (input == null || !input.isAfter(now)) {
            return deadline;
        }
        return input.isAfter(deadline) ? deadline : input;
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
     * 解析付款卡品牌，优先使用 BIN 基础数据，并在能力服务缺失时复用统一平台规则。
     *
     * @param cardInfoDTO 已解密且通过收银台校验的卡信息
     * @return 平台标准卡品牌；卡号缺失时返回 {@code null}
     */
    private String resolveCardBrand(PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfoDTO) {
        if (cardInfoDTO == null || !StringUtils.hasText(cardInfoDTO.getCardNo())) {
            return null;
        }
        if (cardCapabilityService != null) {
            return cardCapabilityService.resolveCardBrand(cardInfoDTO.getCardNo());
        }
        return PaymentCardBrandRuleMatcher.resolve(cardInfoDTO.getCardNo());
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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
                                            String threeDsReturnTokenHash,
                                            boolean duplicate) {
    }

    private record PaymentStatusContext(PaymentCheckoutSessionDO sessionDO,
                                        PaymentCheckoutAttemptDO attemptDO,
                                        boolean coreConvergenceRequired) {
    }

    private record ThreeDsReturnContext(PaymentCheckoutSessionDO sessionDO,
                                        PaymentCheckoutAttemptDO attemptDO,
                                        PaymentCheckoutPaymentResultDTO immediateResult) {
    }

    private record ThreeDsBrowserContext(String returnUrl, String returnTokenHash) {
    }
}

package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.api.HostedWalletSimulator;
import com.scott.payment.channel.payment.api.HostedWalletSimulator.Order;
import com.scott.payment.channel.payment.api.HostedWalletSimulator.Result;
import com.scott.payment.channel.payment.api.HostedWalletSimulator.State;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.config.MerchantNotificationProperties;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.domain.state.PaymentCheckoutPayability;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.mapper.PaymentCheckoutAttemptMapper;
import com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.support.PaymentCheckoutTokenSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** dev 专用钱包渠道适配，按真实渠道职责接入本机挡板并完全隔离资金交易。 */
@Service
@Profile("dev & !test & !uat & !prod & !live-regression")
@DS(DataSourceName.TRANSACTION)
public class CheckoutWalletSimulationService {
    private static final String CHANNEL = "SCOTT_CHANNEL_A";
    private static final String ATTEMPT_PREFIX = "DEV-INTERACTIVE-";
    private static final String CHANNEL_ORDER_PREFIX = "DEV-IW-";
    private static final String CHANNEL_NOTIFY_URL = "http://127.0.0.1:8000/channel/v1/callbacks/SCOTT_CHANNEL_A";
    private static final String CHANNEL_RETURN_PATH = "/checkout/api/v1/wallet/authorization/return";
    private static final Set<String> WALLETS = Set.of("APPLE_PAY", "GOOGLE_PAY");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PaymentChannelRouteService routes;
    private final PaymentExchangeRateService exchangeRates;
    private final IsoDictionaryService isoDictionary;
    private final HostedWalletSimulator channel;
    private final PaymentCheckoutAttemptMapper attempts;
    private final PaymentCheckoutSessionMapper sessions;
    private final GlobalIdGenerator ids;
    private final PaymentCheckoutProperties properties;
    private final TransactionOperations transactions;
    private final TransactionMerchantNotificationMapper notifications;
    private final MerchantNotificationInitialDeliveryService delivery;
    private final MerchantNotificationProperties notificationProperties;
    private final CheckoutWalletTransactionLedger ledger;

    public CheckoutWalletSimulationService(PaymentChannelRouteService routes, PaymentExchangeRateService exchangeRates,
            IsoDictionaryService isoDictionary, Optional<HostedWalletSimulator> channel,
            PaymentCheckoutAttemptMapper attempts, PaymentCheckoutSessionMapper sessions, GlobalIdGenerator ids,
            PaymentCheckoutProperties properties, TransactionOperations transactions,
            TransactionMerchantNotificationMapper notifications, MerchantNotificationInitialDeliveryService delivery,
            MerchantNotificationProperties notificationProperties, CheckoutWalletTransactionLedger ledger) {
        this.routes = routes;
        this.exchangeRates = exchangeRates;
        this.isoDictionary = isoDictionary;
        this.channel = channel.orElse(null);
        this.attempts = attempts;
        this.sessions = sessions;
        this.ids = ids;
        this.properties = properties;
        this.transactions = transactions;
        this.notifications = notifications;
        this.delivery = delivery;
        this.notificationProperties = notificationProperties;
        this.ledger = ledger;
    }

    public boolean supports(PaymentCheckoutSessionDO session, String method) {
        if (channel == null || session == null || !StringUtils.hasText(session.getMerchantId())
                || !"PAYMENT".equals(session.getPaymentAction()) || !"HOSTED_CHECKOUT".equals(session.getIntegrationType())
                || !StringUtils.hasText(session.getLabelCurrency()) || method == null || !WALLETS.contains(method)
                || !StringUtils.hasText(session.getCheckoutDomain())
                || !allowedDevelopmentCheckoutOrigin(session.getCheckoutDomain())) {
            return false;
        }
        BigDecimal amount = session.getLabelAmount();
        return validOrderAmount(amount, session.getLabelCurrency());
    }

    /** 试单身份命名空间与银行卡、旧模拟模式和正式渠道相互隔离。 */
    public boolean isSimulationAttempt(PaymentCheckoutAttemptDO attempt) {
        return attempt != null && StringUtils.hasText(attempt.getMerchantId())
                && CHANNEL.equals(attempt.getChannelCode())
                && attempt.getPaymentMethod() != null && WALLETS.contains(attempt.getPaymentMethod())
                && attempt.getAttemptRequestId() != null
                && attempt.getAttemptRequestId().startsWith(ATTEMPT_PREFIX + attempt.getPaymentMethod())
                && (CHANNEL_ORDER_PREFIX + attempt.getCheckoutAttemptId()).equals(attempt.getChannelOrderNo());
    }

    static boolean isSimulationOrder(String channelCode, String channelOrderNo) {
        return CHANNEL.equalsIgnoreCase(channelCode) && channelOrderNo != null
                && channelOrderNo.startsWith(CHANNEL_ORDER_PREFIX);
    }

    public Map<String, String> execute(PaymentCheckoutSessionDO session, String method, String action) {
        if (!supports(session, method)) {
            throw unavailable();
        }
        PaymentRouteResultDTO route;
        PaymentCheckoutAttemptDO attempt;
        if ("PREPARE".equals(action)) {
            route = route(session, method);
            attempt = transactions.execute(tx -> reserveAttempt(session, method, route));
        } else {
            attempt = attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), method);
            route = restoreRoute(attempt);
        }
        if (!isSimulationAttempt(attempt) || !belongsTo(session, attempt)
                || !Objects.equals(attempt.getChannelMidConfigId(), route.getMidConfigId())) {
            throw unavailable();
        }
        if ("OPEN".equals(action) && !StringUtils.hasText(attempt.getChannelTransactionId())) {
            throw unavailable();
        }
        if ("STATUS".equals(action)) {
            return synchronize(session, attempt);
        }
        Order order = order(attempt, route);
        String returnUrl = channelReturnUrl(session, attempt);
        Result result;
        try {
            // 同一订单号、相同参数重试，渠道回读原订单及授权页。
            result = channel.prepare(order, returnUrl, CHANNEL_NOTIFY_URL);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY);
        }
        attempts.bindWalletChannelTransaction(attempt.getCheckoutAttemptId(), result.transactionId(), now());
        attempt = attempts.selectByCheckoutAttemptId(attempt.getCheckoutAttemptId());
        if (attempt == null || !Objects.equals(attempt.getChannelTransactionId(), result.transactionId())) {
            throw unavailable();
        }
        if ("OPEN".equals(action) && result.state() == State.READY) {
            PaymentCheckoutAttemptDO current = attempt;
            transactions.executeWithoutResult(tx -> activateAttempt(current, route));
        }
        State state = result.state() == State.APPROVED ? State.PROCESSING : result.state();
        return browserState(session, attempt, state, result.redirectUrl());
    }

    /** 回跳令牌只能访问已经启动的专用尝试。浏览器提供的交易号仅用于一致性核对。 */
    public String returned(String token, String channelTransactionId) {
        if (token == null || !token.matches("[0-9]{16,32}\\.[a-f0-9]{64}")) {
            throw unauthorized();
        }
        PaymentCheckoutAttemptDO attempt = attempts.selectByCheckoutAttemptId(token.substring(0, token.indexOf('.')));
        if (!isSimulationAttempt(attempt) || !Objects.equals(attempt.getChannelTransactionId(), channelTransactionId)
                || !MessageDigest.isEqual(returnToken(attempt).getBytes(StandardCharsets.US_ASCII),
                        token.getBytes(StandardCharsets.US_ASCII))
                || attempt.getCreateTime() == null || attempt.getCreateTime().plusHours(25).isBefore(now())) {
            throw unauthorized();
        }
        PaymentCheckoutSessionDO session = sessions.selectByCheckoutSessionId(attempt.getCheckoutSessionId());
        if (!supports(session, attempt.getPaymentMethod()) || !belongsTo(session, attempt) || !isActive(session, attempt)) {
            throw unauthorized();
        }
        synchronize(session, attempt);
        return session.getCheckoutSessionId();
    }

    /** 使用已验签且可靠入箱的渠道事件；回调、提交及查单共享同一终态写入。 */
    public boolean acceptCallback(TransactionChannelCallbackCommandDTO command) {
        String order = command.getExtension() == null ? null : command.getExtension().get("merchantOrderNo");
        if (!CHANNEL.equals(command.getChannelCode())
                || order == null || !order.matches(CHANNEL_ORDER_PREFIX + "[0-9]{16,32}")) {
            return false;
        }
        if (!Boolean.TRUE.equals(command.getSignatureValid()) || !Boolean.TRUE.equals(command.getIpAllowed())
                || !"FINANCIAL_TRANSACTION".equals(command.getCallbackKind())) {
            throw unauthorized();
        }
        PaymentCheckoutAttemptDO attempt = attempts.selectByCheckoutAttemptId(order.substring(CHANNEL_ORDER_PREFIX.length()));
        PaymentRouteResultDTO route = restoreRoute(attempt);
        if (!Objects.equals(route.getMidNo(), command.getChannelCredentialHint())) {
            throw unauthorized();
        }
        if (!isSimulationAttempt(attempt) || !order.equals(attempt.getChannelOrderNo())
                || !Objects.equals(attempt.getChannelTransactionId(), command.getChannelTransactionId())
                || !Objects.equals(attempt.getChannelRequestCurrency(), command.getCurrency())
                || command.getAmount() == null || attempt.getChannelRequestAmount().compareTo(command.getAmount()) != 0
                || !attempt.getPaymentMethod().equals(command.getExtension().get("paymentMethod"))) {
            throw unavailable();
        }
        State terminal = switch (Objects.toString(command.getRawChannelStatus(), "")) {
            case "succeeded" -> State.SUCCESS;
            case "failed" -> "PAYMENT_EXPIRED".equals(command.getChannelResponseCode()) ? State.EXPIRED : State.FAILED;
            case "canceled" -> State.CANCELLED;
            default -> throw unavailable();
        };
        complete(attempt, route, terminal);
        return true;
    }

    private PaymentCheckoutAttemptDO reserveAttempt(PaymentCheckoutSessionDO source, String method, PaymentRouteResultDTO route) {
        PaymentCheckoutSessionDO session = sessions.selectByCheckoutSessionIdForUpdate(source.getCheckoutSessionId());
        if (!supports(session, method)) {
            throw unavailable();
        }
        PaymentCheckoutAttemptDO existing = attempts.selectLatestWalletAttempt(session.getCheckoutSessionId(), method);
        if (existing != null && persistedState(existing) == State.PROCESSING) {
            if (!belongsTo(session, existing)
                    || (!PaymentCheckoutPayability.mayStart(session, now()) && !isActive(session, existing))) {
                throw unavailable();
            }
            return existing;
        }
        if (!PaymentCheckoutPayability.mayStart(session, now())) {
            throw unavailable();
        }
        LocalDateTime now = now();
        PaymentCheckoutAttemptDO attempt = new PaymentCheckoutAttemptDO();
        attempt.setCheckoutAttemptId(ids.nextId());
        attempt.setCheckoutSessionId(session.getCheckoutSessionId());
        attempt.setMerchantId(session.getMerchantId());
        attempt.setMerchantOrderNo(session.getMerchantOrderNo());
        Integer previous = attempts.selectMaxAttemptNo(session.getCheckoutSessionId());
        int attemptNo = previous == null ? 1 : previous + 1;
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptRequestId(ATTEMPT_PREFIX + method + "-" + attemptNo);
        attempt.setRequestFingerprint(ATTEMPT_PREFIX + method + "-" + attemptNo);
        attempt.setAttemptStatus("PROCESSING");
        attempt.setProcessStage("WAITING_PAYER");
        attempt.setPaymentMethod(method);
        attempt.setLabelAmount(session.getLabelAmount());
        attempt.setLabelCurrency(normalizeCurrency(session.getLabelCurrency()));
        ChannelAmountPlan amountPlan = channelAmountPlan(session, route, now);
        attempt.setChannelRequestAmount(amountPlan.amount());
        attempt.setChannelRequestCurrency(amountPlan.currency());
        attempt.setResultSnapshot(JsonUtils.toJsonString(amountPlan.quote()));
        attempt.setChannelCode(route.getChannelCode());
        attempt.setChannelMidConfigId(route.getMidConfigId());
        attempt.setChannelOrderNo(CHANNEL_ORDER_PREFIX + attempt.getCheckoutAttemptId());
        attempt.setSubmitTime(now);
        attempt.setTransactionDateTime(now);
        attempt.setThreeDsRequired(0);
        attempt.setVersion(0);
        attempt.setDeleted(0);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        attempts.insert(attempt);
        return attempt;
    }

    private void activateAttempt(PaymentCheckoutAttemptDO attempt, PaymentRouteResultDTO route) {
        PaymentCheckoutSessionDO session = sessions.selectByCheckoutSessionIdForUpdate(attempt.getCheckoutSessionId());
        PaymentCheckoutAttemptDO current = attempts.selectByCheckoutAttemptId(attempt.getCheckoutAttemptId());
        if (!supports(session, attempt.getPaymentMethod()) || !isSimulationAttempt(current)
                || !belongsTo(session, current)
                || persistedState(current) != State.PROCESSING) {
            throw unavailable();
        }
        if (isActive(session, attempt)) {
            return;
        }
        LocalDateTime now = now().truncatedTo(ChronoUnit.MILLIS);
        if (!PaymentCheckoutPayability.mayStart(session, now)) {
            throw unavailable();
        }
        CheckoutWalletTransactionLedger.Identity identity = null;
        if (StringUtils.hasText(current.getResultSnapshot())) {
            var quote = JsonUtils.parseObject(current.getResultSnapshot(), CheckoutWalletTransactionLedger.Quote.class);
            // OPEN 才正式建单，分片时间必须与这次生成的平台交易号处于同一业务窗口。
            current.setTransactionDateTime(now);
            identity = ledger.open(session, current, route, quote);
            if (attempts.bindWalletCoreIdentity(current.getCheckoutAttemptId(), identity.transactionId(),
                    identity.operationId(), identity.requestId(), current.getVersion(), now) != 1) {
                throw unavailable();
            }
        }
        // 升级前已准备的钱包单缺少锁定汇率快照，沿原路径完成并保留历史口径。
        if (sessions.markSubmittedCas(session.getCheckoutSessionId(), "PROCESSING", "WAITING_CHANNEL",
                identity == null ? null : identity.transactionId(), identity == null ? null : identity.operationId(),
                current.getTransactionDateTime(), current.getCheckoutAttemptId(), session.getVersion(), now) != 1) {
            throw unavailable();
        }
    }

    private Map<String, String> synchronize(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt) {
        State stored = persistedState(attempt);
        if (stored != State.PROCESSING || !isActive(session, attempt)) {
            return browserState(session, attempt, stored, null);
        }
        Result result;
        PaymentRouteResultDTO route;
        try {
            route = restoreRoute(attempt);
            Order order = order(attempt, route);
            result = channel.query(order);
            if (!Objects.equals(attempt.getChannelTransactionId(), result.transactionId())) {
                throw unavailable();
            }
            LocalDateTime now = now();
            // 重试只在已证实仍待提交的同一订单上发生，并沿用挡板的原键原凭证幂等契约。
            if (result.state() == State.APPROVED
                    && attempts.claimWalletSubmission(attempt.getCheckoutAttemptId(), now.minusSeconds(30), now) == 1) {
                result = channel.submitApproved(order);
            }
            if (!Objects.equals(attempt.getChannelTransactionId(), result.transactionId())) {
                throw unavailable();
            }
        } catch (RuntimeException exception) {
            return browserState(session, attempt, State.PROCESSING, null);
        }
        if (isTerminal(result.state())) {
            complete(attempt, route, result.state());
        }
        PaymentCheckoutAttemptDO current = attempts.selectByCheckoutAttemptId(attempt.getCheckoutAttemptId());
        return browserState(session, current, persistedState(current), null);
    }

    public void recover(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt) {
        if (isSimulationAttempt(attempt) && supports(session, attempt.getPaymentMethod())
                && belongsTo(session, attempt) && isActive(session, attempt)) {
            synchronize(session, attempt);
        }
    }

    private void complete(PaymentCheckoutAttemptDO original, PaymentRouteResultDTO route, State terminal) {
        transactions.executeWithoutResult(tx -> {
            PaymentCheckoutSessionDO session = sessions.selectByCheckoutSessionIdForUpdate(original.getCheckoutSessionId());
            PaymentCheckoutAttemptDO attempt = attempts.selectByCheckoutAttemptId(original.getCheckoutAttemptId());
            if (!supports(session, original.getPaymentMethod()) || !isSimulationAttempt(attempt)
                    || !belongsTo(session, attempt)
                    || persistedState(attempt) != State.PROCESSING) {
                return;
            }
            LocalDateTime now = now();
            String attemptStatus = switch (terminal) {
                case SUCCESS -> "SUCCEEDED";
                case CANCELLED -> "ABANDONED";
                default -> "FAILED";
            };
            String failureCode = switch (terminal) {
                case FAILED -> "CHANNEL_DECLINED";
                case CANCELLED -> "PAYMENT_CANCELLED";
                case EXPIRED -> "PAYMENT_TIMEOUT";
                default -> null;
            };
            String failureMessage = switch (terminal) {
                case FAILED -> "The payment was declined by the payment provider.";
                case CANCELLED -> "The payment was cancelled before completion.";
                case EXPIRED -> "The payment authorization expired.";
                default -> null;
            };
            boolean active = isActive(session, attempt);
            boolean hasCore = StringUtils.hasText(attempt.getTransactionId());
            if (active && hasCore) {
                ledger.complete(session, attempt, route, terminal, failureCode, failureMessage);
            }
            int changed = attempts.markResultCas(attempt.getCheckoutAttemptId(), attemptStatus, "RESULT_RENDERED",
                    terminal.name(), null, null, failureCode, failureMessage, failureMessage,
                    null, attempt.getVersion(), now);
            if (changed != 1) {
                throw unavailable();
            }
            if (!active) {
                return;
            }
            int sessionChanged = terminal == State.SUCCESS
                    ? sessions.markSucceededCas(session.getCheckoutSessionId(), "RESULT_RENDERED",
                            attempt.getCheckoutAttemptId(), attempt.getTransactionId(), attempt.getOperationId(),
                            attempt.getTransactionDateTime(),
                            session.getVersion(), now)
                    : sessions.markFailedCas(session.getCheckoutSessionId(), "FAILED", "RESULT_RENDERED",
                            session.getVersion(), now);
            if (sessionChanged != 1) {
                throw unavailable();
            }
            if (!hasCore) {
                enqueueMerchantNotification(session, attempt, terminal, now);
            }
        });
    }

    private void enqueueMerchantNotification(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt,
                                             State terminal, LocalDateTime now) {
        String callbackUrl = session.getMerchantNotifyUrl();
        if (!StringUtils.hasText(callbackUrl)) {
            return;
        }
        String transactionId = firstText(attempt.getTransactionId(), attempt.getCheckoutAttemptId());
        String operationId = firstText(attempt.getOperationId(), attempt.getCheckoutAttemptId());
        String payload = JsonUtils.toJsonString(Map.of(
                "checkoutSessionId", session.getCheckoutSessionId(), "checkoutAttemptId", attempt.getCheckoutAttemptId(),
                "merchantInfo", Map.of("merchantId", session.getMerchantId()),
                "orderInfo", Map.of("orderNo", session.getMerchantOrderNo(), "amount", attempt.getLabelAmount(),
                        "currency", attempt.getLabelCurrency()),
                "transactionInfo", Map.of("transactionStatus", terminal.name(), "paymentMethod", attempt.getPaymentMethod())));
        var task = new TransactionMerchantNotificationDO();
        task.setNotifyId(PaymentOrderNoGenerator.nextOrderNo("NTF", now));
        task.setTransactionId(transactionId);
        task.setOperationId(operationId);
        task.setMerchantId(session.getMerchantId());
        task.setMerchantOrderNo(session.getMerchantOrderNo());
        task.setNotifyType("PAYMENT_RESULT");
        task.setEventType("CHECKOUT_PAYMENT_RESULT");
        task.setNotifyStatus("INIT");
        task.setCallbackUrl(callbackUrl);
        task.setPayloadJson(payload);
        task.setTargetUrlHash(com.scott.payment.component.web.internal.InternalServiceSignature.payloadSha256(callbackUrl));
        task.setTargetUrlMasked(maskUrl(callbackUrl));
        task.setPayloadJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(payload));
        task.setLastAttemptNo(0);
        task.setMaxRetryCount(notificationProperties.getMaxRetryCount());
        task.setNextRetryTime(now.plusSeconds(MerchantNotificationInitialDeliveryService.INITIAL_DELIVERY_DELAY_SECONDS));
        task.setTransactionDateTime(attempt.getTransactionDateTime());
        task.setTransactionUtcTime(attempt.getTransactionDateTime().atZone(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        task.setTransactionTimeZone(BUSINESS_ZONE.getId());
        task.setVersion(0);
        task.setDeleted(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        if (notifications.insertLogical(task) != 1) {
            throw unavailable();
        }
        delivery.schedule(task, 0, now);
    }

    private PaymentRouteResultDTO route(PaymentCheckoutSessionDO session, String method) {
        var command = new PaymentCreateCommandDTO();
        command.setMerchantId(session.getMerchantId());
        command.setCurrency(session.getLabelCurrency());
        command.setAmount(session.getLabelAmount());
        command.setTransactionType("PAYMENT");
        command.setRequestSource("HOSTED_CHECKOUT");
        command.setPaymentMethod(method);
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billing = StringUtils.hasText(session.getBillingInfoJson())
                ? JsonUtils.parseObject(session.getBillingInfoJson(), PaymentCreateCommandDTO.BillingCardHolderInfoDTO.class)
                : null;
        if (billing == null && StringUtils.hasText(session.getPayerCountry())) {
            billing = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
            billing.setCountry(session.getPayerCountry());
        }
        command.setBillingCardHolderInfo(billing);
        return requireRoute(routes.route(command), null);
    }

    /** 后续打开、查单和回调必须恢复原 MID，不能因商户路由调整而切换渠道身份。 */
    private PaymentRouteResultDTO restoreRoute(PaymentCheckoutAttemptDO attempt) {
        if (!isSimulationAttempt(attempt) || attempt.getChannelMidConfigId() == null) {
            throw unavailable();
        }
        return requireRoute(routes.restore(attempt.getChannelCode(), null, attempt.getChannelMidConfigId(), null),
                attempt.getChannelMidConfigId());
    }

    private PaymentRouteResultDTO requireRoute(PaymentRouteResultDTO route, Long expectedMidConfigId) {
        if (route == null || !route.isRouted()
                || !channel.supportsRoute(route.getChannelCode(), route.getRequestUrl(), route.getMidNo())
                || route.getMidConfigId() == null
                || (expectedMidConfigId != null && !expectedMidConfigId.equals(route.getMidConfigId()))
                || route.getMetadataValues() == null
                || !StringUtils.hasText(route.getMetadataValues().get("apiKey"))) {
            throw unavailable();
        }
        return route;
    }

    private Order order(PaymentCheckoutAttemptDO attempt, PaymentRouteResultDTO route) {
        BigDecimal storedAmount = attempt.getChannelRequestAmount();
        String currency = attempt.getChannelRequestCurrency();
        if (!Objects.equals(attempt.getChannelMidConfigId(), route.getMidConfigId()) || storedAmount == null) {
            throw unavailable();
        }
        BigDecimal channelAmount;
        try {
            // 回读金额可能被 DECIMAL 补齐尾零，重放时沿用原渠道币种精度和已冻结的数值。
            channelAmount = storedAmount.setScale(currencyExponent(currency), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw unavailable();
        }
        return new Order(route.getMetadataValues().get("apiKey"), route.getMidNo(), attempt.getChannelOrderNo(),
                channelAmount, currency, attempt.getPaymentMethod());
    }

    private Map<String, String> browserState(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt,
                                             State state, String url) {
        if (url != null) {
            return Map.of("state", state.name(), "redirectUrl", url);
        }
        return Map.of("state", state.name(), "paymentMethod", attempt.getPaymentMethod(),
                "merchantOrderNo", session.getMerchantOrderNo(), "currency", attempt.getLabelCurrency(),
                "amount", formatAmount(attempt.getLabelAmount(), attempt.getLabelCurrency()));
    }

    /** 钱包沿用支付核心 EDC 方向：标签币种乘以交易汇率，且只在渠道目标币种精度处舍入一次。 */
    private ChannelAmountPlan channelAmountPlan(PaymentCheckoutSessionDO session, PaymentRouteResultDTO route,
                                                LocalDateTime rateTime) {
        String labelCurrency = normalizeCurrency(session.getLabelCurrency());
        String routedCurrency = normalizeCurrency(route.getRoutedCurrency());
        if (!StringUtils.hasText(routedCurrency)) {
            if (route.isEdcRequired()) {
                throw unavailable();
            }
            routedCurrency = labelCurrency;
        }
        BigDecimal channelAmount = session.getLabelAmount();
        int labelExponent = currencyExponent(labelCurrency);
        int channelExponent = labelCurrency.equals(routedCurrency) ? labelExponent : currencyExponent(routedCurrency);
        BigDecimal lockedRate = BigDecimal.ONE;
        String rateSource = null;
        LocalDateTime lockedRateTime = null;
        if (!labelCurrency.equals(routedCurrency)) {
            PaymentExchangeRateDTO rate = exchangeRates.findTransactionRate(labelCurrency, routedCurrency, rateTime)
                    .orElseThrow(CheckoutWalletSimulationService::unavailable);
            if (rate.getRateId() == null || rate.getRateId() <= 0 || rate.getFinalRate() == null
                    || rate.getFinalRate().signum() <= 0) {
                throw unavailable();
            }
            lockedRate = rate.getFinalRate();
            rateSource = rate.getSourceCode();
            lockedRateTime = rate.getEffectiveTime();
            channelAmount = channelAmount.multiply(lockedRate).setScale(channelExponent, RoundingMode.HALF_UP);
        } else {
            channelAmount = channelAmount.setScale(channelExponent, RoundingMode.UNNECESSARY);
        }
        if (!validChannelAmount(channelAmount, routedCurrency)) {
            throw unavailable();
        }
        return new ChannelAmountPlan(routedCurrency, channelAmount,
                new CheckoutWalletTransactionLedger.Quote(
                        labelExponent, channelExponent, lockedRate, rateSource, lockedRateTime));
    }

    private boolean validChannelAmount(BigDecimal amount, String currency) {
        return validOrderAmount(amount, currency) && amount.compareTo(MAX_AMOUNT) <= 0;
    }

    private boolean validOrderAmount(BigDecimal amount, String currency) {
        return amount != null && amount.signum() > 0
                && isoDictionary.isCurrencyFractionValid(amount, normalizeCurrency(currency));
    }

    private int currencyExponent(String currency) {
        return isoDictionary.getCurrency(currency)
                .filter(value -> value.defaultFractionDigits() >= 0)
                .orElseThrow(CheckoutWalletSimulationService::unavailable)
                .defaultFractionDigits();
    }

    private String formatAmount(BigDecimal amount, String currency) {
        return amount.setScale(currencyExponent(currency), RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private record ChannelAmountPlan(String currency, BigDecimal amount, CheckoutWalletTransactionLedger.Quote quote) {
    }

    private State persistedState(PaymentCheckoutAttemptDO attempt) {
        return switch (Objects.toString(attempt.getChannelStatus(), "")) {
            case "SUCCESS" -> State.SUCCESS;
            case "FAILED" -> State.FAILED;
            case "CANCELLED" -> State.CANCELLED;
            case "EXPIRED" -> State.EXPIRED;
            default -> State.PROCESSING;
        };
    }

    private boolean isActive(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt) {
        return Objects.equals(attempt.getCheckoutAttemptId(), session.getLastAttemptId());
    }

    private boolean belongsTo(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt) {
        return session != null && attempt != null
                && Objects.equals(session.getCheckoutSessionId(), attempt.getCheckoutSessionId())
                && Objects.equals(session.getMerchantId(), attempt.getMerchantId());
    }

    private String returnToken(PaymentCheckoutAttemptDO attempt) {
        String id = attempt.getCheckoutAttemptId();
        return id + "." + PaymentCheckoutTokenSupport.hmacSha256Hex(
                "dev-wallet-return:" + id + ":" + attempt.getCheckoutSessionId(), properties.getTokenPepper());
    }

    /** 渠道浏览器回跳必须沿用付款人进入收银台的开发主机，否则局域网设备会被错误带回自身回环地址。 */
    private String channelReturnUrl(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt) {
        if (!allowedDevelopmentCheckoutOrigin(session.getCheckoutDomain())) {
            throw unavailable();
        }
        String checkoutHost = URI.create(session.getCheckoutDomain()).getHost();
        String gatewayHost = "localhost".equals(checkoutHost) ? "127.0.0.1" : checkoutHost;
        return "http://" + gatewayHost + ":8000" + CHANNEL_RETURN_PATH + "?returnToken=" + returnToken(attempt);
    }

    private static boolean isTerminal(State state) {
        return state == State.SUCCESS || state == State.FAILED || state == State.EXPIRED || state == State.CANCELLED;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String maskUrl(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }

    /** dev 收银台允许 localhost 及 RFC1918 局域网 IP 的固定 Vite 端口，拒绝公网和任意路径。 */
    private static boolean allowedDevelopmentCheckoutOrigin(String value) {
        try {
            URI origin = URI.create(value);
            String host = origin.getHost();
            if (!"http".equals(origin.getScheme()) || host == null || origin.getPort() != 5175
                    || origin.getUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
                return false;
            }
            return "localhost".equals(host) || "127.0.0.1".equals(host) || privateIpv4(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean privateIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] < 0 || octets[index] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException exception) {
            return false;
        }
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private static ServiceException unavailable() {
        return new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
    }

    private static ServiceException unauthorized() {
        return new ServiceException(ApiResultEnum.UNAUTHORIZED);
    }
}

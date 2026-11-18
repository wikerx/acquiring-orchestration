package com.scott.payment.payment.service.link;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.domain.link.PaymentLinkStatus;
import com.scott.payment.payment.entity.*;
import com.scott.payment.payment.mapper.*;
import com.scott.payment.payment.service.PaymentCheckoutService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** 链接领域命令；创建/停用/开始只做本地事务，资金动作仍从现有 Checkout 发起。 */
@Service
public class PaymentLinkService {
    private final PaymentLinkMapper links;
    private final PaymentLinkAccessMapper access;
    private final PaymentLinkCollectionMapper collections;
    private final PaymentLinkCheckoutBindingMapper bindings;
    private final PaymentLinkEventMapper events;
    private final PaymentCheckoutSessionMapper sessions;
    private final PaymentCheckoutService checkout;
    private final TransactionIdempotencyService idempotency;
    private final PaymentLinkPolicy policy;
    private final PaymentLinkQueryService queries;
    private final PaymentLinkTokenService tokens;
    private final PaymentLinkProperties properties;
    private final GlobalIdGenerator ids;

    public PaymentLinkService(PaymentLinkMapper links, PaymentLinkAccessMapper access,
                               PaymentLinkCollectionMapper collections, PaymentLinkCheckoutBindingMapper bindings,
                               PaymentLinkEventMapper events, PaymentCheckoutSessionMapper sessions,
                               PaymentCheckoutService checkout, TransactionIdempotencyService idempotency,
                               PaymentLinkPolicy policy, PaymentLinkQueryService queries,
                               PaymentLinkTokenService tokens, PaymentLinkProperties properties, GlobalIdGenerator ids) {
        this.links = links; this.access = access; this.collections = collections; this.bindings = bindings;
        this.events = events; this.sessions = sessions; this.checkout = checkout; this.idempotency = idempotency;
        this.policy = policy; this.queries = queries; this.tokens = tokens; this.properties = properties; this.ids = ids;
    }

    // 仅新建用例使用已提交快照：并发唯一键冲突后，详情必须能读到另一事务刚提交的原链接。
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public View create(Actor actor, String merchantId, Create input) {
        policy.requireEnabled();
        policy.requireScope(actor, merchantId);
        BigDecimal amount = policy.validate(input);
        var merchant = policy.merchant(merchantId);
        String fingerprint = fingerprint(input, amount);
        PaymentLinkDO existing = links.selectOne(Wrappers.<PaymentLinkDO>lambdaQuery()
                .eq(PaymentLinkDO::getMerchantId, merchantId).eq(PaymentLinkDO::getCreateRequestId, input.getRequestId()));
        if (existing != null) {
            requireFingerprint(existing, fingerprint);
            return queries.detail(actor, existing.getLinkId());
        }
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods =
                policy.methods(merchantId, input.getCurrency(), input.getCardBrands());
        String orderNo = input.getMerchantOrderNo() == null || input.getMerchantOrderNo().isBlank()
                ? ids.nextId() : input.getMerchantOrderNo().trim();
        if (!orderNo.matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        idempotency.find("MERCHANT_ORDER_FLOW", idempotency.buildMerchantOrderFlowKey(merchantId, orderNo))
                .ifPresent(flow -> { throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT); });
        if (sessions.selectCount(Wrappers.<PaymentCheckoutSessionDO>lambdaQuery()
                .eq(PaymentCheckoutSessionDO::getMerchantId, merchantId)
                .eq(PaymentCheckoutSessionDO::getMerchantOrderNo, orderNo)
                .eq(PaymentCheckoutSessionDO::getDeleted, 0)) > 0) {
            // 已有托管会话也属于原订单入口，不能再建立第二个独立链接目标。
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
        }
        LocalDateTime now = now();
        PaymentLinkDO link = new PaymentLinkDO();
        link.setLinkId("L" + ids.nextId());
        link.setMerchantId(merchantId); link.setMerchantOrderNo(orderNo);
        link.setLinkName(input.getLinkName() == null ? "" : input.getLinkName().trim()); link.setDescription(blankToNull(input.getDescription()));
        link.setCurrency(input.getCurrency()); link.setAmount(amount); link.setCurrencyExponent(policy.exponent(input.getCurrency()));
        link.setAllowedBrandsJson(JsonUtils.toJsonString(methods.stream().flatMap(method -> method.getBrands().stream()).distinct().sorted().toList()));
        link.setMerchantDisplayName(merchant.getMerchantName()); link.setLocale(input.getLocale());
        link.setNotifyUrl(blankToNull(input.getNotifyUrl())); link.setRedirectUrl(blankToNull(input.getRedirectUrl()));
        link.setStatus(PaymentLinkStatus.ACTIVE.name());
        link.setExpireTime(PaymentLinkExpiryPolicy.resolve(input.getExpireMinutes(), input.getExpireTime(), now));
        link.setCreateRequestId(input.getRequestId()); link.setRequestFingerprint(fingerprint);
        link.setCreatedAppCode(actor.getAppCode()); link.setCreatedBy(actor.getActorId());
        link.setVersion(0); link.setCreateTime(now); link.setUpdateTime(now);
        try {
            links.insert(link);
        } catch (DuplicateKeyException exception) {
            // 当前读防止 REPEATABLE READ 的旧快照漏掉刚由另一请求提交的幂等记录。
            existing = links.findRequestForUpdate(merchantId, input.getRequestId());
            if (existing == null) {
                throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
            }
            requireFingerprint(existing, fingerprint);
            return queries.detail(actor, existing.getLinkId());
        }
        PaymentLinkAccessDO credential = new PaymentLinkAccessDO();
        credential.setLinkId(link.getLinkId()); credential.setKeyVersion(properties.getActiveKeyVersion());
        credential.setTokenHash(PaymentLinkTokenService.hash(tokens.token(link, credential.getKeyVersion())));
        credential.setCreateTime(now); access.insert(credential);
        event(link, "CREATED", actor, null);
        return queries.detail(actor, link.getLinkId());
    }

    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public View disable(Actor actor, String linkId, Integer version, String reason, String requestId) {
        policy.requireEnabled();
        PaymentLinkDO link = lock(linkId);
        policy.requireScope(actor, link.getMerchantId());
        if (version == null || reason == null || reason.isBlank() || reason.length() > 500
                || requestId == null || !requestId.matches("[A-Za-z0-9_.:-]{1,80}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String actorIdentity = actor.getAppCode() + ":" + actor.getActorId();
        if (PaymentLinkStatus.DISABLED.name().equals(link.getStatus())) {
            if (requestId.equals(link.getDisableRequestId())
                    && (!reason.trim().equals(link.getDisableReason()) || !actorIdentity.equals(link.getDisabledBy()))) {
                throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
            }
            return queries.detail(actor, linkId);
        }
        if (!version.equals(link.getVersion())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        View current = queries.detail(actor, linkId);
        if (!PaymentLinkStatus.ACTIVE.name().equals(current.getStatus())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        LocalDateTime now = now();
        int changed = links.update(null, Wrappers.<PaymentLinkDO>lambdaUpdate()
                .eq(PaymentLinkDO::getLinkId, linkId).eq(PaymentLinkDO::getVersion, version)
                .eq(PaymentLinkDO::getStatus, PaymentLinkStatus.ACTIVE.name())
                .set(PaymentLinkDO::getStatus, PaymentLinkStatus.DISABLED.name())
                .set(PaymentLinkDO::getDisabledBy, actorIdentity).set(PaymentLinkDO::getDisableReason, reason.trim())
                .set(PaymentLinkDO::getDisableRequestId, requestId)
                .set(PaymentLinkDO::getDisabledTime, now).set(PaymentLinkDO::getUpdateTime, now)
                .set(PaymentLinkDO::getVersion, version + 1));
        if (changed != 1) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        event(link, "DISABLED", actor, reason.trim());
        return queries.detail(actor, linkId);
    }

    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Share share(Actor actor, String linkId) {
        policy.requireEnabled();
        PaymentLinkDO link = lock(linkId);
        policy.requireScope(actor, link.getMerchantId());
        requireEntryActive(link);
        if ("SUCCESS".equals(paymentStatus(linkId))) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        Share result = new Share();
        result.setUrl(tokens.url(link, credential(linkId).getKeyVersion()));
        event(link, "SHARED", actor, null);
        return result;
    }

    @DS(DataSourceName.MASTER)
    public PublicView resolve(String opaqueToken) {
        policy.requireEnabled();
        PaymentLinkDO link = authenticate(opaqueToken);
        String paymentStatus = paymentStatus(link.getLinkId());
        String status = "SUCCESS".equals(paymentStatus) ? PaymentLinkStatus.COMPLETED.name() : effectiveStatus(link, paymentStatus);
        PublicView result = new PublicView();
        result.setLinkName(link.getLinkName()); result.setDescription(link.getDescription());
        result.setMerchantDisplayName(link.getMerchantDisplayName());
        result.setCurrency(link.getCurrency()); result.setCurrencyExponent(link.getCurrencyExponent());
        result.setAmount(link.getAmount().setScale(link.getCurrencyExponent(), java.math.RoundingMode.UNNECESSARY).toPlainString());
        result.setLocale(link.getLocale()); result.setStatus(status);
        result.setPayable(PaymentLinkStatus.ACTIVE.name().equals(status) && sessionPayable(link.getLinkId()) && merchantAvailable(link.getMerchantId()));
        result.setProcessing("PROCESSING".equals(paymentStatus));
        // 会话当前仍处理中/已成功时不投影更早失败，避免把未知结果误判成失败。
        if ("FAILED".equals(paymentStatus)) {
            List<Attempt> latest = links.attempts(link.getLinkId(), 0, 1);
            if (!latest.isEmpty() && "FAILED".equals(latest.get(0).getStatus())) {
                result.setLastFailedTime(latest.get(0).getCompleteTime());
            } else if (!latest.isEmpty() && "PROCESSING".equals(latest.get(0).getStatus())) {
                result.setProcessing(true);
                result.setPayable(false);
            }
        }
        result.setExpireTime(link.getExpireTime()); result.setServerTime(now());
        return result;
    }

    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Started start(String opaqueToken, String requestId) {
        policy.requireEnabled();
        if (requestId == null || !requestId.matches("[A-Za-z0-9_.:-]{1,80}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentLinkDO link = lock(authenticate(opaqueToken).getLinkId());
        requireEntryActive(link);
        if (!sessionPayable(link.getLinkId())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        policy.merchant(link.getMerchantId());
        var methods = policy.currentMethods(link.getMerchantId(), link.getCurrency(), JsonUtils.parseArray(link.getAllowedBrandsJson(), String.class));
        PaymentCheckoutSessionCreateCommandDTO command = new PaymentCheckoutSessionCreateCommandDTO();
        command.setMerchantId(link.getMerchantId()); command.setMerchantOrderNo(link.getMerchantOrderNo());
        command.setMerchantRequestId("PAYMENT_LINK:" + link.getLinkId());
        command.setRequestFingerprint(link.getRequestFingerprint()); command.setPaymentAction("PAYMENT");
        command.setAmount(link.getAmount()); command.setCurrency(link.getCurrency()); command.setCurrencyExponent(link.getCurrencyExponent());
        command.setOrderSubject(null); command.setOrderDescription(link.getDescription());
        command.setAllowedPaymentMethods(methods); command.setCheckoutDomain(properties.getFrontendBaseUrl());
        command.setLocale("AUTO".equals(link.getLocale()) ? null : link.getLocale());
        command.setMerchantDisplayName(link.getMerchantDisplayName()); command.setMerchantNotifyUrl(link.getNotifyUrl());
        command.setRedirectUrl(link.getRedirectUrl()); command.setRetryAllowed(1); command.setExpireTime(link.getExpireTime());
        command.setRequestSource("PAY_BY_LINK");
        var session = checkout.createSession(command);
        PaymentLinkCheckoutBindingDO binding = binding(link.getLinkId());
        if (binding == null) {
            PaymentLinkCollectionDO collection = new PaymentLinkCollectionDO();
            collection.setCollectionId(ids.nextId()); collection.setLinkId(link.getLinkId());
            collection.setMerchantId(link.getMerchantId()); collection.setMerchantOrderNo(link.getMerchantOrderNo());
            collection.setSubmissionDeadline(link.getExpireTime()); collection.setCreateTime(now());
            collections.insert(collection);
            binding = new PaymentLinkCheckoutBindingDO();
            binding.setLinkId(link.getLinkId()); binding.setCollectionId(collection.getCollectionId());
            binding.setCheckoutSessionId(session.getCheckoutSessionId()); binding.setCreateTime(now());
            bindings.insert(binding);
            Actor payer = new Actor(); payer.setAppCode("PAYER"); payer.setActorId("LINK_HOLDER");
            event(link, "CHECKOUT_STARTED", payer, null);
        } else if (!binding.getCheckoutSessionId().equals(session.getCheckoutSessionId())) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
        }
        Started result = new Started();
        result.setCheckoutUrl(session.getCheckoutUrl()); result.setCheckoutSessionId(session.getCheckoutSessionId());
        return result;
    }

    private PaymentLinkDO authenticate(String supplied) {
        if (supplied == null || !supplied.matches("(?:L[0-9]{22}|[0-9]{22}|[a-f0-9]{32})\\.[a-f0-9]{64}")) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        String linkId = supplied.substring(0, supplied.indexOf('.'));
        PaymentLinkAccessDO stored = credential(linkId);
        if (!tokens.matches(supplied, stored.getTokenHash())) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        PaymentLinkDO link = links.selectOne(Wrappers.<PaymentLinkDO>lambdaQuery().eq(PaymentLinkDO::getLinkId, linkId));
        if (link == null) { throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND); }
        return link;
    }

    private PaymentLinkAccessDO credential(String linkId) {
        PaymentLinkAccessDO result = access.selectOne(Wrappers.<PaymentLinkAccessDO>lambdaQuery().eq(PaymentLinkAccessDO::getLinkId, linkId));
        if (result == null) { throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND); }
        return result;
    }

    private PaymentLinkCheckoutBindingDO binding(String linkId) {
        return bindings.selectOne(Wrappers.<PaymentLinkCheckoutBindingDO>lambdaQuery().eq(PaymentLinkCheckoutBindingDO::getLinkId, linkId));
    }

    private boolean merchantAvailable(String merchantId) {
        try { policy.merchant(merchantId); return true; }
        catch (ServiceException exception) {
            if (ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE.getCode().equals(exception.getCode())) return false;
            throw exception;
        }
    }

    private boolean sessionPayable(String linkId) {
        PaymentLinkCheckoutBindingDO binding = binding(linkId);
        if (binding == null) return true;
        return com.scott.payment.payment.domain.state.PaymentCheckoutPayability.mayStart(
                sessions.selectByCheckoutSessionId(binding.getCheckoutSessionId()), now());
    }

    private String paymentStatus(String linkId) {
        PaymentLinkCheckoutBindingDO binding = binding(linkId);
        if (binding == null) { return "PENDING"; }
        PaymentCheckoutSessionDO session = sessions.selectByCheckoutSessionId(binding.getCheckoutSessionId());
        if (session == null) { throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND); }
        return session.getCheckoutStatus();
    }

    private PaymentLinkDO lock(String linkId) {
        PaymentLinkDO result = links.lock(linkId);
        if (result == null) { throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND); }
        return result;
    }

    private void requireEntryActive(PaymentLinkDO link) {
        if (!PaymentLinkStatus.ACTIVE.name().equals(link.getStatus()) || !link.getExpireTime().isAfter(now())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
    }

    private String effectiveStatus(PaymentLinkDO link, String paymentStatus) {
        if (PaymentLinkStatus.DISABLED.name().equals(link.getStatus())) { return PaymentLinkStatus.DISABLED.name(); }
        if ("SUCCESS".equals(paymentStatus)) { return PaymentLinkStatus.COMPLETED.name(); }
        if (!link.getExpireTime().isAfter(now())) { return PaymentLinkStatus.EXPIRED.name(); }
        return link.getStatus();
    }

    private void event(PaymentLinkDO link, String type, Actor actor, String reason) {
        PaymentLinkEventDO event = new PaymentLinkEventDO();
        event.setEventId(ids.nextId()); event.setLinkId(link.getLinkId()); event.setEventType(type);
        event.setActorAppCode(actor.getAppCode()); event.setActorId(actor.getActorId());
        event.setReason(reason); event.setCreateTime(now()); events.insert(event);
    }

    private String fingerprint(Create input, BigDecimal amount) {
        var fields = new TreeMap<String, Object>();
        fields.put("order", blankToNull(input.getMerchantOrderNo())); fields.put("name", input.getLinkName() == null ? "" : input.getLinkName().trim());
        fields.put("description", blankToNull(input.getDescription())); fields.put("amount", amount.stripTrailingZeros().toPlainString());
        fields.put("currency", input.getCurrency()); fields.put("expiry", input.getExpireTime() == null
                ? (input.getExpireMinutes() == null ? 1440 : input.getExpireMinutes())
                : input.getExpireTime().toString());
        fields.put("locale", input.getLocale()); fields.put("brands", input.getCardBrands() == null ? List.of() : input.getCardBrands().stream().distinct().sorted().toList());
        fields.put("notify", blankToNull(input.getNotifyUrl())); fields.put("redirect", blankToNull(input.getRedirectUrl()));
        return PaymentLinkTokenService.hash(JsonUtils.toJsonString(fields));
    }

    private void requireFingerprint(PaymentLinkDO row, String fingerprint) {
        if (!fingerprint.equals(row.getRequestFingerprint())) { throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS); }
}

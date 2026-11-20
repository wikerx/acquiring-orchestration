package com.scott.payment.payment.service.link;

import com.scott.payment.link.entity.PaymentLinkEventDO;

import com.scott.payment.link.entity.PaymentLinkAccessDO;

import com.scott.payment.link.entity.PaymentLinkDO;

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
import com.scott.payment.link.model.PaymentLinkStatus;
import com.scott.payment.payment.entity.*;
import com.scott.payment.payment.mapper.*;
import com.scott.payment.payment.service.PaymentCheckoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

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
    private final PaymentLinkPolicy policy;
    private final PaymentLinkQueryService queries;
    private final com.scott.payment.link.service.PaymentLinkTokenService tokens;
    private final com.scott.payment.link.service.PaymentLinkManagementService management;
    private final PaymentLinkProperties properties;
    private final GlobalIdGenerator ids;

    public PaymentLinkService(PaymentLinkMapper links, PaymentLinkAccessMapper access,
                               PaymentLinkCollectionMapper collections, PaymentLinkCheckoutBindingMapper bindings,
                               PaymentLinkEventMapper events, PaymentCheckoutSessionMapper sessions,
                               PaymentCheckoutService checkout,
                               PaymentLinkPolicy policy, PaymentLinkQueryService queries,
                               com.scott.payment.link.service.PaymentLinkTokenService tokens, PaymentLinkProperties properties, GlobalIdGenerator ids,
                               com.scott.payment.link.service.PaymentLinkManagementService management) {
        this.management = management;
        this.links = links; this.access = access; this.collections = collections; this.bindings = bindings;
        this.events = events; this.sessions = sessions; this.checkout = checkout;
        this.policy = policy; this.queries = queries; this.tokens = tokens; this.properties = properties; this.ids = ids;
    }

    /** 兼容旧内部入口；与双端本地管理操作共用唯一规则实现。 */
    public View create(Actor actor, String merchantId, Create input) {
        return queries.detail(actor, management.create(actor, merchantId, input));
    }
    /** 兼容旧内部停用入口，复用共享版本与幂等规则后读取授权详情。 */
    public View disable(Actor actor, String linkId, Integer version, String reason, String requestId) {
        return queries.detail(actor, management.disable(actor, linkId, version, reason, requestId));
    }
    /** 兼容旧内部分享入口，凭据只通过独立授权操作返回。 */
    public Share share(Actor actor, String linkId) {
        Share result = new Share(); result.setUrl(management.share(actor, linkId)); return result;
    }

    /** 认证持有人凭据并投影当前可支付状态；只暴露最近确认失败的时间，不返回其他付款人信息。 */
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

    /** 锁定链接并复验当前能力后复用收银台会话；固定商户请求号防止重复进入产生独立收款。 */
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

    private LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS); }
}

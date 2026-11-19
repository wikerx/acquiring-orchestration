package com.scott.payment.link.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.link.config.PaymentLinkSettings;
import com.scott.payment.link.entity.*;
import com.scott.payment.link.mapper.*;
import com.scott.payment.link.model.PaymentLinkManagementModels.*;
import com.scott.payment.link.model.PaymentLinkStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 三端本地共用的链接管理规则；不创建 Checkout，不调用渠道，不变更交易状态或历史数据。 */
@Service
public class PaymentLinkManagementService {
    private final PaymentLinkManagementMapper links;
    private final PaymentLinkManagementAccessMapper access;
    private final PaymentLinkManagementEventMapper events;
    private final PaymentLinkManagementPolicy policy;
    private final PaymentLinkTokenService tokens;
    private final PaymentLinkSettings properties;
    private final GlobalIdGenerator ids;
    public PaymentLinkManagementService(PaymentLinkManagementMapper links, PaymentLinkManagementAccessMapper access,
            PaymentLinkManagementEventMapper events, @org.springframework.beans.factory.annotation.Qualifier("paymentLinkManagementPolicy") PaymentLinkManagementPolicy policy, PaymentLinkTokenService tokens,
            PaymentLinkSettings properties, GlobalIdGenerator ids) {
        this.links = links; this.access = access; this.events = events; this.policy = policy;
        this.tokens = tokens; this.properties = properties; this.ids = ids;
    }

    // 仅新建用例使用已提交快照：并发唯一键冲突后，详情必须能读到另一事务刚提交的原链接。
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public String create(Actor actor, String merchantId, Create input) {
        policy.requireEnabled();
        policy.requireScope(actor, merchantId);
        BigDecimal amount = policy.validate(input);
        var merchant = policy.merchant(merchantId);
        String fingerprint = PaymentLinkCreationFingerprint.canonical(input, amount);
        PaymentLinkDO existing = links.selectOne(Wrappers.<PaymentLinkDO>lambdaQuery()
                .eq(PaymentLinkDO::getMerchantId, merchantId).eq(PaymentLinkDO::getCreateRequestId, input.getRequestId()));
        if (existing != null) {
            PaymentLinkCreationFingerprint.requireMatch(existing, input, amount);
            return existing.getLinkId();
        }
        List<AllowedMethod> methods =
                policy.allowedMethods(merchantId, input.getCurrency(), input.getCardBrands());
        String orderNo = input.getMerchantOrderNo() == null || input.getMerchantOrderNo().isBlank()
                ? ids.nextId() : input.getMerchantOrderNo().trim();
        if (!orderNo.matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (links.countOrderFlow((merchantId.trim() + ":" + orderNo.trim()).toUpperCase(java.util.Locale.ROOT)) > 0
                || links.countSession(merchantId, orderNo) > 0) {
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
            PaymentLinkCreationFingerprint.requireMatch(existing, input, amount);
            return existing.getLinkId();
        }
        PaymentLinkAccessDO credential = new PaymentLinkAccessDO();
        credential.setLinkId(link.getLinkId()); credential.setKeyVersion(properties.getActiveKeyVersion());
        credential.setTokenHash(PaymentLinkTokenService.hash(tokens.token(link, credential.getKeyVersion())));
        credential.setCreateTime(now); access.insert(credential);
        event(link, "CREATED", actor, null);
        return link.getLinkId();
    }

    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public String disable(Actor actor, String linkId, Integer version, String reason, String requestId) {
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
            return linkId;
        }
        if (!version.equals(link.getVersion())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (!link.getExpireTime().isAfter(now()) || "SUCCESS".equals(links.paymentStatus(linkId))) {
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
        event(link, PaymentLinkStatus.DISABLED.name(), actor, reason.trim());
        return linkId;
    }

    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public String share(Actor actor, String linkId) {
        policy.requireEnabled();
        PaymentLinkDO link = lock(linkId);
        policy.requireScope(actor, link.getMerchantId());
        requireEntryActive(link);
        if ("SUCCESS".equals(links.paymentStatus(linkId))) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        String result = tokens.url(link, credential(linkId).getKeyVersion());
        event(link, "SHARED", actor, null);
        return result;
    }

    private PaymentLinkDO lock(String linkId) {
        if (linkId == null || linkId.isBlank() || linkId.length() > 32) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        PaymentLinkDO link = links.lock(linkId);
        if (link == null) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        return link;
    }
    private PaymentLinkAccessDO credential(String linkId) {
        PaymentLinkAccessDO row = access.selectOne(Wrappers.<PaymentLinkAccessDO>lambdaQuery().eq(PaymentLinkAccessDO::getLinkId, linkId));
        if (row == null) throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE);
        return row;
    }
    private void requireEntryActive(PaymentLinkDO link) {
        if (!PaymentLinkStatus.ACTIVE.name().equals(link.getStatus()) || !link.getExpireTime().isAfter(now()))
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
    }
    private void event(PaymentLinkDO link, String type, Actor actor, String reason) {
        PaymentLinkEventDO event = new PaymentLinkEventDO(); event.setEventId(ids.nextId()); event.setLinkId(link.getLinkId());
        event.setEventType(type); event.setActorAppCode(actor.getAppCode()); event.setActorId(actor.getActorId());
        event.setReason(reason); event.setCreateTime(now()); events.insert(event);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS); }
}

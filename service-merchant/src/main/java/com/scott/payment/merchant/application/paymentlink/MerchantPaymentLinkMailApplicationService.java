package com.scott.payment.merchant.application.paymentlink;

import com.scott.payment.merchant.config.MerchantPaymentLinkMailProperties;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.merchant.entity.paymentlink.PaymentLinkDeliveryDO;
import com.scott.payment.merchant.mapper.PaymentLinkDeliveryMapper;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** 单收件人邀请；付款链接独立成功，发送失败不修改链接或交易。 */
@Service
public class MerchantPaymentLinkMailApplicationService {
    private final MerchantPaymentLinkApplicationService links;
    private final PaymentLinkDeliveryMapper requests;
    private final MerchantTemplateEmailService emails;
    private final RedisSlidingWindowRateLimiter limiter;
    private final MerchantPaymentLinkMailProperties properties;
    private final Validator validator;
    private final MerchantPaymentLinkDeliveryClaimService claims;
    public MerchantPaymentLinkMailApplicationService(MerchantPaymentLinkApplicationService links, PaymentLinkDeliveryMapper requests,
                                  MerchantTemplateEmailService emails, RedisSlidingWindowRateLimiter limiter,
                                  MerchantPaymentLinkMailProperties properties, Validator validator, MerchantPaymentLinkDeliveryClaimService claims) {
        this.links=links; this.requests=requests; this.emails=emails; this.limiter=limiter; this.properties=properties; this.validator=validator; this.claims=claims;
    }

    /** 先校验链接归属，再锁定邀请占位并核对指纹；按稳定业务号复用已提交邮件，限流失败不改写链接。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Delivery send(String linkId, SendEmail input) {
        if (input==null || !validator.validate(input).isEmpty()) { throw new ServiceException(ApiResultEnum.PARAM_INVALID); }
        View view=links.detail(linkId); Actor actor=links.actor();
        String fingerprint=hash(linkId+"\n"+input.email().trim()+"\n"+input.locale());
        PaymentLinkDeliveryDO proposed=new PaymentLinkDeliveryDO();
        proposed.setMerchantId(view.getMerchantId()); proposed.setLinkId(linkId); proposed.setRequestId(input.requestId());
        proposed.setFingerprint(fingerprint); proposed.setRecipientHash(hash(input.email().trim().toLowerCase(java.util.Locale.ROOT)));
        proposed.setRecipientMasked(mask(input.email().trim())); proposed.setLocale(input.locale());
        proposed.setBizNo(hash("MERCHANT|"+view.getMerchantId()+"|"+input.requestId()));
        proposed.setCreatedBy(actor.getActorId()); proposed.setCreateTime(LocalDateTime.now());
        claims.claim(proposed);
        PaymentLinkDeliveryDO row=requests.lock(view.getMerchantId(),input.requestId());
        if (row==null || !fingerprint.equals(row.getFingerprint())) { throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT); }
        if (row.getEmailRecordId()==null) {
            Long committed=claims.findCommittedEmail(view.getMerchantId(),row.getBizNo());
            if (committed==null) {
                quota("PAYMENT_LINK_MAIL_RECIPIENT",view.getMerchantId()+":"+row.getRecipientHash(),1,Duration.ofSeconds(properties.getRecipientCooldownSeconds()));
                quota("PAYMENT_LINK_MAIL_MERCHANT",view.getMerchantId(),properties.getMerchantHourlyLimit(),Duration.ofHours(1));
                quota("PAYMENT_LINK_MAIL_ACTOR",actor.getAppCode()+":"+actor.getActorId(),properties.getActorHourlyLimit(),Duration.ofHours(1));
                String url=links.share(linkId).getUrl();
                committed=prepare(view,row,input,url);
            }
            if (committed==null) { throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR); }
            row.setEmailRecordId(committed); requests.updateById(row);
        }
        return projection(row);
    }

    /** 校验链接可见性和分页上限后合并邀请历史，补充操作者展示名称。 */
    @DS(DataSourceName.MASTER)
    public PageResult<Delivery> page(String linkId, Query input) {
        View view=links.detail(linkId); Query query=input==null?new Query():input;
        if(query.getPageNo()<1 || query.getPageNo()>1_000_000 || query.getPageSize()<1 || query.getPageSize()>100) { throw new ServiceException(ApiResultEnum.PARAM_INVALID); }
        return links.deliveryOperators(PageResult.of(requests.count(view.getMerchantId(),linkId),query.getPageNo(),query.getPageSize(),
                requests.page(view.getMerchantId(),linkId,query.getPageSize(),(long)(query.getPageNo()-1)*query.getPageSize())));
    }

    private Long prepare(View view, PaymentLinkDeliveryDO row, SendEmail input, String url) {
        var variables=new LinkedHashMap<String,Object>();
        variables.put("merchantName",view.getMerchantDisplayName()); variables.put("linkName",view.getLinkName() == null || view.getLinkName().isBlank()
                ? view.getMerchantOrderNo() : view.getLinkName());
        variables.put("orderNo",view.getMerchantOrderNo()); variables.put("amount",new java.math.BigDecimal(view.getAmount()).stripTrailingZeros().toPlainString()); variables.put("currency",view.getCurrency());
        variables.put("description",view.getDescription()==null?"":view.getDescription());
        variables.put("expireTime",view.getExpireTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+" (UTC+08:00)");
        variables.put("paymentUrl",url);
        return emails.sendByTemplate(new MerchantTemplateEmailService.MerchantEmailSendCommand(
                "MERCHANT", view.getMerchantId(), view.getMerchantId(), view.getMerchantDisplayName(),
                "PAYMENT_LINK_INVITATION", "PAYMENT_LINK", input.locale(), java.util.List.of(input.email().trim()),
                variables, "PAYMENT_LINK", row.getBizNo())).recordId();
    }
    private Delivery projection(PaymentLinkDeliveryDO row) {
        Delivery d=new Delivery(); d.setRequestId(row.getRequestId()); d.setRecipientMasked(row.getRecipientMasked());
        d.setLocale(row.getLocale()); d.setEmailRecordId(row.getEmailRecordId()); d.setSendStatus(requests.status(row.getEmailRecordId()));
        d.setCreatedAppCode("MERCHANT"); d.setCreatedBy(row.getCreatedBy()); d.setCreateTime(row.getCreateTime()); return d;
    }
    private void quota(String scope,String identity,int max,Duration window) {
        var decision=limiter.tryAcquire(scope,identity,max,window);
        if(!decision.allowed()) { throw new RateLimitExceededException(decision.retryAfterSeconds()); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private String mask(String email) { int at=email.lastIndexOf('@'); return email.substring(0,email.offsetByCodePoints(0,1))+"***"+email.substring(at); }
}

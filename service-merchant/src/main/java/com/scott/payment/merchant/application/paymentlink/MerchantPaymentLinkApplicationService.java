package com.scott.payment.merchant.application.paymentlink;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.client.payment.PaymentLinkInternalClient;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import org.springframework.stereotype.Service;

/** 登录用例只组装可信操作者与商户范围；链接写入经支付域内部契约执行。 */
@Service
public class MerchantPaymentLinkApplicationService {
    private final PaymentLinkInternalClient payment;

    public MerchantPaymentLinkApplicationService(PaymentLinkInternalClient payment) {
        this.payment = payment;
    }

    /** 筛选读取基础币种字典，不授予创建权限或泄露内部渠道配置。 */
    public FilterChoices filterChoices() {
        java.util.List<String> currencies = payment.executePaymentLink("filter-currencies", command(),
                new TypeReference<CommonResult<java.util.List<String>>>() { });
        return new FilterChoices(currencies);
    }
    public record FilterChoices(java.util.List<String> currencies) { }

    public View create(String merchantId, Create input) {
        Command command = command(); command.setMerchantId(target(merchantId)); command.setCreate(input);
        return payment.executePaymentLink("create", command, new TypeReference<CommonResult<View>>() { });
    }
    public Options options(String merchantId) {
        Command command = command(); command.setMerchantId(target(merchantId));
        return payment.executePaymentLink("options", command, new TypeReference<CommonResult<Options>>() { });
    }
    public PageResult<View> search(Query query) {
        Command command = command(); command.setQuery(query);
        return payment.executePaymentLink("search", command, new TypeReference<CommonResult<PageResult<View>>>() { });
    }
    public Summary summary(Query query) {
        Command command = command(); command.setQuery(query);
        return payment.executePaymentLink("summary", command, new TypeReference<CommonResult<Summary>>() { });
    }
    public View detail(String linkId) {
        Command command = command(); command.setLinkId(linkId);
        return payment.executePaymentLink("detail", command, new TypeReference<CommonResult<View>>() { });
    }
    public Share share(String linkId) {
        Command command = command(); command.setLinkId(linkId);
        return payment.executePaymentLink("share", command, new TypeReference<CommonResult<Share>>() { });
    }
    public View disable(String linkId, Disable input) {
        Command command = command(); command.setLinkId(linkId); command.setVersion(input.version());
        command.setReason(input.reason()); command.setRequestId(input.requestId());
        return payment.executePaymentLink("disable", command, new TypeReference<CommonResult<View>>() { });
    }
    public PageResult<Collection> collections(String linkId, Query query) {
        Command command = command(); command.setLinkId(linkId); command.setQuery(query);
        return payment.executePaymentLink("collections", command, new TypeReference<CommonResult<PageResult<Collection>>>() { });
    }
    public PageResult<Attempt> attempts(String linkId, Query query) {
        Command command = command(); command.setLinkId(linkId); command.setQuery(query);
        return payment.executePaymentLink("transactions", command, new TypeReference<CommonResult<PageResult<Attempt>>>() { });
    }
    public PageResult<Event> events(String linkId, Query query) {
        Command command = command(); command.setLinkId(linkId); command.setQuery(query);
        return payment.executePaymentLink("events", command, new TypeReference<CommonResult<PageResult<Event>>>() { });
    }
    public Actor actor() {
        var account = InternalAuthContextHolder.get();
        if (account == null || !"MERCHANT".equals(account.getAppCode()) || account.getAccountId() == null || account.getMerchantId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        Actor actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId(String.valueOf(account.getAccountId()));
        actor.setMerchantIds(java.util.Set.of(account.getMerchantId()));
        return actor;
    }
    private Command command() { Command c = new Command(); c.setActor(actor()); return c; }
    private String target(String merchantId) {
        String target = actor().getMerchantIds().iterator().next();
        if (merchantId != null && !merchantId.equals(target)) { throw new ServiceException(ApiResultEnum.FORBIDDEN); }
        return target;
    }
}

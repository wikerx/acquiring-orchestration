package com.scott.payment.merchant.application.paymentlink;

import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.link.service.PaymentLinkManagementService;
import com.scott.payment.link.service.PaymentLinkManagementPolicy;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import org.springframework.stereotype.Service;

/** 管理查询和链接操作由本服务执行，共用链接规则；实际付款仍由 Payment 执行。 */
@Service
public class MerchantPaymentLinkApplicationService {
    private final PaymentLinkManagementService management;
    private final PaymentLinkManagementPolicy policy;
    private final MerchantPaymentLinkQueryService queries;
    private final MerchantPaymentLinkOperatorProjection operators;

    public MerchantPaymentLinkApplicationService(PaymentLinkManagementService management, PaymentLinkManagementPolicy policy, MerchantPaymentLinkOperatorProjection operators, MerchantPaymentLinkQueryService queries) {
        this.queries = queries; this.management = management; this.policy = policy; this.operators = operators;
    }

    /** 筛选读取基础币种字典，不授予创建权限或泄露内部渠道配置。 */
    public FilterChoices filterChoices() {
        java.util.List<String> currencies = queries.filterCurrencies(actor());
        return new FilterChoices(currencies);
    }
    public record FilterChoices(java.util.List<String> currencies) { }

    public View create(String merchantId, Create input) {
        Actor actor = actor(); String merchant = target(merchantId);
        return queries.detail(actor, management.create(actor, merchant, input));
    }
    public Options options(String merchantId) {
        Actor actor = actor(); return policy.options(actor, target(merchantId), Options::new);
    }
    public PageResult<View> search(Query query) {
        return queries.search(actor(), query);
    }
    public Summary summary(Query query) {
        return queries.summary(actor(), query);
    }
    public View detail(String linkId) {
        return operators.view(queries.detail(actor(), linkId));
    }
    public Share share(String linkId) {
        Share result = new Share(); result.setUrl(management.share(actor(), linkId)); return result;
    }
    public View disable(String linkId, Disable input) {
        if (input == null) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        Actor actor = actor(); return queries.detail(actor, management.disable(actor, linkId, input.version(), input.reason(), input.requestId()));
    }
    public PageResult<Collection> collections(String linkId, Query query) {
        return queries.collections(actor(), linkId, query);
    }
    public PageResult<Attempt> attempts(String linkId, Query query) {
        return queries.attempts(actor(), linkId, query);
    }
    public PageResult<Event> events(String linkId, Query query) {
        return operators.events(queries.events(actor(), linkId, query));
    }
    public PageResult<Delivery> deliveryOperators(PageResult<Delivery> page) { return operators.deliveries(page); }

    public Actor actor() {
        var account = InternalAuthContextHolder.get();
        if (account == null || !"MERCHANT".equals(account.getAppCode()) || account.getAccountId() == null || account.getMerchantId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        Actor actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId(String.valueOf(account.getAccountId()));
        actor.setMerchantIds(java.util.Set.of(account.getMerchantId()));
        return actor;
    }
    private String target(String merchantId) {
        String target = actor().getMerchantIds().iterator().next();
        if (merchantId != null && !merchantId.equals(target)) { throw new ServiceException(ApiResultEnum.FORBIDDEN); }
        return target;
    }
}

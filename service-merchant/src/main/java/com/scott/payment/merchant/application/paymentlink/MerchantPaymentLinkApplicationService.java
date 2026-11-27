package com.scott.payment.merchant.application.paymentlink;

import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.link.service.PaymentLinkManagementService;
import com.scott.payment.link.model.PaymentLinkManagementModels.AmountCheck;
import com.scott.payment.link.service.ChannelSingleAmountLimits;
import com.scott.payment.link.service.PaymentLinkManagementPolicy;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理查询和链接操作由本服务执行，共用链接规则；实际付款仍由 Payment 执行。 */
@Service
public class MerchantPaymentLinkApplicationService {
    private final PaymentLinkManagementService management;
    private final PaymentLinkManagementPolicy policy;
    private final MerchantPaymentLinkQueryService queries;
    private final MerchantPaymentLinkOperatorProjection operators;
    @org.springframework.beans.factory.annotation.Autowired
    private ChannelSingleAmountLimits singleAmountLimits;

    public MerchantPaymentLinkApplicationService(PaymentLinkManagementService management, PaymentLinkManagementPolicy policy, MerchantPaymentLinkOperatorProjection operators, MerchantPaymentLinkQueryService queries) {
        this.queries = queries; this.management = management; this.policy = policy; this.operators = operators;
    }

    /** 筛选读取基础币种字典，不授予创建权限或泄露内部渠道配置。 */
    public FilterChoices filterChoices() {
        java.util.List<String> currencies = queries.filterCurrencies(actor());
        return new FilterChoices(currencies);
    }
    public record FilterChoices(java.util.List<String> currencies) { }

    /** 从可信身份确认目标商户，按共享幂等规则创建链接并读取授权详情。 */
    public View create(String merchantId, Create input) {
        Actor actor = actor(); String merchant = target(merchantId);
        return queries.detail(actor, management.create(actor, merchant, input));
    }
    /** 按目标商户权限计算当前创建能力，不向调用方透传渠道密钥。 */
    public Options options(String merchantId) {
        Actor actor = actor(); return policy.options(actor, target(merchantId), Options::new);
    }
    public List<String> amountUnavailableMethods(AmountCheck input) {
        return policy.amountUnavailableMethods(actor(), target(null), input, singleAmountLimits);
    }

    /** 使用当前会话身份查询可见链接，不能由请求指定其他操作者范围。 */
    public PageResult<View> search(Query query) {
        return queries.search(actor(), query);
    }
    /** 使用当前会话的数据范围汇总链接，成功金额保持分币种口径。 */
    public Summary summary(Query query) {
        return queries.summary(actor(), query);
    }
    /** 读取授权详情并补充操作者名称，保留审计中的来源与账号标识。 */
    public View detail(String linkId) {
        return operators.view(queries.detail(actor(), linkId));
    }
    /** 在归属和入口状态校验后签发分享地址，并记录分享事件。 */
    public Share share(String linkId) {
        Share result = new Share(); result.setUrl(management.share(actor(), linkId)); return result;
    }
    /** 校验停用参数后委托共享规则执行版本控制与幂等停用，不取消在途付款。 */
    public View disable(String linkId, Disable input) {
        if (input == null) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        Actor actor = actor(); return queries.detail(actor, management.disable(actor, linkId, input.version(), input.reason(), input.requestId()));
    }
    /** 使用当前身份读取链接已有收款会话，不新建交易。 */
    public PageResult<Collection> collections(String linkId, Query query) {
        return queries.collections(actor(), linkId, query);
    }
    /** 使用当前身份读取链接的所有真实付款尝试，保留未生成核心订单的失败。 */
    public PageResult<Attempt> attempts(String linkId, Query query) {
        return queries.attempts(actor(), linkId, query);
    }
    /** 读取授权时间线并补充操作者名称，不改写历史事件身份。 */
    public PageResult<Event> events(String linkId, Query query) {
        return operators.events(queries.events(actor(), linkId, query));
    }
    /** 为邀请记录补充操作者名称，原始账号与应用来源仍用于审计。 */
    public PageResult<Delivery> deliveryOperators(PageResult<Delivery> page) { return operators.deliveries(page); }

    /** 仅从已鉴权上下文生成本端身份及商户范围；缺失身份时拒绝访问。 */
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

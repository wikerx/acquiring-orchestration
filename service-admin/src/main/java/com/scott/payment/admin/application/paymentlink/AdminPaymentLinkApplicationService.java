package com.scott.payment.admin.application.paymentlink;

import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.link.service.PaymentLinkManagementService;
import com.scott.payment.link.service.PaymentLinkManagementPolicy;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import org.springframework.stereotype.Service;

/** 管理查询和链接操作由本服务执行，共用链接规则；实际付款仍由 Payment 执行。 */
@Service
public class AdminPaymentLinkApplicationService {
    private final PaymentLinkManagementService management;
    private final PaymentLinkManagementPolicy policy;
    private final AdminPaymentLinkQueryService queries;
    private final AdminPaymentLinkOperatorProjection operators;
    private final AdminMerchantDataScopeResolver scopes;
    private final com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper merchants;
    private final com.scott.payment.admin.service.AdminChannelService channels;

    public AdminPaymentLinkApplicationService(PaymentLinkManagementService management, PaymentLinkManagementPolicy policy, AdminMerchantDataScopeResolver scopes, com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper merchants, com.scott.payment.admin.service.AdminChannelService channels, AdminPaymentLinkOperatorProjection operators, AdminPaymentLinkQueryService queries) {
        this.queries = queries; this.management = management; this.policy = policy; this.operators = operators; this.scopes = scopes; this.merchants = merchants; this.channels = channels;
    }

    /** 返回当前数据范围内的商户轻量选项，支付链接角色无需额外取得商户资料权限。 */
    public java.util.List<MerchantChoice> merchantChoices(String keyword) {
        Actor actor = actor();
        if (!actor.isAllMerchants() && actor.getMerchantIds().isEmpty()) return java.util.List.of();
        var condition = com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO>lambdaQuery()
                .select(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantId,
                        com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantName)
                .eq(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getDeleted, 0);
        if (!actor.isAllMerchants()) condition.in(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantId, actor.getMerchantIds());
        if (keyword != null && !keyword.isBlank()) condition.and(q -> q
                .like(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantId, keyword.trim())
                .or().like(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantName, keyword.trim()));
        condition.orderByAsc(com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO::getMerchantId).last("LIMIT 40");
        return merchants.selectList(condition).stream().map(row -> new MerchantChoice(row.getMerchantId(), row.getMerchantName())).toList();
    }
    public record MerchantChoice(String merchantId, String merchantName) { }

    /** 列表筛选选项与创建能力分离，只返回基础币种和必要渠道名称，不透传渠道配置。 */
    public FilterChoices filterChoices() {
        java.util.List<String> currencies = queries.filterCurrencies(actor());
        return new FilterChoices(currencies, channels.listChannelOptions().stream()
                .map(item -> new ChannelChoice(item.getChannelCode(), item.getChannelName())).toList());
    }
    public record ChannelChoice(String channelCode, String channelName) { }
    public record FilterChoices(java.util.List<String> currencies, java.util.List<ChannelChoice> channels) { }

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
        if (account == null || !"ADMIN".equals(account.getAppCode()) || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        Actor actor = new Actor(); actor.setAppCode("ADMIN"); actor.setActorId(String.valueOf(account.getAccountId()));
        var scope = scopes.resolve(account);
        actor.setAllMerchants(scope.allMerchants()); actor.setMerchantIds(scope.merchantIds());
        return actor;
    }
    private String target(String merchantId) {
        Actor actor = actor();
        if (merchantId == null || merchantId.isBlank() || !(actor.isAllMerchants() || actor.getMerchantIds().contains(merchantId))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return merchantId;
    }
}

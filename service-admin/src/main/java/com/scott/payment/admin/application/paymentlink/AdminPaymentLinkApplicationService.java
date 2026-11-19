package com.scott.payment.admin.application.paymentlink;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.admin.client.payment.PaymentLinkInternalClient;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import org.springframework.stereotype.Service;

/** 登录用例只组装可信操作者与商户范围；链接写入经支付域内部契约执行。 */
@Service
public class AdminPaymentLinkApplicationService {
    private final PaymentLinkInternalClient payment;
    private final AdminPaymentLinkOperatorProjection operators;
    private final AdminMerchantDataScopeResolver scopes;
    private final com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper merchants;
    private final com.scott.payment.admin.service.AdminChannelService channels;

    public AdminPaymentLinkApplicationService(PaymentLinkInternalClient payment, AdminMerchantDataScopeResolver scopes, com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper merchants, com.scott.payment.admin.service.AdminChannelService channels, AdminPaymentLinkOperatorProjection operators) {
        this.payment = payment; this.operators = operators; this.scopes = scopes; this.merchants = merchants; this.channels = channels;
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
        java.util.List<String> currencies = payment.executePaymentLink("filter-currencies", command(),
                new TypeReference<CommonResult<java.util.List<String>>>() { });
        return new FilterChoices(currencies, channels.listChannelOptions().stream()
                .map(item -> new ChannelChoice(item.getChannelCode(), item.getChannelName())).toList());
    }
    public record ChannelChoice(String channelCode, String channelName) { }
    public record FilterChoices(java.util.List<String> currencies, java.util.List<ChannelChoice> channels) { }

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
        return operators.view(payment.executePaymentLink("detail", command, new TypeReference<CommonResult<View>>() { }));
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
        return operators.events(payment.executePaymentLink("events", command, new TypeReference<CommonResult<PageResult<Event>>>() { }));
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
    private Command command() { Command c = new Command(); c.setActor(actor()); return c; }
    private String target(String merchantId) {
        Actor actor = actor();
        if (merchantId == null || merchantId.isBlank() || !(actor.isAllMerchants() || actor.getMerchantIds().contains(merchantId))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return merchantId;
    }
}

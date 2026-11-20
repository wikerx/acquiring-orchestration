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

    /** 从可信身份确认目标商户，按共享幂等规则创建链接并读取授权详情。 */
    public View create(String merchantId, Create input) {
        Actor actor = actor(); String merchant = target(merchantId);
        return queries.detail(actor, management.create(actor, merchant, input));
    }
    /** 按目标商户权限计算当前创建能力，不向调用方透传渠道密钥。 */
    public Options options(String merchantId) {
        Actor actor = actor(); return policy.options(actor, target(merchantId), Options::new);
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

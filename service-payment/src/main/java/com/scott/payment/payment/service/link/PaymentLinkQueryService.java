package com.scott.payment.payment.service.link;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.payment.mapper.PaymentLinkMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 授权查询与只读投影；不将存储实体或完整支付凭据返回双端。 */
@Service
public class PaymentLinkQueryService {
    private final PaymentLinkMapper links;
    private final PaymentLinkPolicy policy;

    public PaymentLinkQueryService(PaymentLinkMapper links, PaymentLinkPolicy policy) {
        this.links = links;
        this.policy = policy;
    }

    /** 在操作者的商户数据范围内分页读取链接，金额按冻结的币种精度展示。 */
    @DS(DataSourceName.MASTER)
    public PageResult<View> search(Actor actor, Query input) {
        policy.requireEnabled();
        Query query = bounded(input);
        if (query.getMerchantId() != null && !query.getMerchantId().isBlank()) {
            policy.requireScope(actor, query.getMerchantId());
        }
        requireActor(actor);
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(links.count(actor, query, now), query.getPageNo(), query.getPageSize(),
                links.search(actor, query, now, offset(query)).stream().map(this::moneyView).toList());
    }

    /** 先校验链接归属再组装内部详情；回调地址不得进入匿名付款人投影。 */
    @DS(DataSourceName.MASTER)
    public View detail(Actor actor, String linkId) {
        policy.requireEnabled();
        PaymentLinkDO row = links.selectOne(Wrappers.<PaymentLinkDO>lambdaQuery().eq(PaymentLinkDO::getLinkId, linkId));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        policy.requireScope(actor, row.getMerchantId());
        Query query = new Query();
        query.setLinkId(linkId);
        List<View> result = links.search(actor, query, LocalDateTime.now(), 0);
        if (result.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        View view = moneyView(result.get(0));
        view.setCardBrands(JsonUtils.parseArray(row.getAllowedBrandsJson(), String.class));
        if (row.getAllowedMethodsJson() != null) {
            var keys = com.scott.payment.link.service.PaymentLinkMethodScope.deserialize(row.getAllowedMethodsJson());
            view.setPaymentMethodKeys(keys);
            if (keys.stream().noneMatch(key -> key.startsWith("CARD_BRAND:"))) {
                view.setPaymentAvailable(false);
            }
        }
        view.setNotifyUrl(row.getNotifyUrl());
        view.setRedirectUrl(row.getRedirectUrl());
        return view;
    }

    /** ISO 币种属于基础选项，读取不依赖创建权限，也不扫描交易或链接事实。 */
    public List<String> filterCurrencies(Actor actor) {
        policy.requireEnabled(); requireActor(actor);
        return policy.filterCurrencies();
    }

    /** 在相同授权和筛选范围内汇总状态数量，成功金额按币种分别累计。 */
    @DS(DataSourceName.MASTER)
    public Summary summary(Actor actor, Query input) {
        policy.requireEnabled();
        Query query = bounded(input);
        requireActor(actor);
        if (query.getMerchantId() != null && !query.getMerchantId().isBlank()) {
            policy.requireScope(actor, query.getMerchantId());
        }
        Summary result = new Summary();
        Map<String, String> amounts = new LinkedHashMap<>();
        for (Aggregate row : links.summarize(actor, query, LocalDateTime.now())) {
            result.setTotal(result.getTotal() + row.getTotal());
            result.setActive(result.getActive() + row.getActive());
            result.setCompleted(result.getCompleted() + row.getCompleted());
            result.setProcessing(result.getProcessing() + row.getProcessing());
            result.setFailedAttempts(result.getFailedAttempts() + row.getFailedAttempts());
            amounts.put(row.getCurrency(), row.getAmount().toPlainString());
        }
        result.setSuccessfulAmounts(amounts);
        return result;
    }

    /** 确认链接可见后分页读取收款会话，不创建会话或推进支付状态。 */
    @DS(DataSourceName.MASTER)
    public PageResult<Collection> collections(Actor actor, String linkId, Query input) {
        detail(actor, linkId);
        Query query = bounded(input);
        return PageResult.of(links.countCollections(linkId), query.getPageNo(), query.getPageSize(),
                links.collections(linkId, offset(query), query.getPageSize()));
    }

    /** 确认链接可见后读取真实付款尝试；预分配编号不作为已生成的系统订单。 */
    @DS(DataSourceName.MASTER)
    public PageResult<Attempt> attempts(Actor actor, String linkId, Query input) {
        detail(actor, linkId);
        Query query = bounded(input);
        return PageResult.of(links.countAttempts(linkId), query.getPageNo(), query.getPageSize(),
                links.attempts(linkId, offset(query), query.getPageSize()).stream().map(row -> {
                    row.setAmount(amount(row.getAmount(), row.getCurrencyExponent())); return row;
                }).toList());
    }

    /** 确认链接可见后分页读取生命周期时间线，查询不写入审计事件。 */
    @DS(DataSourceName.MASTER)
    public PageResult<Event> events(Actor actor, String linkId, Query input) {
        detail(actor, linkId);
        Query query = bounded(input);
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(links.countTimeline(linkId, now), query.getPageNo(), query.getPageSize(),
                links.timeline(linkId, now, offset(query), query.getPageSize()));
    }

    private View moneyView(View view) {
        view.setAmount(amount(view.getAmount(), view.getCurrencyExponent()));
        return view;
    }

    private String amount(String value, Integer exponent) {
        if (value == null || exponent == null || exponent < 0 || exponent > 6) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return new java.math.BigDecimal(value).setScale(exponent, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.getActorId() == null || actor.getActorId().isBlank()
                || !("ADMIN".equals(actor.getAppCode()) || "MERCHANT".equals(actor.getAppCode()))
                || actor.isAllMerchants() && !"ADMIN".equals(actor.getAppCode())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    private Query bounded(Query input) {
        Query query = input == null ? new Query() : input;
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        query.setLinkId(query.getLinkId() == null || query.getLinkId().isBlank() ? null : query.getLinkId().trim());
        query.setMerchantOrderNo(query.getMerchantOrderNo() == null || query.getMerchantOrderNo().isBlank()
                ? null : query.getMerchantOrderNo().trim());
        if (query.getLinkId() != null && !query.getLinkId().matches("(?:L[0-9]{22}|[0-9]{22}|[a-f0-9]{32})")
                || query.getMerchantOrderNo() != null && !query.getMerchantOrderNo().matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (query.getPageNo() < 1 || query.getPageSize() < 1 || query.getPageSize() > 100
                || query.getPageNo() > 1_000_000) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return query;
    }

    private long offset(Query query) { return (long) (query.getPageNo() - 1) * query.getPageSize(); }
}

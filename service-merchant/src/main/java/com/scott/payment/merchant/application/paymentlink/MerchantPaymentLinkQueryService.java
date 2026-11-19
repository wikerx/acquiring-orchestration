package com.scott.payment.merchant.application.paymentlink;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.merchant.mapper.PaymentLinkQueryMapper;
import com.scott.payment.merchant.mapper.PaymentLinkQueryMapper.CurrencyAggregate;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理查询直接读取既有事实，不依赖支付服务在线，也不推进链接或交易状态。 */
@Service
@DS(DataSourceName.MASTER)
@Transactional(readOnly = true)
public class MerchantPaymentLinkQueryService {
    private final PaymentLinkQueryMapper links;
    private final IsoDictionaryService currencies;

    public MerchantPaymentLinkQueryService(PaymentLinkQueryMapper links, IsoDictionaryService currencies) {
        this.links = links;
        this.currencies = currencies;
    }

    public PageResult<View> search(Actor actor, Query input) {
        Query query = bounded(input);
        if (query.getMerchantId() != null && !query.getMerchantId().isBlank()) {
            requireScope(actor, query.getMerchantId());
        }
        requireActor(actor);
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(links.count(actor, query, now), query.getPageNo(), query.getPageSize(),
                links.search(actor, query, now, offset(query)).stream().map(this::moneyView).toList());
    }

    public View detail(Actor actor, String linkId) {
        requireActor(actor);
        PaymentLinkQueryMapper.DetailMetadata row = links.detailMetadata(linkId);
        if (row == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        requireScope(actor, row.getMerchantId());
        Query query = new Query();
        query.setLinkId(linkId);
        List<View> result = links.search(actor, query, LocalDateTime.now(), 0);
        if (result.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        View view = moneyView(result.get(0));
        view.setCardBrands(JsonUtils.parseArray(row.getAllowedBrandsJson(), String.class));
        view.setNotifyUrl(row.getNotifyUrl());
        view.setRedirectUrl(row.getRedirectUrl());
        return view;
    }

    /** ISO 币种属于基础选项，读取不依赖创建权限，也不扫描交易或链接事实。 */
    public List<String> filterCurrencies(Actor actor) {
        requireActor(actor);
        return currencies.listCurrencies().stream().map(IsoCurrencyInfo::alphabeticCode).distinct().sorted().toList();
    }

    public Summary summary(Actor actor, Query input) {
        Query query = bounded(input);
        requireActor(actor);
        if (query.getMerchantId() != null && !query.getMerchantId().isBlank()) {
            requireScope(actor, query.getMerchantId());
        }
        Summary result = new Summary();
        Map<String, String> amounts = new LinkedHashMap<>();
        for (CurrencyAggregate row : links.summarize(actor, query, LocalDateTime.now())) {
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

    public PageResult<Collection> collections(Actor actor, String linkId, Query input) {
        detail(actor, linkId);
        Query query = bounded(input);
        return PageResult.of(links.countCollections(linkId), query.getPageNo(), query.getPageSize(),
                links.collections(linkId, offset(query), query.getPageSize()));
    }

    public PageResult<Attempt> attempts(Actor actor, String linkId, Query input) {
        detail(actor, linkId);
        Query query = bounded(input);
        return PageResult.of(links.countAttempts(linkId), query.getPageNo(), query.getPageSize(),
                links.attempts(linkId, offset(query), query.getPageSize()).stream().map(row -> {
                    row.setAmount(amount(row.getAmount(), row.getCurrencyExponent())); return row;
                }).toList());
    }

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
                || !"MERCHANT".equals(actor.getAppCode())
                || actor.isAllMerchants()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    private void requireScope(Actor actor, String merchantId) {
        requireActor(actor);
        if (merchantId == null || merchantId.isBlank()
                || !(actor.isAllMerchants() || actor.getMerchantIds() != null && actor.getMerchantIds().contains(merchantId))) {
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

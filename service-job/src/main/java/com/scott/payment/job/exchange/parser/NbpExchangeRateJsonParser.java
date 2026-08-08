package com.scott.payment.job.exchange.parser;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateJsonParser
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 解析波兰国家银行 Table C JSON，将外币兑 PLN 的官方 bid/ask 映射为平台即期买入价和即期卖出价。
 * @status : create
 */
@Component
public class NbpExchangeRateJsonParser {

    private static final String TABLE_C = "C";
    private static final String QUOTE_CURRENCY = "PLN";
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Z]{3}$");

    /**
     * 解析 NBP Table C JSON 响应。
     *
     * @param json NBP Table C 原始响应
     * @return 统一原始汇率项目列表；现金价和中间价保持为空
     */
    public List<RawRateItem> parse(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        List<NbpTableResponse> tables;
        try {
            tables = JsonUtils.parseArray(json, NbpTableResponse.class);
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        List<RawRateItem> result = new ArrayList<>();
        for (NbpTableResponse table : tables) {
            appendTableRates(table, result);
        }
        return result;
    }

    /**
     * 将单张有效 Table C 的报价追加到统一原始汇率结果中。
     */
    private void appendTableRates(NbpTableResponse table, List<RawRateItem> result) {
        if (table == null || !TABLE_C.equalsIgnoreCase(table.getTable()) || table.getRates() == null) {
            return;
        }
        LocalDate effectiveDate;
        try {
            effectiveDate = LocalDate.parse(table.getEffectiveDate());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalidResponse();
        }
        for (NbpRateResponse rate : table.getRates()) {
            RawRateItem item = toRawRateItem(rate, effectiveDate);
            if (item != null) {
                result.add(item);
            }
        }
    }

    /**
     * 校验单币种报价并按外币兑 PLN 方向构造统一模型。
     */
    private RawRateItem toRawRateItem(NbpRateResponse rate, LocalDate effectiveDate) {
        if (rate == null || !StringUtils.hasText(rate.getCurrency()) || !StringUtils.hasText(rate.getCode())) {
            return null;
        }
        String currencyCode = rate.getCode().trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE_PATTERN.matcher(currencyCode).matches()
                || !isPositive(rate.getBid())
                || !isPositive(rate.getAsk())
                || rate.getBid().compareTo(rate.getAsk()) > 0) {
            return null;
        }
        RawRateItem item = new RawRateItem();
        item.setSourceCurrencyName(rate.getCurrency().trim());
        item.setBaseCurrency(currencyCode);
        item.setQuoteCurrency(QUOTE_CURRENCY);
        item.setSpotBuyRate(rate.getBid());
        item.setSpotSellRate(rate.getAsk());
        item.setPublishTime(effectiveDate.atStartOfDay());
        return item;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private ServiceException invalidResponse() {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "NBP Table C response is invalid");
    }

    /**
     * NBP Table C 单日响应，只保留解析所需字段。
     */
    @Data
    private static class NbpTableResponse {

        /** 报价表类型，当前只接受 C。 */
        private String table;

        /** 汇率生效日期，格式 yyyy-MM-dd。 */
        private String effectiveDate;

        /** 当日外币兑 PLN 买卖报价。 */
        private List<NbpRateResponse> rates;
    }

    /**
     * NBP Table C 单币种 bid/ask 响应。
     */
    @Data
    private static class NbpRateResponse {

        /** NBP 返回的币种名称。 */
        private String currency;

        /** ISO 4217 三位币种代码。 */
        private String code;

        /** NBP 买入价，单位为 PLN/一单位外币。 */
        private BigDecimal bid;

        /** NBP 卖出价，单位为 PLN/一单位外币。 */
        private BigDecimal ask;
    }
}

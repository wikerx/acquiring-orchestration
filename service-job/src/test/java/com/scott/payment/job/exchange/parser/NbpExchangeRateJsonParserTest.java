package com.scott.payment.job.exchange.parser;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateJsonParserTest
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 验证波兰国家银行 Table C 报价按外币兑 PLN 的方向映射为平台即期买入价和即期卖出价。
 * @status : create
 */
class NbpExchangeRateJsonParserTest {

    /**
     * 验证 Table C 的 bid/ask、币种方向和日期精度映射。
     */
    @Test
    void shouldParseTableCBidAndAskAsSpotRatesQuotedInPln() {
        NbpExchangeRateJsonParser parser = new NbpExchangeRateJsonParser();
        String json = """
                [
                  {
                    "table": "C",
                    "no": "151/C/NBP/2026",
                    "tradingDate": "2026-08-06",
                    "effectiveDate": "2026-08-07",
                    "rates": [
                      {
                        "currency": "dolar amerykanski",
                        "code": "USD",
                        "bid": 3.6123,
                        "ask": 3.6853
                      },
                      {
                        "currency": "jen (Japonia)",
                        "code": "JPY",
                        "bid": 0.024146,
                        "ask": 0.024634
                      }
                    ]
                  }
                ]
                """;

        List<RawRateItem> items = parser.parse(json);

        assertThat(items).hasSize(2);
        RawRateItem usd = items.get(0);
        assertThat(usd.getSourceCurrencyName()).isEqualTo("dolar amerykanski");
        assertThat(usd.getBaseCurrency()).isEqualTo("USD");
        assertThat(usd.getQuoteCurrency()).isEqualTo("PLN");
        assertThat(usd.getSpotBuyRate()).isEqualByComparingTo("3.6123");
        assertThat(usd.getSpotSellRate()).isEqualByComparingTo("3.6853");
        assertThat(usd.getCashBuyRate()).isNull();
        assertThat(usd.getCashSellRate()).isNull();
        assertThat(usd.getMiddleRate()).isNull();
        assertThat(usd.getPublishTime()).hasToString("2026-08-07T00:00");
        assertThat(items.get(1).getSpotBuyRate()).isEqualByComparingTo("0.024146");
    }

    /**
     * 验证非法币种、非正数和买入价高于卖出价的报价不会进入原始汇率链路。
     */
    @Test
    void shouldSkipInvalidAndInvertedQuotes() {
        NbpExchangeRateJsonParser parser = new NbpExchangeRateJsonParser();
        String json = """
                [{
                  "table":"C",
                  "effectiveDate":"2026-08-07",
                  "rates":[
                    {"currency":"euro","code":"EUR","bid":4.2123,"ask":4.2973},
                    {"currency":"invalid code","code":"EU","bid":4.1,"ask":4.2},
                    {"currency":"negative bid","code":"USD","bid":-1,"ask":3.8},
                    {"currency":"inverted","code":"GBP","bid":5.2,"ask":5.1}
                  ]
                }]
                """;

        List<RawRateItem> items = parser.parse(json);

        assertThat(items).singleElement()
                .extracting(RawRateItem::getBaseCurrency)
                .isEqualTo("EUR");
    }
}

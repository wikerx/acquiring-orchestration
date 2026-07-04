package com.scott.payment.job.exchange.parser;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateHtmlParserTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 中国银行汇率 HTML 解析测试。
 * @status : create
 */
class BocExchangeRateHtmlParserTest {

    @Test
    void shouldParseBocHtmlAndConvertHundredUnitQuoteToSingleUnitRate() {
        BocExchangeRateHtmlParser parser = new BocExchangeRateHtmlParser();
        String html = """
                <table>
                  <tr><th>货币名称</th><th>现汇买入价</th><th>现钞买入价</th><th>现汇卖出价</th><th>现钞卖出价</th><th>中行折算价</th><th>发布时间</th></tr>
                  <tr>
                    <td>美元</td><td>718.65</td><td>712.81</td><td>721.53</td><td>721.53</td><td>710.12</td><td>2026.07.03 10:30:00</td>
                  </tr>
                  <tr>
                    <td>林吉特</td><td></td><td>-</td><td>151.20</td><td>151.20</td><td></td><td>2026.07.03 10:30:00</td>
                  </tr>
                </table>
                """;

        List<RawRateItem> items = parser.parse(html);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getSourceCurrencyName()).isEqualTo("美元");
        assertThat(items.get(0).getSpotBuyRate()).isEqualByComparingTo("7.186500000000");
        assertThat(items.get(0).getSpotSellRate()).isEqualByComparingTo("7.215300000000");
        assertThat(items.get(0).getQuoteCurrency()).isEqualTo("CNY");
        assertThat(items.get(1).getCashBuyRate()).isNull();
        assertThat(items.get(1).getSpotSellRate()).isEqualByComparingTo("1.512000000000");
    }

    @Test
    void shouldParseCurrentBocSlashSeparatedPublishTime() {
        BocExchangeRateHtmlParser parser = new BocExchangeRateHtmlParser();
        String html = """
                <table>
                  <tr><th>货币名称</th><th>现汇买入价</th><th>现钞买入价</th><th>现汇卖出价</th><th>现钞卖出价</th><th>中行折算价</th><th>发布时间</th></tr>
                  <tr>
                    <td>美元</td><td>718.65</td><td>712.81</td><td>721.53</td><td>721.53</td><td>710.12</td><td>2026/07/03 13:18:38</td>
                  </tr>
                </table>
                """;

        List<RawRateItem> items = parser.parse(html);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPublishTime()).hasToString("2026-07-03T13:18:38");
    }
}

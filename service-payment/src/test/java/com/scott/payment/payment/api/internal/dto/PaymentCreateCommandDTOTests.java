package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Internal payment command JSON contract tests. */
class PaymentCreateCommandDTOTests {

    @Test
    void shouldReceiveMerchantGoodsPayerAndShippingSnapshots() throws Exception {
        String json = """
                {
                  "goodsInfo":[{"name":"Travel Booking","quantity":1,"amount":12.34,"currency":"USD"}],
                  "payerInfo":{"payerId":"CUSTOMER-001","ipAddress":"203.0.113.10","sessionId":"SESSION-001"},
                  "shippingInfo":{"firstName":"John","street":"2 Shipping St","country":"USA"}
                }
                """;

        PaymentCreateCommandDTO command = new ObjectMapper().readValue(json, PaymentCreateCommandDTO.class);

        assertThat(command.getGoodsInfo()).singleElement()
                .satisfies(goods -> assertThat(goods.getAmount()).isEqualByComparingTo("12.34"));
        assertThat(command.getPayerInfo().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(command.getShippingInfo().getStreet()).isEqualTo("2 Shipping St");
    }
}

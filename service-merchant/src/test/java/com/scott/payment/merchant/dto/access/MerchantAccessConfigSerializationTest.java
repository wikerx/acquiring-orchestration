package com.scott.payment.merchant.dto.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户支付接入配置响应的字段边界契约测试。
 */
class MerchantAccessConfigSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sourceHostShouldBeReadableFromAdminButHiddenFromMerchantResponse() throws Exception {
        SourceUrlItem item = objectMapper.readValue("""
                {
                  "id": "1",
                  "sourceUrl": "https://shop.example.com/checkout",
                  "sourceHost": "shop.example.com"
                }
                """, SourceUrlItem.class);

        JsonNode responseJson = objectMapper.readTree(objectMapper.writeValueAsString(item));

        assertThat(item.getSourceHost()).isEqualTo("shop.example.com");
        assertThat(responseJson.path("sourceUrl").asText())
                .isEqualTo("https://shop.example.com/checkout");
        assertThat(responseJson.has("sourceHost")).isFalse();
    }
}

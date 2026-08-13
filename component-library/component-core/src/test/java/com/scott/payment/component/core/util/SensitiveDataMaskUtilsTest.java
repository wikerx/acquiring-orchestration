package com.scott.payment.component.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SensitiveDataMaskUtilsTest
 * @date : 2026-06-26 15:24
 * @email : scott_x@163.com
 * @description : Sensitive Data Mask Utils Test 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
 * @status : create
 */
class SensitiveDataMaskUtilsTest {

    /**
     * 日志 JSON 脱敏应隐藏凭据并保留必要排查尾号。
     */
    @Test
    void shouldMaskSensitiveJsonFields() {
        String json = """
                {
                  "password":"plain",
                  "Authorization":"Bearer abc.def",
                  "apiKey":"key",
                  "apiPassword":"mpgs-password",
                  "mid.apiPassword":"mid-password",
                  "mid.password":"mid-password-alias",
                  "merchantKey":"merchant-key",
                  "authenticationToken":"three-ds-token",
                  "privateKey":"pem",
                  "cardNo":"4111111111111111",
                  "ipAddress":"2001:4860:4860::8888",
                  "cardBin":"65432198765",
                  "pan":"5555555555554444",
                  "cvv":"123",
                  "mobile":"13812345678",
                  "phone":"+8613812345678",
                  "email":"scott@example.com",
                  "subEmail":"merchant@example.com",
                  "cardholderName":"John Smith",
                  "nameOnCard":"John Smith MPGS",
                  "payerIp":"203.0.113.9",
                  "legalPerson":"Jane Owner",
                  "enterprise":"Example Trading Limited",
                  "customerId":"CUSTOMER-0001",
                  "deviceFingerprint":"device-fingerprint-value",
                  "merchantWebsite":"https://shop.merchant.example/checkout?token=secret",
                  "billingAddress":"1 Billing Street",
                  "shippingAddress":"2 Shipping Street",
                  "idCard":"110101199001011234",
                  "bankAccount":"6222021234567890123",
                  "receiverAccountNo":"6222021234567890123",
                  "iban":"GB82WEST12345698765432"
                }
                """;

        String masked = SensitiveDataMaskUtils.maskJson(json);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"Authorization\":\"***\"");
        assertThat(masked).contains("\"apiKey\":\"***\"");
        assertThat(masked).contains("\"apiPassword\":\"***\"");
        assertThat(masked).contains("\"mid.apiPassword\":\"***\"");
        assertThat(masked).contains("\"mid.password\":\"***\"");
        assertThat(masked).contains("\"merchantKey\":\"***\"");
        assertThat(masked).contains("\"authenticationToken\":\"***\"");
        assertThat(masked).contains("\"privateKey\":\"***\"");
        assertThat(masked).contains("\"cardNo\":\"411111******1111\"");
        assertThat(masked).contains("\"ipAddress\":\"***\"");
        assertThat(masked).contains("\"cardBin\":\"***\"");
        assertThat(masked).contains("\"pan\":\"555555******4444\"");
        assertThat(masked).contains("\"cvv\":\"***\"");
        assertThat(masked).contains("\"mobile\":\"138****5678\"");
        assertThat(masked).contains("\"phone\":\"+86****5678\"");
        assertThat(masked).contains("\"email\":\"s***@example.com\"");
        assertThat(masked).contains("\"subEmail\":\"m***@example.com\"");
        assertThat(masked).contains("\"cardholderName\":\"***\"");
        assertThat(masked).contains("\"nameOnCard\":\"***\"");
        assertThat(masked).contains("\"payerIp\":\"***\"");
        assertThat(masked).contains("\"legalPerson\":\"***\"");
        assertThat(masked).contains("\"enterprise\":\"***\"");
        assertThat(masked).contains("\"customerId\":\"***\"");
        assertThat(masked).contains("\"deviceFingerprint\":\"***\"");
        assertThat(masked).contains("\"merchantWebsite\":\"***\"");
        assertThat(masked).contains("\"billingAddress\":\"***\"");
        assertThat(masked).contains("\"shippingAddress\":\"***\"");
        assertThat(masked).contains("\"idCard\":\"***\"");
        assertThat(masked).contains("\"bankAccount\":\"6222******0123\"");
        assertThat(masked).contains("\"receiverAccountNo\":\"6222******0123\"");
        assertThat(masked).contains("\"iban\":\"GB82******5432\"");
        assertThat(masked).doesNotContain("plain", "Bearer abc.def", "mpgs-password",
                "mid-password", "mid-password-alias", "merchant-key", "three-ds-token", "pem", "1234567890123",
                "2001:4860:4860::8888", "65432198765",
                "scott@example.com", "merchant@example.com", "John Smith", "John Smith MPGS", "203.0.113.9", "Jane Owner",
                "Example Trading Limited", "CUSTOMER-0001", "device-fingerprint-value",
                "https://shop.merchant.example/checkout?token=secret",
                "1 Billing Street", "2 Shipping Street");
    }

    /**
     * 六位 BIN 也必须脱敏，避免最短合法查询值完整进入诊断日志。
     */
    @Test
    void shouldMaskSixDigitCardBin() {
        String masked = SensitiveDataMaskUtils.maskJson("{\"cardBin\":\"411111\"}");

        assertThat(masked)
                .isEqualTo("{\"cardBin\":\"***\"}")
                .doesNotContain("411111");
    }

    /**
     * 渠道回调可能是 form-urlencoded，3DS session data 不得以明文进入日志或回调日志表。
     */
    @Test
    void shouldMaskThreeDsFormPayloadFields() {
        String formPayload = "threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                + "&threeDSSessionData=encrypted-session-data"
                + "&PaRes=issuer-payer-authentication-response"
                + "&MD=merchant-data"
                + "&orderId=TX202607141000000000001";

        String masked = SensitiveDataMaskUtils.maskJson(formPayload);

        assertThat(masked).contains("threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
        assertThat(masked).contains("threeDSSessionData=***");
        assertThat(masked).contains("PaRes=***");
        assertThat(masked).contains("MD=***");
        assertThat(masked).contains("orderId=TX202607141000000000001");
        assertThat(masked).doesNotContain("encrypted-session-data", "issuer-payer-authentication-response", "merchant-data");
    }

    @Test
    void shouldMaskJsonSafelyWithoutLeakingOriginalTextWhenMaskingFails() {
        String rawJson = "{\"cardNo\":\"4111111111111111\",\"securityCode\":\"123\"}";

        String masked = SensitiveDataMaskUtils.maskJsonSafely(rawJson, value -> {
            throw new IllegalStateException("mask failed");
        });

        assertThat(masked).isEqualTo("***MASK_FAILED***");
        assertThat(masked).doesNotContain("4111111111111111", "123");
    }

    @Test
    void shouldOnlyMaskPaymentDataAndCredentialsForTransactionInteractionJson() {
        String json = """
                {
                  "Authorization":"Bearer abc.def",
                  "merchantKey":"merchant-key",
                  "cardNo":"4111111111111111",
                  "pan":"5555555555554444",
                  "expirationMonth":"12",
                  "expirationYear":"2030",
                  "expiryDate":"12/30",
                  "securityCode":"123",
                  "cvv":"456",
                  "cavv":"AAABBIIFmAAAAAAAAAAAAAAAAAA=",
                  "threeDSSessionData":"session-data",
                  "merchantWebsite":"https://shop.merchant.example/checkout",
                  "cardholderName":"John Smith",
                  "billingAddress":"1 Billing Street",
                  "email":"scott@example.com",
                  "phone":"+8613812345678",
                  "orderNo":"M202608100001"
                }
                """;

        String masked = SensitiveDataMaskUtils.maskTransactionInteractionJson(json);

        assertThat(masked).contains("\"Authorization\":\"***\"");
        assertThat(masked).contains("\"merchantKey\":\"***\"");
        assertThat(masked).contains("\"cardNo\":\"411111******1111\"");
        assertThat(masked).contains("\"pan\":\"555555******4444\"");
        assertThat(masked).contains("\"expirationMonth\":\"***\"");
        assertThat(masked).contains("\"expirationYear\":\"***\"");
        assertThat(masked).contains("\"expiryDate\":\"***\"");
        assertThat(masked).contains("\"securityCode\":\"***\"");
        assertThat(masked).contains("\"cvv\":\"***\"");
        assertThat(masked).contains("\"cavv\":\"***\"");
        assertThat(masked).contains("\"threeDSSessionData\":\"***\"");
        assertThat(masked).contains("\"merchantWebsite\":\"https://shop.merchant.example/checkout\"");
        assertThat(masked).contains("\"cardholderName\":\"John Smith\"");
        assertThat(masked).contains("\"billingAddress\":\"1 Billing Street\"");
        assertThat(masked).contains("\"email\":\"scott@example.com\"");
        assertThat(masked).contains("\"phone\":\"+8613812345678\"");
        assertThat(masked).contains("\"orderNo\":\"M202608100001\"");
        assertThat(masked).doesNotContain("Bearer abc.def", "merchant-key", "4111111111111111",
                "5555555555554444", "\"securityCode\":\"123\"", "\"cvv\":\"456\"",
                "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "session-data");
    }
}

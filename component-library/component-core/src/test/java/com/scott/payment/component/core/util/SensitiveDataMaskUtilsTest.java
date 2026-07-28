package com.scott.payment.component.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;


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
                  "pan":"5555555555554444",
                  "cvv":"123",
                  "mobile":"13812345678",
                  "phone":"+8613812345678",
                  "email":"scott@example.com",
                  "subEmail":"merchant@example.com",
                  "idCard":"110101199001011234",
                  "bankAccount":"6222021234567890123",
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
        assertThat(masked).contains("\"pan\":\"555555******4444\"");
        assertThat(masked).contains("\"cvv\":\"***\"");
        assertThat(masked).contains("\"mobile\":\"138****5678\"");
        assertThat(masked).contains("\"phone\":\"+86****5678\"");
        assertThat(masked).contains("\"email\":\"s***@example.com\"");
        assertThat(masked).contains("\"subEmail\":\"m***@example.com\"");
        assertThat(masked).contains("\"idCard\":\"***\"");
        assertThat(masked).contains("\"bankAccount\":\"6222******0123\"");
        assertThat(masked).contains("\"iban\":\"GB82******5432\"");
        assertThat(masked).doesNotContain("plain", "Bearer abc.def", "mpgs-password",
                "mid-password", "mid-password-alias", "merchant-key", "three-ds-token", "pem", "1234567890123",
                "scott@example.com", "merchant@example.com");
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

        try (var mocked = mockStatic(SensitiveDataMaskUtils.class, invocation -> {
            if ("maskJson".equals(invocation.getMethod().getName())) {
                throw new IllegalStateException("mask failed");
            }
            return invocation.callRealMethod();
        })) {
            String masked = SensitiveDataMaskUtils.maskJsonSafely(rawJson);

            assertThat(masked).isEqualTo("***MASK_FAILED***");
            assertThat(masked).doesNotContain("4111111111111111", "123");
        }
    }
}

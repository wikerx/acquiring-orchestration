package com.scott.payment.component.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SensitiveDataMaskUtilsTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 敏感数据脱敏工具测试，覆盖日志中常见凭据、卡号、联系方式和银行账号字段。
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
}

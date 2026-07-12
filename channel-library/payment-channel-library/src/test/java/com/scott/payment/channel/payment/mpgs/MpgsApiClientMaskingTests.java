package com.scott.payment.channel.payment.mpgs;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsApiClientMaskingTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 日志脱敏测试，验证卡号、CVV、3DS authenticationToken 和渠道密码等敏感字段不会以明文进入请求响应日志。
 * @status : create
 */
@Slf4j
class MpgsApiClientMaskingTests {

    /**
     * 验证 MPGS JSON 脱敏规则：完整卡号、CVV、3DS authenticationToken 和 apiPassword 都不能以明文进入日志。
     */
    @Test
    void shouldMaskMpgsCardNumberAndAuthenticationToken() {
        String json = "{\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"5123450000000008\",\"securityCode\":\"100\"}}},"
                + "\"authentication\":{\"threeDs\":{\"authenticationToken\":\"AAABBIIFmAAAAAAAAAAAAAAAAAA=\"}},"
                + "\"apiPassword\":\"secret-value\"}";
        log.info("MPGS脱敏测试开始，case=卡号、CVV、authenticationToken、apiPassword");

        String masked = MpgsApiClient.maskMpgsJson(json);
        log.info("MPGS脱敏测试输出，masked={}", masked);

        assertThat(masked).contains("\"number\":\"512345******0008\"");
        assertThat(masked).contains("\"securityCode\":\"***\"");
        assertThat(masked).contains("\"authenticationToken\":\"***\"");
        assertThat(masked).contains("\"apiPassword\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "secret-value");
    }

    /**
     * 验证查询类接口的空请求体可安全进入日志脱敏方法，不应出现空指针或伪造请求体。
     */
    @Test
    void shouldKeepBlankPayloadSafeForQueryLog() {
        log.info("MPGS脱敏测试开始，case=查询空请求体");

        String masked = MpgsApiClient.maskMpgsJson(null);

        assertThat(masked).isNull();
    }
}

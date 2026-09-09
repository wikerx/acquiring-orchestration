package com.scott.payment.component.security.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackJwtSignerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证平台回调 JWT 的签名和事件关联声明。
 * @status : create
 */
class MerchantCallbackJwtSignerTests {

    @Test
    void shouldSignVerifiableCallbackClaimsWithMatchingEventAndJti() {
        String secret = "callback-secret-must-be-at-least-32-bytes";
        String payloadSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String token = new MerchantCallbackJwtSigner().sign(
                "M1001", secret, "CBE-1", "NOTIFY-1", "TX-1", payloadSha256, 2,
                Instant.ofEpochSecond(1_786_000_000L), 180);

        JWT jwt = JWTUtil.parseToken(token);
        assertThat(jwt.verify(JWTSignerUtil.hs256(secret.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jwt.getPayload("iss")).isEqualTo("platform");
        assertThat(jwt.getPayload("merchantId")).isEqualTo("M1001");
        assertThat(jwt.getPayload("eventId")).isEqualTo("CBE-1");
        assertThat(jwt.getPayload("jti")).isEqualTo("CBE-1");
        assertThat(jwt.getPayload("payloadSha256")).isEqualTo(payloadSha256);
        assertThat(((Number) jwt.getPayload("callbackTimes")).intValue()).isEqualTo(2);
    }
}

package com.scott.payment.data.service.impl;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantCallbackJwtSigner;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.model.MerchantCallbackHttpRequest;
import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackRequestFactoryTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证回调 Header、JWT、事件 ID 和密文正文形成同一个不可拆分的协议请求。
 * @status : create
 */
class MerchantCallbackRequestFactoryTests {

    @Test
    void shouldBuildVerifiableEncryptedV1Request() {
        OpenApiPayloadCrypto crypto = new OpenApiPayloadCrypto();
        KeyPair merchantResponseKey = crypto.generateRsaKeyPair(2048);
        String secret = "merchant-callback-secret-at-least-32-bytes";
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();
        MerchantCallbackRequestFactory factory = new MerchantCallbackRequestFactory(
                merchantId -> new MerchantCallbackSecurityMaterial(
                        secret,
                        Base64.getEncoder().encodeToString(merchantResponseKey.getPublic().getEncoded())),
                crypto,
                new MerchantCallbackJwtSigner(),
                properties,
                Clock.fixed(Instant.ofEpochSecond(1_786_000_000L), ZoneOffset.UTC));
        DataMerchantNotificationTaskDO task = task();

        MerchantCallbackHttpRequest request = factory.create(task, 2);

        assertThat(request.headers().getFirst("Content-Type"))
                .isEqualTo("application/json;charset=UTF-8");
        assertThat(request.headers().getFirst(MerchantCallbackRequestFactory.HEADER_CALLBACK_VERSION)).isEqualTo("v1");
        assertThat(request.headers().getFirst(MerchantCallbackRequestFactory.HEADER_CALLBACK_TIMES)).isEqualTo("2");
        assertThat(request.headers().getFirst(MerchantCallbackRequestFactory.HEADER_CALLBACK_EVENT_ID))
                .isEqualTo("NOTIFY-1");

        String token = request.headers().getFirst("Authorization").substring("Bearer ".length());
        JWT jwt = JWTUtil.parseToken(token);
        assertThat(jwt.verify(JWTSignerUtil.hs256(secret.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jwt.getPayload("merchantId")).isEqualTo(task.getMerchantId());
        assertThat(jwt.getPayload("eventId")).isEqualTo("NOTIFY-1");
        assertThat(jwt.getPayload("jti")).isEqualTo("NOTIFY-1");

        JSONObject body = JsonUtils.parseObject(request.encryptedBody(), JSONObject.class);
        assertThat(jwt.getPayload("payloadSha256"))
                .isEqualTo(sha256Hex(body.getString("data")));
        assertThat(crypto.decrypt(body.getString("data"), merchantResponseKey.getPrivate()))
                .isEqualTo("{\"transactionId\":\"TX-1\",\"status\":\"SUCCESS\",\"email\":\"buyer@example.com\"}");
        assertThat(request.auditBody()).doesNotContain(body.getString("data"));
    }

    @Test
    void shouldKeepAutomaticEventIdStableAcrossDeliveryAttempts() {
        OpenApiPayloadCrypto crypto = new OpenApiPayloadCrypto();
        KeyPair merchantResponseKey = crypto.generateRsaKeyPair(2048);
        MerchantCallbackRequestFactory factory = new MerchantCallbackRequestFactory(
                merchantId -> new MerchantCallbackSecurityMaterial(
                        "merchant-callback-secret-at-least-32-bytes",
                        Base64.getEncoder().encodeToString(merchantResponseKey.getPublic().getEncoded())),
                crypto,
                new MerchantCallbackJwtSigner(),
                new DataMerchantNotificationProperties());
        DataMerchantNotificationTaskDO task = task();

        MerchantCallbackHttpRequest firstAttempt = factory.create(task, 1);
        MerchantCallbackHttpRequest secondAttempt = factory.create(task, 2);

        assertThat(firstAttempt.eventId()).isEqualTo(task.getNotifyId());
        assertThat(secondAttempt.eventId()).isEqualTo(firstAttempt.eventId());
        assertThat(secondAttempt.headers().getFirst(MerchantCallbackRequestFactory.HEADER_CALLBACK_TIMES))
                .isEqualTo("2");
    }

    private DataMerchantNotificationTaskDO task() {
        DataMerchantNotificationTaskDO task = new DataMerchantNotificationTaskDO();
        task.setMerchantId("M1001");
        task.setNotifyId("NOTIFY-1");
        task.setTransactionId("TX-1");
        task.setCallbackUrl("https://merchant.example.com/callback");
        task.setPayloadJson("{\"transactionId\":\"TX-1\",\"status\":\"SUCCESS\",\"email\":\"buyer@example.com\"}");
        task.setPayloadJsonMasked("{\"transactionId\":\"TX-1\",\"status\":\"SUCCESS\",\"email\":\"b***@example.com\"}");
        return task;
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}

package com.scott.payment.component.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisPropertiesTests
 * @date : 2026-07-29 16:00
 * @email : scott_x@163.com
 * @description : 验证 Redis Key 的环境隔离、输入约束和 Cluster 同槽摘要能力
 * @status : update
 */
@Slf4j
class PaymentRedisPropertiesTests {

    /**
     * 新增 Key 默认使用精简的系统、环境、领域和业务层级。
     */
    @Test
    void shouldBuildConciseBusinessKey() {
        log.info("测试 Redis 精简业务 Key，关键输入: merchant/info/pending、ISO 字典与非法片段");
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:dev");

        assertThat(properties.businessKey(
                "merchant",
                "info",
                "pending",
                "200045"
        )).isEqualTo("acquiring:dev:merchant:info:pending:200045");
        assertThat(properties.businessKey("merchant", "info", "200045"))
                .isEqualTo("acquiring:dev:merchant:info:200045");
        assertThat(properties.businessKey("iso", "currency"))
                .isEqualTo("acquiring:dev:iso:currency");
        assertThat(properties.businessKey("iso", "country"))
                .isEqualTo("acquiring:dev:iso:country");

        assertThatIllegalArgumentException().isThrownBy(() ->
                properties.businessKey("merchant:unsafe", "info", "200045"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                properties.businessKey("merchant", "info", "merchant 200045"));
        log.info("Redis 精简业务 Key 测试完成，结果: 短命名生成正确且非法片段被拒绝");
    }

    /**
     * 新业务同槽 Key 必须沿用精简命名，服务名和版本不得进入物理 Key。
     */
    @Test
    void shouldBuildConciseCoLocatedBusinessKeys() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        String slotIdentity = "rule-1001:merchant-200045:CNY:20260729";

        String aggregateKey = properties.coLocatedBusinessKey(
                "risk", "merchant-limit", slotIdentity, "total");
        String reservationKey = properties.coLocatedBusinessKey(
                "risk", "merchant-limit", slotIdentity, "reservation", "tx-digest");
        String anotherScopeKey = properties.coLocatedBusinessKey(
                "risk", "merchant-limit", slotIdentity + "-next", "total");

        assertThat(aggregateKey)
                .startsWith("acquiring:test:risk:merchant-limit:{")
                .endsWith("}:total")
                .doesNotContain("service-risk", ":v2:", slotIdentity);
        assertThat(hashTag(aggregateKey))
                .matches("[0-9a-f]{64}")
                .isEqualTo(hashTag(reservationKey))
                .isNotEqualTo(hashTag(anotherScopeKey));
    }

    /**
     * Key API 必须拒绝可能破坏命名空间、Cluster Slot 或 Key 长度预算的输入。
     */
    @Test
    void shouldRejectUnsafeKeyInputs() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");

        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "merchant 200045"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "{caller-owned-tag}"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "x".repeat(129)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.coLocatedBusinessKey(
                "risk", "merchant-limit", " ", "total"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.coLocatedBusinessKey(
                "risk", "merchant-limit", "rule-1001:merchant-200045"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.coLocatedBusinessKey(
                "risk:unsafe", "merchant-limit", "rule-1001:merchant-200045", "total"));

        properties.setKeyPrefix("acquiring");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "merchant-200045"));
        properties.setKeyPrefix("other:test");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "merchant-200045"));
        properties.setKeyPrefix("acquiring:test:extra");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.businessKey(
                "risk", "frequency", "merchant-200045"));
    }

    private String hashTag(String key) {
        int start = key.indexOf('{');
        int end = key.indexOf('}', start + 1);
        return key.substring(start + 1, end);
    }
}

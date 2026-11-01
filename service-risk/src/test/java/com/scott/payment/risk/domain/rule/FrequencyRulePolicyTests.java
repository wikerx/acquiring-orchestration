package com.scott.payment.risk.domain.rule;

import com.scott.payment.risk.domain.RiskListMatch;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证频率规则类型化配置的兼容性和失败关闭边界。
 */
class FrequencyRulePolicyTests {

    /**
     * 已知数值字段格式错误时必须拒绝执行，禁止回退数据库阈值后继续放行。
     */
    @Test
    void shouldRejectMalformedKnownNumericField() {
        RiskListMatch rule = baseRule();
        rule.setElementsJson("""
                {"elements":["ip"],"allowedCount":"not-a-number"}
                """);

        FrequencyRulePolicy policy = FrequencyRulePolicy.from(rule);

        assertThat(policy.valid()).isFalse();
        assertThat(policy.isExecutable(86_400, 1_000)).isFalse();
    }

    /**
     * 扩展 JSON 缺少可选阈值时沿用数据库列，保持历史规则兼容。
     */
    @Test
    void shouldUseDatabaseColumnsWhenOptionalJsonFieldsAreMissing() {
        RiskListMatch rule = baseRule();
        rule.setElementsJson("""
                {"elements":["ip"]}
                """);

        FrequencyRulePolicy policy = FrequencyRulePolicy.from(rule);

        assertThat(policy.valid()).isTrue();
        assertThat(policy.allowedCount()).isEqualTo(5);
        assertThat(policy.windowSeconds()).isEqualTo(3_600);
        assertThat(policy.isExecutable(86_400, 1_000)).isTrue();
    }

    /**
     * 新增未知扩展字段时继续解析已声明字段，避免规则平台升级破坏旧服务。
     */
    @Test
    void shouldIgnoreUnknownExtensionFields() {
        RiskListMatch rule = baseRule();
        rule.setElementsJson("""
                {
                  "elements":["ip"],
                  "statisticDimension":"ELEMENT_COMBINATION",
                  "allowedCount":4,
                  "futurePolicy":{"mode":"SHADOW"}
                }
                """);

        FrequencyRulePolicy policy = FrequencyRulePolicy.from(rule);

        assertThat(policy.valid()).isTrue();
        assertThat(policy.allowedCount()).isEqualTo(4);
        assertThat(policy.counterIdentities(Map.of("ip", "ip-hash"))).hasSize(1);
    }

    /**
     * 构造使用数据库阈值和窗口的基础频控规则。
     */
    private RiskListMatch baseRule() {
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(101L);
        rule.setThresholdCount(5);
        rule.setTimeWindowSeconds(3_600);
        return rule;
    }
}

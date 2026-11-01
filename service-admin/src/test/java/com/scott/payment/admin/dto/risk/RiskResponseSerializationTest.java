package com.scott.payment.admin.dto.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风险管理响应的 JSON 协议兼容性测试。
 */
class RiskResponseSerializationTest {

    /**
     * 使用生产同类 Jackson 模块验证字段命名、空值和大整数序列化约束。
     */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 风控评估列表继续使用前端既有的 snake_case 字段，并将 BIGINT 主键序列化为字符串。
     */
    @Test
    void evaluationShouldPreserveSnakeCaseContractAndStringId() {
        RiskDTOs.RiskEvaluationResponse response = new RiskDTOs.RiskEvaluationResponse();
        response.setId(9_007_199_254_740_993L);
        response.setRiskRecordNo("RISK-1001");
        response.setTransactionAmount(new BigDecimal("89.590000"));
        response.setTransactionCurrency("AUD");
        response.setDecisionResult("PASS");
        response.setHitCount(2);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("id").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("risk_record_no").asText()).isEqualTo("RISK-1001");
        assertThat(json.path("transaction_amount").decimalValue()).isEqualByComparingTo("89.590000");
        assertThat(json.path("transaction_currency").asText()).isEqualTo("AUD");
        assertThat(json.path("hit_count").asInt()).isEqualTo(2);
        assertThat(json.has("riskRecordNo")).isFalse();
    }

    /**
     * 工作台聚合响应继续使用 lowerCamelCase，并在没有变更日志时省略三个最近变更字段。
     */
    @Test
    void dashboardFunctionShouldPreserveLowerCamelCaseAndOptionalLatestChangeFields() {
        RiskDTOs.RiskDashboardFunctionResponse response = new RiskDTOs.RiskDashboardFunctionResponse();
        response.setFunctionCode("cardBin");
        response.setLatestUpdateTime(LocalDateTime.of(2026, 11, 1, 10, 30));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("functionCode").asText()).isEqualTo("cardBin");
        assertThat(json.has("function_code")).isFalse();
        assertThat(json.has("latestOperationType")).isFalse();
        assertThat(json.has("latestOperator")).isFalse();
        assertThat(json.has("latestOperationTime")).isFalse();
    }

    /**
     * 交易加黑列表只暴露管理端展示字段，禁止返回内部目标哈希和软删除标记。
     */
    @Test
    void tradeBlackShouldExcludeInternalHashAndDeleteMarker() {
        RiskDTOs.TradeBlackResponse response = new RiskDTOs.TradeBlackResponse();
        response.setId(9_007_199_254_740_993L);
        response.setBlackTargetType("CARD_FINGERPRINT");
        response.setBlackTargetValueMasked("fp_****1234");
        response.setStatus(1);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("black_target_type").asText()).isEqualTo("CARD_FINGERPRINT");
        assertThat(json.path("black_target_value_masked").asText()).isEqualTo("fp_****1234");
        assertThat(json.has("black_target_hash")).isFalse();
        assertThat(json.has("deleted")).isFalse();
    }
}

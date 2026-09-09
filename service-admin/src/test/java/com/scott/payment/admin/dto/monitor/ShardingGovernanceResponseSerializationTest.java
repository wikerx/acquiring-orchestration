package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingGovernanceResponseSerializationTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证分表治理主键和季度号段以 JSON 字符串返回，避免 JavaScript 安全整数精度损失。
 * @status : create
 */
class ShardingGovernanceResponseSerializationTest {

    /** 超出 JavaScript Number 安全整数范围的 18 位治理样本值。 */
    private static final long UNSAFE_INTEGER = 202_604_999_999_999_999L;
    /** 直接验证 DTO Jackson 注解的隔离序列化器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeGovernanceIdsAndAutoIncrementRangesAsStrings() throws Exception {
        ShardingPhysicalTableResponse physicalTable = new ShardingPhysicalTableResponse();
        physicalTable.setId(UNSAFE_INTEGER);
        physicalTable.setAutoIncrementStart(UNSAFE_INTEGER);
        physicalTable.setAutoIncrementCurrent(UNSAFE_INTEGER);
        physicalTable.setAutoIncrementMax(UNSAFE_INTEGER);

        JsonNode physicalJson = objectMapper.readTree(objectMapper.writeValueAsString(physicalTable));

        assertTextValue(physicalJson, "id");
        assertTextValue(physicalJson, "autoIncrementStart");
        assertTextValue(physicalJson, "autoIncrementCurrent");
        assertTextValue(physicalJson, "autoIncrementMax");
    }

    @Test
    void shouldSerializePreCreateAndIdRuleRangesAsStrings() throws Exception {
        ShardingTablePreCreateTableResultResponse tableResult = new ShardingTablePreCreateTableResultResponse();
        tableResult.setAutoIncrementStart(UNSAFE_INTEGER);
        tableResult.setAutoIncrementCurrent(UNSAFE_INTEGER);
        tableResult.setAutoIncrementMax(UNSAFE_INTEGER);
        JsonNode tableResultJson = objectMapper.readTree(objectMapper.writeValueAsString(tableResult));

        ShardingIdRuleResponse idRule = new ShardingIdRuleResponse();
        idRule.setStartSequence(UNSAFE_INTEGER);
        idRule.setMaxSequence(UNSAFE_INTEGER);
        idRule.setCurrentQuarterStartValue(UNSAFE_INTEGER);
        idRule.setCurrentQuarterMaxValue(UNSAFE_INTEGER);
        JsonNode idRuleJson = objectMapper.readTree(objectMapper.writeValueAsString(idRule));

        assertTextValue(tableResultJson, "autoIncrementStart");
        assertTextValue(tableResultJson, "autoIncrementCurrent");
        assertTextValue(tableResultJson, "autoIncrementMax");
        assertTextValue(idRuleJson, "startSequence");
        assertTextValue(idRuleJson, "maxSequence");
        assertTextValue(idRuleJson, "currentQuarterStartValue");
        assertTextValue(idRuleJson, "currentQuarterMaxValue");
    }

    @Test
    void shouldSerializeCreateLogIdAsString() throws Exception {
        ShardingTableCreateLogResponse createLog = new ShardingTableCreateLogResponse();
        createLog.setId(UNSAFE_INTEGER);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(createLog));

        assertTextValue(json, "id");
    }

    private void assertTextValue(JsonNode json, String fieldName) {
        assertThat(json.path(fieldName).isTextual()).isTrue();
        assertThat(json.path(fieldName).asText()).isEqualTo(Long.toString(UNSAFE_INTEGER));
    }
}

package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户后台交易列表分片时间序列化契约测试。
 */
class MerchantTransactionShardingTimeSerializationTest {

    private static final LocalDateTime SHARDING_TIME =
            LocalDateTime.of(2026, 8, 2, 12, 8, 36, 255_000_000);

    /** 使用秒级全局配置模拟生产 ObjectMapper，验证列表响应保留毫秒分片时间。 */
    private final ObjectMapper objectMapper = secondsConfiguredObjectMapper();

    /**
     * 验证商户列表响应保留详情和后续动作所需的毫秒级分片时间。
     */
    @Test
    void transactionResponsesShouldPreserveMillisecondShardingTimes() throws Exception {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setTransactionDateTime(SHARDING_TIME);
        order.setRootTransactionDateTime(SHARDING_TIME);
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setTransactionDateTime(SHARDING_TIME);
        operation.setRootTransactionDateTime(SHARDING_TIME);

        JsonNode orderJson = objectMapper.readTree(objectMapper.writeValueAsString(order));
        JsonNode operationJson = objectMapper.readTree(objectMapper.writeValueAsString(operation));

        assertThat(orderJson.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(orderJson.path("rootTransactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(operationJson.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(operationJson.path("rootTransactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
    }

    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

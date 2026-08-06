package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO.TransactionInfoDTO;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 内部接口分片时间序列化契约测试。
 */
class PaymentShardingTimeSerializationTest {

    private static final LocalDateTime SHARDING_TIME =
            LocalDateTime.of(2026, 8, 2, 12, 8, 36, 255_000_000);

    /** 使用秒级全局配置模拟生产 ObjectMapper，验证内部响应保留毫秒分片时间。 */
    private final ObjectMapper objectMapper = secondsConfiguredObjectMapper();

    /**
     * 验证创建、查询和后台逻辑表响应均保留毫秒级分片时间。
     */
    @Test
    void internalResponsesShouldPreserveMillisecondShardingTimes() throws Exception {
        PaymentCreateResultDTO createResult = new PaymentCreateResultDTO();
        createResult.setTransactionDateTime(SHARDING_TIME);
        createResult.setRootTransactionDateTime(SHARDING_TIME);
        TransactionInfoDTO queryInfo = new TransactionInfoDTO();
        queryInfo.setTransactionDateTime(SHARDING_TIME);
        queryInfo.setRootTransactionDateTime(SHARDING_TIME);
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setTransactionDateTime(SHARDING_TIME);
        order.setRootTransactionDateTime(SHARDING_TIME);
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setTransactionDateTime(SHARDING_TIME);
        operation.setRootTransactionDateTime(SHARDING_TIME);

        assertShardingTimes(createResult);
        assertShardingTimes(queryInfo);
        assertShardingTimes(order);
        assertShardingTimes(operation);
    }

    /**
     * 验证 Payment 接收的创建或后续动作命令能按毫秒精度还原分片键。
     */
    @Test
    void paymentCommandShouldPreserveMillisecondShardingTimes() throws Exception {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setTransactionDateTime(SHARDING_TIME);
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo =
                new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionDateTime(SHARDING_TIME);
        transactionInfo.setRootTransactionDateTime(SHARDING_TIME);
        command.setTransactionInfo(transactionInfo);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(command));

        assertThat(json.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(json.path("transactionInfo").path("sourceTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.255");
        assertThat(json.path("transactionInfo").path("rootTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.255");
    }

    private void assertShardingTimes(Object value) throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(value));
        assertThat(json.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(json.path("rootTransactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
    }

    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

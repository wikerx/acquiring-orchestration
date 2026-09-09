package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientShardingTimeSerializationTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 Payment 时的分片时间序列化契约测试。
 * @status : create
 */
class PaymentClientShardingTimeSerializationTest {

    private static final LocalDateTime SHARDING_TIME =
            LocalDateTime.of(2026, 8, 2, 12, 8, 36, 255_000_000);

    /** 使用秒级全局配置模拟生产 ObjectMapper，验证内部请求保留毫秒分片时间。 */
    private final ObjectMapper objectMapper = secondsConfiguredObjectMapper();

    /**
     * 验证创建和后续动作请求在进入 Payment 前保留毫秒级分片时间。
     */
    @Test
    void paymentRequestShouldPreserveMillisecondShardingTimes() throws Exception {
        PaymentCreateClientRequestDTO request = new PaymentCreateClientRequestDTO();
        request.setTransactionDateTime(SHARDING_TIME);
        PaymentCreateClientRequestDTO.TransactionInfoDTO transactionInfo =
                new PaymentCreateClientRequestDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionDateTime(SHARDING_TIME);
        transactionInfo.setRootTransactionDateTime(SHARDING_TIME);
        request.setTransactionInfo(transactionInfo);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.255");
        assertThat(json.path("transactionInfo").path("sourceTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.255");
        assertThat(json.path("transactionInfo").path("rootTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.255");
    }

    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

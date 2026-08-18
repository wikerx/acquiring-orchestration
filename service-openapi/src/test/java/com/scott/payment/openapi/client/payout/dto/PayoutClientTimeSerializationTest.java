package com.scott.payment.openapi.client.payout.dto;

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
 * 验证 OpenAPI 调用 Payout 时使用稳定的毫秒时间字符串契约。
 */
class PayoutClientTimeSerializationTest {

    private static final LocalDateTime REQUEST_TIME =
            LocalDateTime.of(2026, 8, 3, 1, 24, 25, 962_000_000);

    /** 使用秒级全局时间配置，验证 DTO 字段自身仍强制输出毫秒分片时间。 */
    private final ObjectMapper objectMapper = secondsConfiguredObjectMapper();

    /** 验证代付内部请求不会受全局秒级序列化配置影响而丢失毫秒。 */
    @Test
    void payoutRequestShouldSerializeTransactionTimeAsMillisecondString() throws Exception {
        PayoutCreateClientRequestDTO request = new PayoutCreateClientRequestDTO();
        request.setTransactionDateTime(REQUEST_TIME);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.path("transactionDateTime").asText())
                .isEqualTo("2026-08-03 01:24:25.962");
    }

    /** 构造刻意使用秒级 {@link LocalDateTime} 序列化器的测试对象。 */
    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

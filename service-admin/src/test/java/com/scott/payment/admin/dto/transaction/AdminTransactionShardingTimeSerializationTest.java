package com.scott.payment.admin.dto.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO.TransactionInfoDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionShardingTimeSerializationTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理后台交易列表分片时间序列化契约测试。
 * @status : create
 *
 *
 * <p>页面展示仍可隐藏毫秒，但列表响应必须保留数据库 {@code DATETIME(3)} 精度，
 * 以便详情请求把真实交易时间和根主单时间原样传回后端。</p>
 */
class AdminTransactionShardingTimeSerializationTest {

    private static final DateTimeFormatter COMMON_SECONDS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 8, 2, 12, 8, 36, 233_000_000);

    /** 使用秒级全局配置模拟生产 ObjectMapper，验证 DTO 的字段级毫秒格式覆盖。 */
    private final ObjectMapper objectMapper = secondsConfiguredObjectMapper();

    /**
     * 验证主单和动作单响应不会被公共秒级格式截断分片时间。
     */
    @Test
    void transactionResponsesShouldPreserveMillisecondShardingTimes() throws Exception {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setTransactionDateTime(TRANSACTION_DATE_TIME);
        order.setRootTransactionDateTime(TRANSACTION_DATE_TIME);
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setTransactionDateTime(TRANSACTION_DATE_TIME);
        operation.setRootTransactionDateTime(TRANSACTION_DATE_TIME);

        JsonNode orderJson = objectMapper.readTree(objectMapper.writeValueAsString(order));
        JsonNode operationJson = objectMapper.readTree(objectMapper.writeValueAsString(operation));

        assertThat(orderJson.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.233");
        assertThat(orderJson.path("rootTransactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.233");
        assertThat(operationJson.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.233");
        assertThat(operationJson.path("rootTransactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.233");
    }

    /**
     * 验证 Admin 调用 Payment 的后续动作命令不会在跨服务序列化时丢失分片毫秒。
     */
    @Test
    void paymentActionCommandShouldPreserveMillisecondShardingTimes() throws Exception {
        PaymentTransactionActionClientRequestDTO request = new PaymentTransactionActionClientRequestDTO();
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        TransactionInfoDTO transactionInfo = new TransactionInfoDTO();
        transactionInfo.setSourceTransactionDateTime(TRANSACTION_DATE_TIME);
        transactionInfo.setRootTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setTransactionInfo(transactionInfo);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.path("transactionDateTime").asText()).isEqualTo("2026-08-02 12:08:36.233");
        assertThat(json.path("transactionInfo").path("sourceTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.233");
        assertThat(json.path("transactionInfo").path("rootTransactionDateTime").asText())
                .isEqualTo("2026-08-02 12:08:36.233");
    }

    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(COMMON_SECONDS_FORMATTER));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

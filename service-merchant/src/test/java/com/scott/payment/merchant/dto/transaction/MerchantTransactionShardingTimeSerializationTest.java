package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
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

    /**
     * 使用秒级全局配置模拟生产 ObjectMapper，验证列表响应保留毫秒分片时间。
     */
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

    /**
     * 验证平台与渠道内部的对账状态不会进入商户交易查询契约。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void merchantTransactionContractShouldNotExposeReconciliationStatus() throws Exception {
        JsonNode queryJson = objectMapper.readTree(objectMapper.writeValueAsString(new TransactionPageQuery()));
        JsonNode orderJson = objectMapper.readTree(objectMapper.writeValueAsString(new TransactionOrderResponse()));
        JsonNode operationJson = objectMapper.readTree(objectMapper.writeValueAsString(new TransactionOperationResponse()));

        assertThat(queryJson.has("reconciliationStatus")).isFalse();
        assertThat(queryJson.has("channelOrderNo")).isFalse();
        assertThat(orderJson.has("reconciliationStatus")).isFalse();
        assertThat(orderJson.fieldNames()).toIterable()
                .doesNotContain("channelCode", "channelOrderNo",
                        "transactionCurrency", "transactionAmount", "transactionRate",
                        "dccEnabled", "edcEnabled", "settlementRate");
        assertThat(operationJson.has("reconciliationStatus")).isFalse();
        assertThat(operationJson.fieldNames()).toIterable()
                .doesNotContain("channelCode", "channelOrderNo", "channelTransactionId", "acquirerReferenceNo",
                        "transactionCurrency", "transactionAmount", "transactionRate",
                        "dccEnabled", "edcEnabled", "settlementRate");
    }

    /**
     * 验证商户退款响应只返回标签金额，不暴露渠道交易金额口径。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void merchantRefundContractShouldOnlyExposeLabelAmount() throws Exception {
        RefundRecord refund = new RefundRecord();
        refund.setLabelCurrency("EUR");
        refund.setLabelAmount(new java.math.BigDecimal("20.00"));

        JsonNode refundJson = objectMapper.readTree(objectMapper.writeValueAsString(refund));

        assertThat(refundJson.path("labelCurrency").asText()).isEqualTo("EUR");
        assertThat(refundJson.path("labelAmount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(refundJson.fieldNames()).toIterable()
                .doesNotContain("transactionCurrency", "transactionAmount", "transactionRate");
    }

    private ObjectMapper secondsConfiguredObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return JsonMapper.builder().addModule(javaTimeModule).build();
    }
}

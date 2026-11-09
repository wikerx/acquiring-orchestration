package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantApiInteractionLogResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantNotificationDeliveryLogResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantNotificationResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionAmountChangeResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionFlowEventResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionStatusHistoryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户交易详情扩展记录的序列化契约测试。
 */
class TransactionDetailResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证既有集合字段名保持不变，元素由明确类型承载且不包含渠道报文或商户密文。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void detailSectionsShouldUseTypedSafeResponses() throws Exception {
        TransactionStatusHistoryResponse status = new TransactionStatusHistoryResponse();
        status.setToStatus("SUCCESS");
        TransactionFlowEventResponse flowEvent = new TransactionFlowEventResponse();
        flowEvent.setEventType("CAPTURE_COMPLETED");
        TransactionAmountChangeResponse amountChange = new TransactionAmountChangeResponse();
        amountChange.setAmountCurrency("JPY");
        MerchantNotificationResponse notification = new MerchantNotificationResponse();
        notification.setNotifyStatus("SUCCESS");
        MerchantNotificationDeliveryLogResponse notificationLog = new MerchantNotificationDeliveryLogResponse();
        notificationLog.setDeliveryStatus("SUCCESS");
        MerchantApiInteractionLogResponse apiLog = new MerchantApiInteractionLogResponse();
        apiLog.setMerchantResponseCode("S000");

        TransactionDetailResponse response = new TransactionDetailResponse();
        response.setStatusHistory(List.of(status));
        response.setFlowEvents(List.of(flowEvent));
        response.setAmountChanges(List.of(amountChange));
        response.setMerchantNotifications(List.of(notification));
        response.setMerchantNotificationLogs(List.of(notificationLog));
        response.setMerchantApiInteractionLogs(List.of(apiLog));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("statusHistory").get(0).path("toStatus").asText()).isEqualTo("SUCCESS");
        assertThat(json.path("flowEvents").get(0).path("eventType").asText()).isEqualTo("CAPTURE_COMPLETED");
        assertThat(json.path("amountChanges").get(0).path("amountCurrency").asText()).isEqualTo("JPY");
        assertThat(json.path("merchantNotifications").get(0).path("notifyStatus").asText()).isEqualTo("SUCCESS");
        assertThat(json.path("merchantNotificationLogs").get(0).path("deliveryStatus").asText()).isEqualTo("SUCCESS");
        assertThat(json.path("merchantApiInteractionLogs").get(0).path("merchantResponseCode").asText())
                .isEqualTo("S000");
        assertThat(json.fieldNames()).toIterable()
                .doesNotContain("channelRequests", "channelInteractionLogs");
        assertThat(json.path("merchantApiInteractionLogs").get(0).fieldNames())
                .toIterable()
                .doesNotContain("requestCipherMasked", "requestPlainJsonMasked",
                        "responsePlainJsonMasked", "responseCipherMasked");
    }

    /**
     * 验证未加载扩展记录时继续返回既有空数组，避免前端字段兼容性回退。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void detailSectionsShouldRemainEmptyArraysByDefault() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new TransactionDetailResponse()));

        assertThat(json.path("statusHistory").isArray()).isTrue();
        assertThat(json.path("statusHistory").isEmpty()).isTrue();
        assertThat(json.path("flowEvents").isArray()).isTrue();
        assertThat(json.path("amountChanges").isArray()).isTrue();
        assertThat(json.fieldNames()).toIterable()
                .doesNotContain("channelRequests", "channelInteractionLogs");
        assertThat(json.path("merchantNotifications").isArray()).isTrue();
        assertThat(json.path("merchantNotificationLogs").isArray()).isTrue();
        assertThat(json.path("merchantApiInteractionLogs").isArray()).isTrue();
    }
}

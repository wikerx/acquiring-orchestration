package com.scott.payment.openapi.vo.payment;

import com.scott.payment.component.core.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户交易查询响应的序列化契约测试，确保结果投影所需的内部诊断字段不会随交易数组泄露。
 */
class PaymentQueryVOContractTest {

    /**
     * 内部阶段和原因仅参与服务端投影；即使已有值，也只能返回投影后的公开码、文案和真实交易状态。
     */
    @Test
    void shouldHideInternalProjectionContextFromTransactionArray() {
        PaymentQueryVO.TransactionInfoVO transaction = new PaymentQueryVO.TransactionInfoVO();
        transaction.setCode("F3000");
        transaction.setMessage("The channel result is not yet confirmed. Check the original order and avoid submitting it again.");
        transaction.setTransactionStatus("PROCESSING");
        transaction.setProcessStage("CHANNEL_INVOKING");
        transaction.setFailReasonCode("CHANNEL_REQUEST_FAILED");
        transaction.setFailReasonMessage("Internal channel exception detail");
        transaction.setPendingReasonCode("CHANNEL_RESULT_UNKNOWN");

        PaymentQueryVO response = new PaymentQueryVO();
        response.getTransactionInfo().add(transaction);

        String responseJson = JsonUtils.toJsonString(response);

        assertThat(responseJson).contains("\"code\":\"F3000\"");
        assertThat(responseJson).contains("\"message\":\"The channel result is not yet confirmed. Check the original order and avoid submitting it again.\"");
        assertThat(responseJson).contains("\"transactionStatus\":\"PROCESSING\"");
        assertThat(responseJson).doesNotContain("processStage");
        assertThat(responseJson).doesNotContain("failReasonCode");
        assertThat(responseJson).doesNotContain("failReasonMessage");
        assertThat(responseJson).doesNotContain("pendingReasonCode");
        assertThat(responseJson).doesNotContain("CHANNEL_INVOKING");
        assertThat(responseJson).doesNotContain("CHANNEL_REQUEST_FAILED");
        assertThat(responseJson).doesNotContain("Internal channel exception detail");
        assertThat(responseJson).doesNotContain("CHANNEL_RESULT_UNKNOWN");
    }
}

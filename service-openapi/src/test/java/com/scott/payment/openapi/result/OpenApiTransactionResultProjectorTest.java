package com.scott.payment.openapi.result;

import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionProjection;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionResultProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 交易结果投影的状态与受众边界测试。
 *
 * <p>验证渠道结果未确认、明确渠道拒绝和平台拒绝分别投影，且不会覆盖支付核心的真实状态。</p>
 */
class OpenApiTransactionResultProjectorTest {

    /** 成功、处理中和普通等待状态必须映射到交易域标准码。 */
    @Test
    void shouldProjectNormalTransactionStates() {
        assertThat(project("SUCCESS", "FINISHED", null, null, "T200").resultCode())
                .isEqualTo(OpenApiResultCode.TRANSACTION_SUCCESS);
        assertThat(project("PROCESSING", "CHANNEL_PROCESSING", null, null, "T202").resultCode())
                .isEqualTo(OpenApiResultCode.TRANSACTION_PROCESSING);
        assertThat(project("PENDING", "WAITING_3DS", null, "NEED_REDIRECT", "T203").resultCode())
                .isEqualTo(OpenApiResultCode.TRANSACTION_PENDING);
    }

    /**
     * 渠道调用结果未确认时必须返回 F3000，同时保留 PROCESSING，禁止伪造失败终态。
     */
    @Test
    void shouldProjectUnconfirmedChannelResultWithoutChangingNonTerminalStatus() {
        OpenApiTransactionProjection projection = OpenApiTransactionResultProjector.project(
                "PROCESSING",
                "CHANNEL_PROCESSING",
                null,
                "CHANNEL_RESULT_UNKNOWN",
                "T202"
        );

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED);
        assertThat(projection.failureReasonCode()).isEqualTo("CHANNEL_RESULT_UNCONFIRMED");
        assertThat(projection.failureReasonMessage())
                .isEqualTo("The channel result is not yet confirmed. Check the original order and avoid submitting it again.");
    }

    /** 已确认终态必须覆盖遗留等待原因，避免历史 CHANNEL_RESULT_UNKNOWN 把成功结果降级为 F3000。 */
    @Test
    void shouldPreferConfirmedTerminalStateOverStalePendingReason() {
        OpenApiTransactionProjection projection = OpenApiTransactionResultProjector.project(
                "SUCCESS",
                "FINISHED",
                null,
                "CHANNEL_RESULT_UNKNOWN",
                "T200"
        );

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.TRANSACTION_SUCCESS);
        assertThat(projection.failureReasonCode()).isNull();
        assertThat(projection.failureReasonMessage()).isNull();
    }

    /** 只有渠道明确返回拒绝时才允许对外归类为 F1000。 */
    @Test
    void shouldProjectConfirmedChannelDecline() {
        OpenApiTransactionProjection projection = project(
                "FAILED", "FINISHED", "CHANNEL_DECLINED", null, "F210");

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.CHANNEL_RETURNED_ERROR);
        assertThat(projection.failureReasonCode()).isEqualTo("CHANNEL_DECLINED");
        assertThat(projection.failureReasonMessage())
                .isEqualTo("The transaction was declined by the issuer bank. Check the original order, then ask the payer to contact their bank or use another payment method when applicable.");
        assertThat(projection.message(OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US))
                .isEqualTo("The transaction was declined by the issuer bank. Check the original order, then ask the payer to contact their bank or use another payment method when applicable.");
    }

    /** 平台风控和状态机拒绝必须保留业务语义，不能伪装成渠道失败。 */
    @Test
    void shouldProjectPlatformBusinessRejections() {
        OpenApiTransactionProjection risk = project(
                "FAILED", "FINISHED", "RISK_REJECTED", null, "F210");
        OpenApiTransactionProjection stateConflict = project(
                "FAILED", "FINISHED", "STATE_TRANSITION_DENIED", null, "F210");

        assertThat(risk.resultCode()).isEqualTo(OpenApiResultCode.PLATFORM_RISK_REJECTED);
        assertThat(risk.failureReasonCode()).isEqualTo("PLATFORM_RISK_REJECTED");
        assertThat(stateConflict.resultCode()).isEqualTo(OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED);
        assertThat(stateConflict.failureReasonCode()).isEqualTo("ORIGINAL_ACTION_NOT_ALLOWED");
    }

    /** FAILED 已是明确终态，不得再投影为表示结果未知的 F3000。 */
    @Test
    void shouldRejectFailedAndUnconfirmedContradiction() {
        for (String failureReason : new String[]{
                "CHANNEL_REQUEST_FAILED", "CHANNEL_TIMEOUT", "CHANNEL_RESPONSE_INVALID"}) {
            OpenApiTransactionProjection projection = project(
                    "FAILED", "FINISHED", failureReason, null, "F210");

            assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
            assertThat(projection.failureReasonCode()).isEqualTo("PLATFORM_PROCESSING_ERROR");
        }
    }

    /** 请求明确未发出属于平台处理失败，不得谎报为渠道结果未确认。 */
    @Test
    void shouldProjectRequestNotSentAsPlatformFailure() {
        OpenApiTransactionProjection projection = project(
                "FAILED", "FINISHED", "CHANNEL_REQUEST_NOT_SENT", null, "F210");

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(projection.failureReasonCode()).isEqualTo("PLATFORM_PROCESSING_ERROR");
    }

    /** 历史 F210 没有来源证据时只能归为通用交易失败，禁止机械映射成渠道拒绝。 */
    @Test
    void shouldNotInferChannelDeclineFromLegacyMerchantCode() {
        OpenApiTransactionProjection projection = project("FAILED", "FINISHED", null, null, "F210");

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED);
        assertThat(projection.failureReasonCode()).isEqualTo("TRANSACTION_FAILED");
    }

    /** 无法识别的核心状态属于平台投影异常，公开响应不得猜测交易结果。 */
    @Test
    void shouldFailClosedForUnknownTransactionStatus() {
        OpenApiTransactionProjection projection = project("UNKNOWN", null, null, null, null);

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(projection.failureReasonCode()).isEqualTo("PLATFORM_PROCESSING_ERROR");
    }

    /** 使用完整核心上下文调用投影器，保持各测试的状态断言聚焦。 */
    private OpenApiTransactionProjection project(String status,
                                                 String processStage,
                                                 String failureReason,
                                                 String pendingReason,
                                                 String legacyMerchantCode) {
        return OpenApiTransactionResultProjector.project(
                status, processStage, failureReason, pendingReason, legacyMerchantCode);
    }
}

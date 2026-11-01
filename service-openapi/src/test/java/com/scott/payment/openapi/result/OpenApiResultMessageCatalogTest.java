package com.scott.payment.openapi.result;

import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiResultMessageCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI V1.3 分层文案的完整性与安全回退测试。
 *
 * <p>覆盖内部、商户、付款人三类受众，验证动态参数过滤、敏感内容拦截和占位符回退。</p>
 */
class OpenApiResultMessageCatalogTest {

    /**
     * 48 个公开码必须完整登记内部、商户和付款人文案，不能出现空文案或未展开占位符。
     */
    @Test
    void shouldPublishCompleteAudienceAndLocaleMessages() {
        for (OpenApiResultCode code : OpenApiResultCode.values()) {
            assertPublished(code, OpenApiMessageAudience.INTERNAL, OpenApiMessageLocale.ZH_CN);
            assertPublished(code, OpenApiMessageAudience.INTERNAL, OpenApiMessageLocale.EN_US);
            assertPublished(code, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.ZH_CN);
            assertPublished(code, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US);
            assertPublished(code, OpenApiMessageAudience.PAYER, OpenApiMessageLocale.ZH_CN);
            assertPublished(code, OpenApiMessageAudience.PAYER, OpenApiMessageLocale.EN_US);
        }
    }

    /**
     * 外部出口固定使用英文时，全部商户和付款人模板都不得包含中文字符。
     */
    @Test
    void shouldKeepEveryExternalEnglishTemplateFreeOfChineseCharacters() {
        for (OpenApiResultCode code : OpenApiResultCode.values()) {
            assertThat(OpenApiResultMessageCatalog.message(
                    code, OpenApiMessageAudience.INTERNAL, OpenApiMessageLocale.EN_US))
                    .as("internal message for %s", code.getCode())
                    .doesNotContainPattern("[\\p{IsHan}]");
            assertThat(OpenApiResultMessageCatalog.message(
                    code, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US))
                    .as("merchant message for %s", code.getCode())
                    .doesNotContainPattern("[\\p{IsHan}]");
            assertThat(OpenApiResultMessageCatalog.message(
                    code, OpenApiMessageAudience.PAYER, OpenApiMessageLocale.EN_US))
                    .as("payer message for %s", code.getCode())
                    .doesNotContainPattern("[\\p{IsHan}]");
        }
    }

    /**
     * 受控字段参数可以进入对应模板，但参数缺失或非法时必须回退到同对象安全静态文案。
     */
    @Test
    void shouldRenderControlledPlaceholderOrFallBackSafely() {
        assertThat(OpenApiResultMessageCatalog.message(
                OpenApiResultCode.PARAM_INVALID,
                OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US,
                Map.of("field", "orderInfo.orderId")))
                .isEqualTo("Invalid request parameter: orderInfo.orderId. Check the API requirements.");

        String missingParameter = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.PARAM_INVALID,
                OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US,
                Map.of());
        String unsafeParameter = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.PARAM_INVALID,
                OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US,
                Map.of("field", "<script>alert(1)</script>"));

        assertThat(missingParameter).isEqualTo(OpenApiResultCode.PARAM_INVALID.getMessage());
        assertThat(unsafeParameter).isEqualTo(OpenApiResultCode.PARAM_INVALID.getMessage());
        assertThat(missingParameter).doesNotContain("{").doesNotContain("}");
        assertThat(unsafeParameter).doesNotContain("<script>");
    }

    /**
     * 渠道失败按展示对象输出不同英文文案，只有管理端可以看到受控渠道摘要。
     */
    @Test
    void shouldKeepAudienceBoundaryWhenSelectingMessages() {
        Map<String, String> diagnosticParameters = Map.of(
                "channelName", "MPGS",
                "channelResponseCode", "DECLINED",
                "channelResponseMessage", "Transaction failed",
                "channelReference", "REF-1");

        String internal = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                OpenApiMessageAudience.INTERNAL,
                OpenApiMessageLocale.EN_US,
                diagnosticParameters);
        String merchant = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US,
                diagnosticParameters);
        String payer = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                OpenApiMessageAudience.PAYER,
                OpenApiMessageLocale.EN_US,
                diagnosticParameters);

        assertThat(internal).isEqualTo(
                "Channel processing failed (code: DECLINED, message: Transaction failed). "
                        + "Please check the original order or contact platform support.");
        assertThat(merchant).isEqualTo(
                "The transaction was declined by the issuer bank. Check the original order, "
                        + "then ask the payer to contact their bank or use another payment method when applicable.");
        assertThat(payer).isEqualTo(
                "The transaction was declined. Please contact your card issuer or try a different payment method.");
        assertThat(merchant).doesNotContain("MPGS", "DECLINED", "Transaction failed", "REF-1");
        assertThat(payer).doesNotContain("MPGS", "DECLINED", "Transaction failed", "REF-1");
    }

    /**
     * 疑似包含卡号等敏感内容的渠道描述必须整体回退，不能进入管理端文案。
     */
    @Test
    void shouldRejectSensitiveChannelResponseMessage() {
        String internal = OpenApiResultMessageCatalog.message(
                OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                OpenApiMessageAudience.INTERNAL,
                OpenApiMessageLocale.EN_US,
                Map.of(
                        "channelResponseCode", "DECLINED",
                        "channelResponseMessage", "cardNumber=4111111111111111"));

        assertThat(internal)
                .isEqualTo("Channel processing failed. Please check the original order or contact platform support.")
                .doesNotContain("4111111111111111");
    }

    /**
     * 字段路径只允许进入商户和内部模板，诊断原因仅允许内部模板使用，付款人始终获得静态安全文案。
     */
    @Test
    void shouldIsolateContextualParameterDetailsByAudience() {
        OpenApiErrorProjection projection = OpenApiErrorMapper.project(
                OpenApiContextualException.parameter(
                        OpenApiResultCode.PARAM_INVALID,
                        "orderInfo.amount",
                        "amount must be greater than zero"));

        String internal = OpenApiResultMessageCatalog.message(
                projection.resultCode(), OpenApiMessageAudience.INTERNAL,
                OpenApiMessageLocale.ZH_CN, projection.messageParameters());
        String merchant = OpenApiResultMessageCatalog.message(
                projection.resultCode(), OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US, projection.messageParameters());
        String payer = OpenApiResultMessageCatalog.message(
                projection.resultCode(), OpenApiMessageAudience.PAYER,
                OpenApiMessageLocale.EN_US, projection.messageParameters());

        assertThat(internal).contains("orderInfo.amount", "amount must be greater than zero");
        assertThat(merchant).contains("orderInfo.amount").doesNotContain("greater than zero");
        assertThat(payer)
                .isEqualTo("Check the information you entered and submit again.")
                .doesNotContain("orderInfo.amount", "greater than zero");
    }

    /**
     * 未登记格式的字段路径必须触发静态回退，不能借模板参数向任一外部对象注入内容。
     */
    @Test
    void shouldFallBackWhenContextualFieldPathIsUnsafe() {
        OpenApiErrorProjection projection = OpenApiErrorMapper.project(
                OpenApiContextualException.parameter(
                        OpenApiResultCode.PARAM_INVALID,
                        "orderInfo.amount<script>",
                        "invalid amount"));

        String merchant = OpenApiResultMessageCatalog.message(
                projection.resultCode(), OpenApiMessageAudience.MERCHANT,
                OpenApiMessageLocale.EN_US, projection.messageParameters());

        assertThat(merchant)
                .isEqualTo(OpenApiResultCode.PARAM_INVALID.getMessage())
                .doesNotContain("<script>", "invalid amount");
    }

    /**
     * 校验指定受众和语言能够得到不含未展开占位符的已发布文案。
     */
    private void assertPublished(OpenApiResultCode code,
                                 OpenApiMessageAudience audience,
                                 OpenApiMessageLocale locale) {
        String message = OpenApiResultMessageCatalog.message(code, audience, locale);
        assertThat(message)
                .as("%s %s %s", code.getCode(), audience, locale)
                .isNotBlank()
                .doesNotContain("{")
                .doesNotContain("}");
    }
}

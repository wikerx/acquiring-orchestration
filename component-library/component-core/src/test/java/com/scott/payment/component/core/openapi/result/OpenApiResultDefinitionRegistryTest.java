package com.scott.payment.component.core.openapi.result;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI V1.3 治理注册表的完整性和受众隔离契约测试。
 *
 * <p>验证 48 个已发布编码都有完整元数据，且内部、商户和付款人文案不会跨越权限边界。</p>
 */
class OpenApiResultDefinitionRegistryTest {

    /** 48 个已发布返回码必须逐一登记规范要求的全部逻辑元数据。 */
    @Test
    void shouldRegisterCompleteMetadataForEveryPublishedCode() {
        assertThat(OpenApiResultDefinitionRegistry.definitions()).hasSize(48);
        assertThat(OpenApiResultCode.values()).allSatisfy(code -> {
            OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(code);
            assertThat(definition.resultCode()).isEqualTo(code);
            assertThat(definition.meaning()).isNotBlank();
            assertThat(definition.exclusions()).isNotBlank();
            assertThat(definition.applicableContracts()).isNotEmpty();
            assertThat(definition.phase()).isNotNull();
            assertThat(definition.origin()).isNotNull();
            assertThat(definition.messages()).containsKeys(
                    "internal.zh-CN", "internal.en-US", "merchant.zh-CN", "merchant.en-US",
                    "payer.zh-CN", "payer.en-US");
            assertThat(definition.messages().values()).allMatch(message -> message != null && !message.isBlank());
            assertThat(definition.evidence()).isNotNull();
            assertThat(definition.handlingPolicy()).isEqualTo(code.handlingPolicy());
            assertThat(definition.dataContract()).isNotBlank();
            assertThat(definition.mappingRules()).isNotEmpty();
            assertThat(definition.owner()).isNotBlank();
            assertThat(definition.version()).isEqualTo("V1.3");
            assertThat(definition.lifecycle())
                    .isIn(OpenApiResultLifecycle.REGISTERED, OpenApiResultLifecycle.ACTIVE);
        });
    }

    /**
     * 已发布但尚无生产触发路径的保留或扩展码必须保持 REGISTERED，防止治理报表误报为已启用。
     */
    @Test
    void shouldDistinguishRegisteredCodesFromActiveRuntimeCodes() {
        Set<OpenApiResultCode> registeredCodes = OpenApiResultDefinitionRegistry.definitions().entrySet().stream()
                .filter(entry -> entry.getValue().lifecycle() == OpenApiResultLifecycle.REGISTERED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(registeredCodes).containsExactlyInAnyOrder(
                OpenApiResultCode.REQUEST_ACCEPTED,
                OpenApiResultCode.REQUEST_PARTIALLY_ACCEPTED,
                OpenApiResultCode.PRODUCT_NOT_ENABLED,
                OpenApiResultCode.CURRENCY_NOT_ENABLED,
                OpenApiResultCode.PAYMENT_METHOD_NOT_ENABLED,
                OpenApiResultCode.AVAILABLE_BALANCE_INSUFFICIENT,
                OpenApiResultCode.QUOTE_EXPIRED,
                OpenApiResultCode.SINGLE_AMOUNT_LIMIT_EXCEEDED,
                OpenApiResultCode.CUMULATIVE_LIMIT_EXCEEDED);
        assertThat(OpenApiResultDefinitionRegistry.definitions().values())
                .filteredOn(definition -> definition.lifecycle() == OpenApiResultLifecycle.ACTIVE)
                .hasSize(39);
    }

    /** 渠道诊断参数只允许进入内部文案，同一码在商户和付款人出口必须使用各自安全模板。 */
    @Test
    void shouldKeepInternalMerchantAndPayerMessagesSeparated() {
        OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.CHANNEL_RETURNED_ERROR);

        assertThat(definition.allowedParameters())
                .contains("channelName:INTERNAL", "channelResponseCode:INTERNAL",
                        "channelResponseMessage:INTERNAL", "channelReference:INTERNAL");
        assertThat(definition.messages().get("internal.en-US"))
                .doesNotContainPattern("[\\p{IsHan}]");
        assertThat(definition.messages().get("internal.zh-CN"))
                .isNotEqualTo(definition.messages().get("merchant.zh-CN"));
        assertThat(definition.messages().get("merchant.zh-CN"))
                .isNotEqualTo(definition.messages().get("payer.zh-CN"));
    }

    /** F3000 必须登记未确认结果证据和查询策略，防止客户端据此自动重新发起资金动作。 */
    @Test
    void shouldGovernUnconfirmedChannelOutcomeConservatively() {
        OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED);

        assertThat(definition.origin()).isEqualTo(OpenApiResultOrigin.CHANNEL_TRANSPORT);
        assertThat(definition.evidence()).isEqualTo(OpenApiResultEvidence.UNCONFIRMED_CHANNEL_OUTCOME);
        assertThat(definition.handlingPolicy()).isEqualTo(OpenApiHandlingPolicy.QUERY);
        assertThat(definition.exclusions()).contains("不得");
    }

    /** C、具体 T 原因码和 F 类可位于外层或内层，交易基础状态只能位于业务对象。 */
    @Test
    void shouldRegisterResponsePositionFromBusinessResponseBoundary() {
        assertThat(OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE).applicableContracts())
                .contains("OPENAPI_V1_3_ENVELOPE", "OPENAPI_V1_3_TRANSACTION_INFO");
        assertThat(OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED).applicableContracts())
                .contains("OPENAPI_V1_3_ENVELOPE", "OPENAPI_V1_3_TRANSACTION_INFO");
        assertThat(OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.SYSTEM_ERROR).applicableContracts())
                .contains("OPENAPI_V1_3_ENVELOPE", "OPENAPI_V1_3_TRANSACTION_INFO");
        assertThat(OpenApiResultDefinitionRegistry.definition(
                OpenApiResultCode.TRANSACTION_SUCCESS).applicableContracts())
                .contains("OPENAPI_V1_3_TRANSACTION_INFO")
                .doesNotContain("OPENAPI_V1_3_ENVELOPE");
    }

    /** 所有使用 internalReason 的内部模板都必须显式登记参数权限。 */
    @Test
    void shouldDeclareEveryInternalReasonTemplateParameter() {
        assertThat(OpenApiResultCode.values())
                .filteredOn(code -> OpenApiResultDefinitionRegistry.definition(code)
                        .messages().get("internal.zh-CN").contains("{internalReason}"))
                .allSatisfy(code -> assertThat(OpenApiResultDefinitionRegistry.definition(code)
                        .allowedParameters()).contains("internalReason:INTERNAL"));
    }
}

package com.scott.payment.openapi.result;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiResultEvidence;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.security.MerchantIpWhitelistDenyReason;
import org.junit.jupiter.api.Test;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 旧异常码到 V1.3 的收敛映射测试。
 */
class OpenApiErrorMapperTest {

    /**
     * 已有受控旧码必须按 V1.3 的安全材料、身份和访问状态分别投影。
     */
    @Test
    void shouldMapMerchantAuthenticationFailuresByRegisteredMeaning() {
        assertThat(project("F401008").resultCode())
                .isEqualTo(OpenApiResultCode.MERCHANT_SIGNING_MATERIAL_MISSING);
        assertThat(project("F401009").resultCode())
                .isEqualTo(OpenApiResultCode.MERCHANT_INVALID);
        assertThat(project("F401010").resultCode())
                .isEqualTo(OpenApiResultCode.MERCHANT_ACCESS_BLOCKED);
    }

    /**
     * IP 白名单拒绝使用来源不允许语义，不能向响应 message 泄露真实客户端 IP。
     */
    @Test
    void shouldMapIpWhitelistDenialToSourceNotAllowed() {
        OpenApiErrorProjection projection = OpenApiErrorMapper.project(
                new MerchantIpWhitelistAccessDeniedException(
                        MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED, "203.0.113.10"));

        assertThat(projection.resultCode()).isEqualTo(OpenApiResultCode.SOURCE_NOT_ALLOWED);
    }

    /**
     * 内部微服务调用故障属于平台故障，不能仅凭旧 F502/F503 冒充渠道调用未确认。
     */
    @Test
    void shouldMapInternalServiceFailuresToSystemError() {
        ApiException timeout = new ApiException("F502", "internal");
        timeout.initCause(new SocketTimeoutException("read timed out"));

        assertThat(OpenApiErrorMapper.project(timeout).resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("F503").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
    }

    /**
     * 业务码投影不得携带 HTTP 决策，既有接口 HTTP 契约由 Web 边界独立维护。
     */
    @Test
    void shouldKeepHttpSemanticsOutsideBusinessCodeProjection() {
        assertThat(Arrays.stream(OpenApiErrorProjection.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("resultCode", "internalCode", "phase", "origin", "requestSent",
                        "outcomeUncertain", "fieldPath", "messageParameters", "evidence");
    }

    /**
     * 旧网关类码只有具备已确认渠道响应证据时才能归为 F1000。
     */
    @Test
    void shouldMapAmbiguousGatewayCodeFromConfirmedChannelEvidence() {
        for (String legacyCode : Set.of("F210", "F502", "F503", "Z606")) {
            OpenApiErrorProjection projection = projectWithContext(
                    legacyCode,
                    OpenApiResultOrigin.CHANNEL_RESPONSE,
                    true,
                    false,
                    OpenApiResultEvidence.CONFIRMED_CHANNEL_RESPONSE);

            assertThat(projection.resultCode())
                    .as("confirmed channel response for legacy code %s", legacyCode)
                    .isEqualTo(OpenApiResultCode.CHANNEL_RETURNED_ERROR);
            assertThat(projection.internalCode()).isEqualTo(legacyCode);
            assertThat(projection.origin()).isEqualTo(OpenApiResultOrigin.CHANNEL_RESPONSE);
            assertThat(projection.evidence()).isEqualTo(OpenApiResultEvidence.CONFIRMED_CHANNEL_RESPONSE);
        }
    }

    /**
     * 请求已发送且结果不确定时才能归为 F3000；仅有超时异常名不足以形成该结论。
     */
    @Test
    void shouldMapAmbiguousGatewayCodeFromUnconfirmedChannelEvidence() {
        for (String legacyCode : Set.of("F210", "F502", "F503", "Z606")) {
            OpenApiErrorProjection projection = projectWithContext(
                    legacyCode,
                    OpenApiResultOrigin.CHANNEL_TRANSPORT,
                    true,
                    true,
                    OpenApiResultEvidence.UNCONFIRMED_CHANNEL_OUTCOME);

            assertThat(projection.resultCode())
                    .as("unconfirmed channel outcome for legacy code %s", legacyCode)
                    .isEqualTo(OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED);
            assertThat(projection.requestSent()).isTrue();
            assertThat(projection.outcomeUncertain()).isTrue();
        }
    }

    /**
     * 证据字段缺失或相互矛盾时必须回到各旧码的安全兜底，不能推断渠道结果。
     */
    @Test
    void shouldFailClosedWhenAmbiguousGatewayEvidenceIsIncomplete() {
        Map<String, OpenApiResultCode> fallbacks = Map.of(
                "F210", OpenApiResultCode.TRANSACTION_FAILED,
                "F502", OpenApiResultCode.SYSTEM_ERROR,
                "F503", OpenApiResultCode.SYSTEM_ERROR,
                "Z606", OpenApiResultCode.SYSTEM_ERROR);

        fallbacks.forEach((legacyCode, fallback) -> {
            assertThat(projectWithContext(
                    legacyCode,
                    OpenApiResultOrigin.CHANNEL_RESPONSE,
                    true,
                    true,
                    OpenApiResultEvidence.CONFIRMED_CHANNEL_RESPONSE).resultCode())
                    .as("uncertain confirmed response for legacy code %s", legacyCode)
                    .isEqualTo(fallback);
            assertThat(projectWithContext(
                    legacyCode,
                    OpenApiResultOrigin.CHANNEL_TRANSPORT,
                    false,
                    true,
                    OpenApiResultEvidence.UNCONFIRMED_CHANNEL_OUTCOME).resultCode())
                    .as("request-not-sent transport failure for legacy code %s", legacyCode)
                    .isEqualTo(fallback);
        });
    }

    /**
     * 明确渠道拒绝、无来源的旧拒绝和内部异常案件必须保持不同边界。
     */
    @Test
    void shouldRespectLegacySourceEvidenceBoundaries() {
        assertThat(project("F207").resultCode()).isEqualTo(OpenApiResultCode.CHANNEL_RETURNED_ERROR);
        assertThat(project("F210").resultCode()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED);
        assertThat(project("F519").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("F520").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("Z606").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
    }

    /**
     * 支付核心细分原因必须通过稳定机器码投影，禁止根据内部异常 message 猜测公开语义。
     */
    @Test
    void shouldMapStructuredPaymentReasonCodesWithoutInspectingMessages() {
        assertThat(project("F409001").resultCode())
                .isEqualTo(OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE);
        assertThat(project("F500001").resultCode())
                .isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("F510001").resultCode())
                .isEqualTo(OpenApiResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(project("F510002").resultCode())
                .isEqualTo(OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED);
    }

    /**
     * 异常出口表示当前请求未能正常形成约定响应，不得将遗留或新规范的正常结果码透传为成功。
     */
    @Test
    void shouldFailClosedWhenExceptionCarriesNormalOutcomeCode() {
        assertThat(project("T200").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T201").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T202").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T203").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T206").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("A0000").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("A0001").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("A0002").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T0000").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T0001").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
        assertThat(project("T0002").resultCode()).isEqualTo(OpenApiResultCode.SYSTEM_ERROR);
    }

    /**
     * V1.3 迁移表中的内部枚举条目必须全部具备默认安全映射；
     * 需要上下文分流的旧码在无证据时使用保守结果，不能凭旧名称猜测渠道或终态。
     */
    @Test
    void shouldCoverEveryUniqueLegacyResultCode() {
        Map<String, OpenApiResultCode> expected = Map.ofEntries(
                Map.entry("T200", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("T201", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("T202", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("T203", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("T206", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F207", OpenApiResultCode.CHANNEL_RETURNED_ERROR),
                Map.entry("F210", OpenApiResultCode.TRANSACTION_FAILED),
                Map.entry("F400", OpenApiResultCode.BAD_REQUEST),
                Map.entry("F401", OpenApiResultCode.AUTHENTICATION_FAILED),
                Map.entry("F401001", OpenApiResultCode.AUTHORIZATION_MISSING),
                Map.entry("F401002", OpenApiResultCode.AUTHENTICATION_FAILED),
                Map.entry("F401003", OpenApiResultCode.AUTHORIZATION_TIME_INVALID),
                Map.entry("F401004", OpenApiResultCode.AUTHORIZATION_TIME_INVALID),
                Map.entry("F401005", OpenApiResultCode.AUTHENTICATION_FAILED),
                Map.entry("F401006", OpenApiResultCode.AUTHENTICATION_FAILED),
                Map.entry("F401007", OpenApiResultCode.AUTHENTICATION_FAILED),
                Map.entry("F401008", OpenApiResultCode.MERCHANT_SIGNING_MATERIAL_MISSING),
                Map.entry("F401009", OpenApiResultCode.MERCHANT_INVALID),
                Map.entry("F401010", OpenApiResultCode.MERCHANT_ACCESS_BLOCKED),
                Map.entry("F402001", OpenApiResultCode.PARAM_INVALID),
                Map.entry("F402002", OpenApiResultCode.PARAM_MISSING),
                Map.entry("F402003", OpenApiResultCode.ENCRYPTED_DATA_INVALID),
                Map.entry("F402004", OpenApiResultCode.CALLBACK_URL_NOT_ALLOWED),
                Map.entry("F403", OpenApiResultCode.FORBIDDEN),
                Map.entry("F404", OpenApiResultCode.API_NOT_FOUND),
                Map.entry("F405", OpenApiResultCode.METHOD_NOT_ALLOWED),
                Map.entry("F409", OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE),
                Map.entry("F409001", OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE),
                Map.entry("F410", OpenApiResultCode.CARD_NOT_SUPPORTED),
                Map.entry("F411", OpenApiResultCode.CURRENCY_NOT_SUPPORTED),
                Map.entry("F412", OpenApiResultCode.TRANSACTION_TYPE_NOT_SUPPORTED),
                Map.entry("F413", OpenApiResultCode.CARD_NOT_SUPPORTED),
                Map.entry("F414", OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED),
                Map.entry("F429", OpenApiResultCode.TOO_MANY_REQUESTS),
                Map.entry("F500", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F500001", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F500002", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F502", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F503", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F510", OpenApiResultCode.BUSINESS_IDENTIFIER_CONFLICT),
                Map.entry("F510001", OpenApiResultCode.IDEMPOTENCY_CONFLICT),
                Map.entry("F510002", OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED),
                Map.entry("F511", OpenApiResultCode.BUSINESS_RESOURCE_NOT_FOUND),
                Map.entry("F512", OpenApiResultCode.BUSINESS_RESOURCE_NOT_FOUND),
                Map.entry("F513", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F515", OpenApiResultCode.BUSINESS_IDENTIFIER_CONFLICT),
                Map.entry("F516", OpenApiResultCode.REFUND_AMOUNT_EXCEEDED),
                Map.entry("F517", OpenApiResultCode.REFUND_APPROVAL_CONFLICT),
                Map.entry("F518", OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED),
                Map.entry("F519", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("F520", OpenApiResultCode.SYSTEM_ERROR),
                Map.entry("Z605", OpenApiResultCode.BAD_REQUEST),
                Map.entry("Z606", OpenApiResultCode.SYSTEM_ERROR));
        Set<String> registeredLegacyCodes = new LinkedHashSet<>();
        Arrays.stream(ApiResultEnum.values()).map(ApiResultEnum::getCode)
                .forEach(registeredLegacyCodes::add);

        assertThat(expected.keySet()).containsExactlyInAnyOrderElementsOf(registeredLegacyCodes);
        expected.forEach((legacyCode, resultCode) ->
                assertThat(project(legacyCode).resultCode())
                        .as("legacy code %s", legacyCode)
                        .isEqualTo(resultCode));
    }

    /**
     * 投影不带结构化上下文的内部旧码，用于验证安全默认映射。
     */
    private OpenApiErrorProjection project(String legacyCode) {
        return OpenApiErrorMapper.project(new ApiException(legacyCode, "internal detail"));
    }

    /**
     * 投影带渠道确定性证据的内部旧码，用于验证 F1000 与 F3000 的严格分界。
     */
    private OpenApiErrorProjection projectWithContext(String legacyCode,
                                                      OpenApiResultOrigin origin,
                                                      Boolean requestSent,
                                                      Boolean outcomeUncertain,
                                                      OpenApiResultEvidence evidence) {
        return OpenApiErrorMapper.project(new OpenApiContextualException(
                legacyCode,
                "controlled internal detail",
                new OpenApiErrorContext(
                        OpenApiResultPhase.CHANNEL,
                        origin,
                        requestSent,
                        outcomeUncertain,
                        null,
                        Map.of("channelName", "controlled-channel"),
                        evidence)));
    }
}

package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsRequestMapperTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 请求映射测试，覆盖平台交易类型到 MPGS apiOperation、金额、币种、卡信息和目标交易号的映射规则。
 * @status : create
 */
@Slf4j
class MpgsRequestMapperTests {

    private final MpgsRequestMapper mapper = new MpgsRequestMapper();

    /**
     * 验证 PAY 一步支付映射：使用 MPGS 完成 3DS 时只引用同一 authentication transaction id。
     */
    @Test
    void shouldBuildPayRequestWithCardAndThreeDs() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.PAYMENT.getCode());
        ChannelPaymentRequest.ThreeDsInfo threeDsInfo = new ChannelPaymentRequest.ThreeDsInfo();
        threeDsInfo.setAuthenticationTransactionId("3DS-AUTH-001");
        threeDsInfo.setEci("05");
        threeDsInfo.setCavv("AAABBIIFmAAAAAAAAAAAAAAAAAA=");
        threeDsInfo.setDsTransactionId("ds-tx-001");
        threeDsInfo.setThreeDsVersion("3DS2");
        request.setThreeDsInfo(threeDsInfo);
        logCaseStart("PAY一步支付映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("PAY一步支付映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.PAY);
        assertThat(payload.getOrder().getAmount()).isEqualTo("10.25");
        assertThat(payload.getOrder().getCurrency()).isEqualTo("USD");
        assertThat(payload.getOrder().getReference()).isEqualTo("TX-001");
        assertThat(payload.getSourceOfFunds().getType()).isEqualTo(MpgsApiOperation.CARD);
        assertThat(payload.getSourceOfFunds().getProvided().getCard().getNumber()).isEqualTo("5123450000000008");
        assertThat(payload.getSourceOfFunds().getProvided().getCard().getExpiry().getYear()).isEqualTo("39");
        assertThat(payload.getAuthentication().getTransactionId()).isEqualTo("3DS-AUTH-001");
        assertThat(payload.getAuthentication().getThreeDs()).isNull();
        assertThat(payload.getAuthentication().getThreeDs2()).isNull();
        log.info("MPGS请求映射测试完成，case=PAY一步支付映射，result: {}",
                JsonUtils.toJsonString(new MappingResult(payload.getApiOperation(),
                        MpgsApiClient.maskMpgsJson("{\"number\":\"" + request.getCardNo() + "\"}"))));
    }

    /**
     * 验证声明为 3DS 的 MPGS 支付必须引用同一网关认证交易，缺失时不得发起 PAY。
     */
    @Test
    void shouldRejectThreeDsPayWithoutMpgsAuthenticationTransactionId() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.PAYMENT.getCode());
        ChannelPaymentRequest.ThreeDsInfo threeDsInfo = new ChannelPaymentRequest.ThreeDsInfo();
        threeDsInfo.setEci("05");
        threeDsInfo.setCavv("AAABBIIFmAAAAAAAAAAAAAAAAAA=");
        request.setThreeDsInfo(threeDsInfo);

        assertThatThrownBy(() -> mapper.toMpgsRequest(request))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("authenticationTransactionId is required");
    }

    @Test
    void shouldBuildInitiateAuthenticationWithPayerBrowserContext() {
        MpgsThreeDsAuthenticationRequest request = threeDsRequest();

        String json = JsonUtils.toJsonString(
                mapper.toMpgsThreeDsRequest(request, MpgsApiOperation.INITIATE_AUTHENTICATION));

        assertThat(json)
                .contains("\"acceptVersions\":\"3DS2\"")
                .contains("\"channel\":\"PAYER_BROWSER\"")
                .contains("\"purpose\":\"PAYMENT_TRANSACTION\"")
                .contains("\"currency\":\"USD\"")
                .contains("\"notificationUrl\":\"https://gateway.example.com/channel/v1/callbacks/MPGS/3ds\"")
                .contains("\"reference\":\"TX-3DS-001\"")
                .contains("\"number\":\"5123450000000008\"")
                .doesNotContain("\"amount\"")
                .doesNotContain("\"expiry\"")
                .doesNotContain("\"securityCode\"")
                .doesNotContain("\"device\":")
                .doesNotContain("browserPayment");
    }

    @Test
    void shouldAllowInitiateAuthenticationWithoutOptionalWebhookUrl() {
        MpgsThreeDsAuthenticationRequest request = threeDsRequest();
        request.setNotificationUrl(null);

        String json = JsonUtils.toJsonString(
                mapper.toMpgsThreeDsRequest(request, MpgsApiOperation.INITIATE_AUTHENTICATION));

        assertThat(json)
                .contains("\"apiOperation\":\"INITIATE_AUTHENTICATION\"")
                .doesNotContain("notificationUrl");
    }

    @Test
    void shouldPutAuthenticatePayerReturnUrlUnderAuthentication() {
        MpgsThreeDsAuthenticationRequest request = threeDsRequest();

        String json = JsonUtils.toJsonString(
                mapper.toMpgsThreeDsRequest(request, MpgsApiOperation.AUTHENTICATE_PAYER));

        assertThat(json)
                .contains("\"redirectResponseUrl\":\"https://pay.example.com/checkout/api/v1/3ds/bridge\"")
                .contains("\"amount\":\"31\"")
                .contains("\"currency\":\"USD\"")
                .contains("\"number\":\"5123450000000008\"")
                .contains("\"expiry\":{\"month\":\"01\",\"year\":\"39\"}")
                .contains("\"securityCode\":\"100\"")
                .contains("\"nameOnCard\":\"Test Buyer\"")
                .contains("\"browser\":\"Mozilla/5.0\"")
                .contains("\"ipAddress\":\"203.0.113.9\"")
                .contains("\"3DSecureChallengeWindowSize\":\"FULL_SCREEN\"")
                .contains("\"acceptHeaders\":\"text/html,application/xhtml+xml\"")
                .contains("\"javaScriptEnabled\":true")
                .contains("\"billing\":{\"address\":")
                .contains("\"city\":\"Singapore\"")
                .contains("\"country\":\"SGP\"")
                .contains("\"postcodeZip\":\"018956\"")
                .contains("\"stateProvince\":\"Singapore\"")
                .contains("\"street\":\"1 Marina Boulevard\"")
                .contains("\"customer\":{")
                .contains("\"email\":\"buyer@example.com\"")
                .contains("\"firstName\":\"Test\"")
                .contains("\"lastName\":\"Buyer\"")
                .contains("\"mobilePhone\":\"+6591234567\"")
                .doesNotContain("notificationUrl")
                .doesNotContain("\"type\":\"CARD\"")
                .doesNotContain("\"reference\"")
                .doesNotContain("browserPayment")
                .doesNotContain("\"payerInteraction\":\"REQUIRED\"");
    }

    @Test
    void shouldOmitBlankOptionalCustomerPhoneForAuthenticatePayer() {
        MpgsThreeDsAuthenticationRequest request = threeDsRequest();
        request.getBillingInfo().setPhone(" ");

        String json = JsonUtils.toJsonString(
                mapper.toMpgsThreeDsRequest(request, MpgsApiOperation.AUTHENTICATE_PAYER));

        assertThat(json)
                .contains("\"customer\":{")
                .contains("\"email\":\"buyer@example.com\"")
                .doesNotContain("mobilePhone");
    }

    /**
     * 验证 AUTHORIZATION 授权映射：授权类交易必须带卡资料、金额和币种，MPGS apiOperation 为 AUTHORIZE。
     */
    @Test
    void shouldBuildAuthorizeRequestWithCard() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.AUTHORIZATION.getCode());
        logCaseStart("AUTHORIZE授权映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("AUTHORIZE授权映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.AUTHORIZE);
        assertThat(payload.getOrder().getAmount()).isEqualTo("10.25");
        assertThat(payload.getSourceOfFunds().getProvided().getCard().getSecurityCode()).isEqualTo("100");
    }

    /**
     * 验证 PRE_AUTHORIZATION 预授权映射：平台预授权在 MPGS 侧仍提交 AUTHORIZE，状态差异由平台交易状态机表达。
     */
    @Test
    void shouldBuildPreAuthorizeRequestAsAuthorize() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.PRE_AUTHORIZATION.getCode());
        logCaseStart("PRE_AUTHORIZATION预授权映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("PRE_AUTHORIZATION预授权映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.AUTHORIZE);
        assertThat(payload.getOrder().getCurrency()).isEqualTo("USD");
    }

    /**
     * 验证 CAPTURE 请款映射：后续交易只提交 transaction.amount/currency，不能重复提交卡信息。
     */
    @Test
    void shouldBuildCaptureRequestWithoutCard() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.CAPTURE.getCode());
        logCaseStart("CAPTURE请款映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("CAPTURE请款映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.CAPTURE);
        assertThat(payload.getOrder()).isNull();
        assertThat(payload.getSourceOfFunds()).isNull();
        assertThat(payload.getTransaction().getAmount()).isEqualTo("10.25");
        assertThat(payload.getTransaction().getCurrency()).isEqualTo("USD");
    }

    /**
     * 验证 PRE_AUTH_COMPLETION 预授权完成映射：平台预授权完成在 MPGS 侧按 CAPTURE 提交。
     */
    @Test
    void shouldBuildPreAuthCompletionRequestAsCapture() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.PRE_AUTH_COMPLETION.getCode());
        logCaseStart("PRE_AUTH_COMPLETION预授权完成映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("PRE_AUTH_COMPLETION预授权完成映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.CAPTURE);
        assertThat(payload.getTransaction().getAmount()).isEqualTo("10.25");
    }

    /**
     * 验证 REFUND 退款映射：退款只提交退款金额和币种，不能携带完整卡资料。
     */
    @Test
    void shouldBuildRefundRequestWithoutCard() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.REFUND.getCode());
        logCaseStart("REFUND退款映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("REFUND退款映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.REFUND);
        assertThat(payload.getSourceOfFunds()).isNull();
        assertThat(payload.getTransaction().getAmount()).isEqualTo("10.25");
        assertThat(payload.getTransaction().getCurrency()).isEqualTo("USD");
    }

    /**
     * 验证 INCREMENTAL_AUTHORIZATION 增量授权映射：平台增量授权对应 MPGS UPDATE_AUTHORIZATION。
     */
    @Test
    void shouldBuildIncrementalAuthorizeRequestAsUpdateAuthorization() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode());
        logCaseStart("INCREMENTAL_AUTHORIZATION增量授权映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("INCREMENTAL_AUTHORIZATION增量授权映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.UPDATE_AUTHORIZATION);
        assertThat(payload.getOrder().getReference()).isEqualTo("TX-001");
        assertThat(payload.getTransaction().getAmount()).isEqualTo("10.25");
    }

    /**
     * 验证 VOID 撤销映射：必须写入 transaction.targetTransactionId，关联被撤销的原始交易。
     */
    @Test
    void shouldBuildVoidRequestWithTargetTransactionId() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.VOID.getCode());
        request.getExtension().put("targetTransactionId", "CH-AUTH-001");
        logCaseStart("VOID撤销映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("VOID撤销映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.VOID);
        assertThat(payload.getTransaction().getTargetTransactionId()).isEqualTo("CH-AUTH-001");
    }

    /**
     * 验证 REVERSAL 冲正映射：当前 MPGS 适配按 VOID 提交，目标交易号可从扩展字段读取。
     */
    @Test
    void shouldBuildReversalRequestAsVoidWithTargetTransactionId() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.REVERSAL.getCode());
        request.getExtension().put("targetTransactionId", "TX-PAY-001");
        logCaseStart("REVERSAL冲正映射", request);

        MpgsRequestPayload payload = mapper.toMpgsRequest(request);
        logPayload("REVERSAL冲正映射", payload);

        assertThat(payload.getApiOperation()).isEqualTo(MpgsApiOperation.VOID);
        assertThat(payload.getTransaction().getTargetTransactionId()).isEqualTo("TX-PAY-001");
    }

    /**
     * 验证卡交易缺少卡号时立即拒绝请求，避免无效请求进入真实渠道。
     */
    @Test
    void shouldRejectPaymentWithoutCardNo() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.PAYMENT.getCode());
        request.setCardNo(null);
        logCaseStart("PAY缺少卡号异常映射", request);

        assertThatThrownBy(() -> mapper.toMpgsRequest(request))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("card number is required");
    }

    /**
     * 验证 VOID 缺少目标交易号时立即拒绝请求，避免无法幂等关联原交易的撤销动作进入渠道。
     */
    @Test
    void shouldRejectVoidWithoutTargetTransactionId() {
        ChannelPaymentRequest request = baseRequest(ChannelCapability.VOID.getCode());
        request.setSourceTransactionId(null);
        logCaseStart("VOID缺少目标交易号异常映射", request);

        assertThatThrownBy(() -> mapper.toMpgsRequest(request))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("target transactionId is required");
    }

    /**
     * 验证不支持的交易类型不会被静默映射，防止状态机外的交易动作误打到 MPGS。
     */
    @Test
    void shouldRejectUnsupportedTransactionType() {
        ChannelPaymentRequest request = baseRequest("CHARGEBACK");
        logCaseStart("不支持交易类型异常映射", request);

        assertThatThrownBy(() -> mapper.toMpgsRequest(request))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("unsupported transaction type");
    }

    /**
     * 打印请求映射测试开始日志，使用统一 JSON 工具输出可提取的结构化摘要。
     *
     * @param caseName 测试场景名称
     * @param request  渠道请求
     */
    private void logCaseStart(String caseName, ChannelPaymentRequest request) {
        log.info("MPGS请求映射测试开始，case: {}, request: {}", caseName, JsonUtils.toJsonString(new MappingRequest(
                request.getTransactionType(), request.getOperationId(), request.getTransactionId(),
                request.getChannelOrderNo(), request.getChannelTransactionId(), request.getMerchantOrderNo(),
                request.getMerchantOrderId(), String.valueOf(request.getAmount()), request.getCurrency(),
                MpgsApiClient.maskMpgsJson("{\"number\":\"" + request.getCardNo() + "\"}")
        )));
    }

    /**
     * 打印 MPGS 请求体映射结果，进入日志前必须脱敏卡号、CVV 和 3DS token。
     *
     * @param caseName 测试场景名称
     * @param payload  MPGS 请求体
     */
    private void logPayload(String caseName, MpgsRequestPayload payload) {
        String requestBody = JsonUtils.toJsonString(payload);
        log.info("MPGS请求映射测试请求体，case: {}, request: {}", caseName, MpgsApiClient.maskMpgsJson(requestBody));
    }

    /**
     * 构造请求映射测试的基础渠道请求。
     *
     * @param transactionType 平台交易类型
     * @return 已填充金额、币种和测试卡信息的渠道请求
     */
    private ChannelPaymentRequest baseRequest(String transactionType) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setSourceTransactionId("CH-SRC-001");
        request.setChannelOrderNo("TX-AUTH-001");
        request.setChannelTransactionId("CH-001");
        request.setMerchantId("M001");
        request.setMerchantOrderNo("MER-ORDER-001");
        request.setMerchantOrderId("MER-REQ-001");
        request.setTransactionType(transactionType);
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("usd");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("2039");
        request.setSecurityCode("100");
        return request;
    }

    private MpgsThreeDsAuthenticationRequest threeDsRequest() {
        MpgsThreeDsAuthenticationRequest request = new MpgsThreeDsAuthenticationRequest();
        request.setChannelOrderNo("ORDER-3DS-001");
        request.setAuthenticationTransactionId("AUTH-3DS-001");
        request.setTransactionId("TX-3DS-001");
        request.setAmount(new BigDecimal("31.00"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("01");
        request.setExpirationYear("2039");
        request.setSecurityCode("100");
        request.setCardholderName("Test Buyer");
        request.setPayerIp("203.0.113.9");
        request.setRedirectResponseUrl("https://pay.example.com/checkout/api/v1/3ds/bridge");
        request.setNotificationUrl("https://gateway.example.com/channel/v1/callbacks/MPGS/3ds");
        ChannelPaymentRequest.BillingInfo billingInfo = new ChannelPaymentRequest.BillingInfo();
        billingInfo.setFirstName("Test");
        billingInfo.setLastName("Buyer");
        billingInfo.setEmail("buyer@example.com");
        billingInfo.setPhone("+6591234567");
        billingInfo.setCountry("SGP");
        billingInfo.setState("Singapore");
        billingInfo.setCity("Singapore");
        billingInfo.setStreet("1 Marina Boulevard");
        billingInfo.setPostal("018956");
        request.setBillingInfo(billingInfo);
        request.setBrowserInfoJson("""
                {"userAgent":"Mozilla/5.0","acceptHeaders":"text/html,application/xhtml+xml",
                "challengeWindowSize":"FULL_SCREEN","colorDepth":24,"javaEnabled":false,
                "javaScriptEnabled":true,"language":"en-US","screenHeight":1080,
                "screenWidth":1920,"timezoneOffset":-480}
                """);
        return request;
    }

    private record MappingRequest(String transactionType,
                                  String operationId,
                                  String transactionId,
                                  String channelOrderNo,
                                  String channelTransactionId,
                                  String merchantOrderNo,
                                  String merchantOrderId,
                                  String amount,
                                  String currency,
                                  String card) {
    }

    private record MappingResult(String apiOperation, String maskedCard) {
    }
}

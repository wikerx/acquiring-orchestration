package com.scott.payment.channel.payment.scottchannela.client;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.scottchannela.config.ChannelProperties;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScottChannelA 请求映射、响应语义、未知结果和敏感信息脱敏测试。
 **/
class PaymentApiClientTests {

    private HttpServer server;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> responseBody;
    private AtomicReference<Integer> responseStatus;

    @BeforeEach
    void setUp() throws IOException {
        requestPath = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        responseStatus = new AtomicReference<>(200);
        responseBody = new AtomicReference<>("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"ORDER-001\",\"transactionId\":\"TX-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldUseConfiguredVersionAndDirectPayinPath() {
        ChannelProperties properties = properties();
        PaymentApiClient client = paymentClient(properties);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setTransactionId("202611211816573540075");
        request.setChannelOrderNo("CMT-OTHER-REFERENCE");
        request.setMerchantOrderNo("ORDER-001");
        responseBody.set("""
                {"code":"SUCCESS","message":"OK","data":{"merchantOrderNo":"202611211816573540075",
                 "transactionId":"CMT-001","status":"SUCCESS","responseCode":"SUCCESS","currency":"USD","amount":10.25}}
                """);
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(requestPath).hasValue("/api/v7/payins/direct");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantOrderNo", "202611211816573540075");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMT-001");
        assertThat(payload).containsEntry("currency", "USD");
        assertThat(payload).containsEntry("paymentMethod", "CARD");
        assertThat(payload).containsKey("requestTime");
    }

    /**
     * 默认客户端使用生产级出站策略，必须在发送前拒绝 HTTP 和回环地址。
     **/
    @Test
    void shouldRejectLocalHttpTargetWithDefaultOutboundPolicy() {
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardlessPaymentRequest("CMT-STRICT-URL-001");

        assertThatThrownBy(() -> client.execute(request))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("outbound URL policy");
    }

    @Test
    void shouldUseChannelRequestUrlInsteadOfMidApiBaseUrl() {
        ChannelProperties properties = properties();
        PaymentApiClient client = paymentClient(properties);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setMerchantOrderNo("ORDER-CHANNEL-URL");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        request.getExtension().put("requestUrl", properties.getBaseUrl());
        request.getExtension().put("mid.apiBaseUrl", "http://127.0.0.1:1");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/direct");
    }

    @Test
    void shouldSendOnlyMerchantOrderNoForPayinQueryWhenTransactionIdIsAbsent() {
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setMerchantOrderNo("ORDER-QUERY-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/query");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantOrderNo", "ORDER-QUERY-001");
        assertThat(payload).doesNotContainKey("transactionId");
    }

    @Test
    void retainsPreviouslyFrozenReferenceForLegacyPayin() {
        ChannelPaymentRequest request = cardlessPaymentRequest("202611211816573540075");
        request.setChannelOrderNo("CR202611211816573540077");

        paymentClient(properties()).execute(request);

        assertThat(parsePayload()).containsEntry("merchantOrderNo", "CR202611211816573540077");
    }

    @Test
    void queriesUnconfirmedPayinUsingFrozenRequestReference() {
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setChannelOrderNo("CR202611211816573540077");
        request.setChannelTransactionId("CH202611211816573540078");

        client.execute(request);

        assertThat(parsePayload()).containsOnlyKeys("merchantOrderNo")
                .containsEntry("merchantOrderNo", "CR202611211816573540077");
    }

    @Test
    void shouldUseOriginalPaymentTransactionIdForRefund() {
        responseStatus.set(201);
        responseBody.set(refundResponse());
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("CMT-REFUND-ACTION");
        request.setSourceTransactionId("CMT-ORIGINAL-PAYMENT");
        request.setChannelOrderNo("CMT-ORIGINAL-PAYMENT");
        request.setChannelTransactionId("CH-REFUND-ACTION");
        request.setMerchantOrderNo("SDKPAY-001");
        request.setMerchantOrderId("REFUND-REQUEST-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.getExtension().put("merchantRefundNo", "REFUND-001");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");
        request.getExtension().put("simulationResult", "SUCCESS");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("transactionId", "CMT-ORIGINAL-PAYMENT");
        assertThat(payload).doesNotContainKey("merchantOrderNo");
        assertThat(payload).doesNotContainEntry("transactionId", "CH-REFUND-ACTION");
        assertThat(payload).doesNotContainEntry("transactionId", "CMT-REFUND-ACTION");
        assertThat(payload)
                .containsEntry("merchantRefundNo", "REFUND-001")
                .containsEntry("simulationResult", "SUCCESS");
    }

    /** 连接失败时仍保留真正的退款报文和来源，且不把本地异常当作渠道拒绝。 */
    @Test
    void shouldRetainMaskedRefundRequestAfterConnectionFailure() {
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = refundRequest();
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> client.execute(request))
                .isInstanceOfSatisfying(ChannelRequestException.class,
                        failure -> assertThat(failure.isOutcomeUncertain()).isTrue());
        assertThat(request.getExtension()).containsEntry("httpMethod", "POST");
        assertThat(request.getExtension().get("requestUrlMasked")).endsWith("/api/v7/payins/refunds");
        assertThat(request.getExtension().get("requestHeaderJsonMasked")).contains("\"X-Api-Key\":\"***\"");
        assertThat(request.getExtension().get("requestBodyJsonMasked"))
                .contains("\"transactionId\":\"CMT-ORIGINAL-PAYMENT\"");
    }

    /** HTTP 成功仅表示收到业务响应，资金不足仍是明确失败。 */
    @Test
    void shouldReturnInsufficientFundsAsDefinitiveRefundFailure() {
        responseStatus.set(201);
        responseBody.set("""
                {"code":"SUCCESS","message":"OK","data":{"status":"FAIL","responseCode":"INSUFFICIENT_FUNDS",
                "message":"Insufficient funds","transactionId":"CMT-ORIGINAL-PAYMENT"}}
                """);

        ChannelPaymentResponse response = paymentClient(properties()).execute(refundRequest());

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.getHttpStatus()).isEqualTo(201);
    }

    /**
     * 数据库存储的六位小数金额必须按渠道两位小数协议发送。
     **/
    @Test
    void shouldNormalizeRefundAmountToTwoDecimalPlaces() {
        responseStatus.set(201);
        responseBody.set(refundResponse());
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-SCALE-001");
        request.setMerchantOrderId("REFUND-SCALE-001");
        request.setAmount(new java.math.BigDecimal("8.850000"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        client.execute(request);

        assertThat(requestBody.get()).contains("\"amount\":8.85");
        assertThat(requestBody.get()).doesNotContain("\"amount\":8.850000");
    }

    /**
     * 渠道明确返回 4xx 时请求已被拒绝，不能继续作为结果未知订单处理。
     **/
    @Test
    void shouldMapClientErrorToDeterministicFailure() {
        responseStatus.set(400);
        responseBody.set("{\"code\":\"INVALID_REQUEST\",\"message\":\"Request validation failed\",\"errors\":[\"amount: scale must be less than or equal to 2\"]}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-INVALID-001");
        request.setMerchantOrderId("REFUND-INVALID-001");
        request.setAmount(new java.math.BigDecimal("8.85"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getRawChannelStatus()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Request validation failed");
        assertThat(response.getHttpStatus()).isEqualTo(400);
        assertThat(response.getResponseBodyJsonMasked()).contains("INVALID_REQUEST");
    }

    /**
     * 5xx 无法证明资金动作是否被渠道受理，仍需保持结果未知并后续勾兑。
     **/
    @Test
    void shouldKeepServerErrorOutcomeUncertain() {
        responseStatus.set(500);
        responseBody.set("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-SERVER-ERROR-001");
        request.setMerchantOrderId("REFUND-SERVER-ERROR-001");
        request.setAmount(new java.math.BigDecimal("8.85"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        assertThatThrownBy(() -> client.execute(request))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 明确失败不要求渠道生成仅在受理成功后才有的退款流水号。 */
    @Test
    void shouldAcceptDefinitiveRefundFailureWithoutSuccessIdentifiers() {
        responseBody.set("{\"code\":\"FAILED\",\"message\":\"Declined\",\"data\":{\"status\":\"FAILED\",\"responseCode\":\"REFUND_DECLINED\"}}");
        ChannelPaymentRequest request = refundRequest();

        ChannelPaymentResponse response = paymentClient(properties()).execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("REFUND_DECLINED");
        assertThat(response.getResponseBodyJsonMasked()).contains("REFUND_DECLINED");
    }

    /** 明确未受理的退款拒绝应立即失败，不得等待补查不存在后才释放预占。 */
    @Test
    void shouldRejectNonRefundableRefundConflictImmediately() {
        responseStatus.set(409);
        responseBody.set("{\"code\":\"ORDER_NOT_REFUNDABLE\",\"message\":\"Card authorization operations cannot be refunded before capture settlement support\"}");

        ChannelPaymentResponse response = paymentClient(properties()).execute(refundRequest());

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("ORDER_NOT_REFUNDABLE");
        assertThat(response.getChannelResponseMessage()).contains("cannot be refunded");
        assertThat(response.getHttpStatus()).isEqualTo(409);
        assertThat(response.getResponseBodyJsonMasked()).contains("ORDER_NOT_REFUNDABLE");
    }

    /** 幂等冲突或未知冲突可能对应已经受理的资金请求，不能一律按 409 失败。 */
    @Test
    void shouldKeepDuplicateAndUnknownRefundConflictsUncertain() {
        responseStatus.set(409);
        for (String code : new String[]{"DUPLICATE_REQUEST", "DUPLICATE_REFUND_CONFLICT", "UNRECOGNIZED_CONFLICT"}) {
            responseBody.set("{\"code\":\"" + code + "\"}");
            assertThatThrownBy(() -> paymentClient(properties()).execute(refundRequest()))
                    .as("退款冲突码 %s 必须保留结果未知", code)
                    .isInstanceOfSatisfying(ChannelResponseException.class,
                            exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
        }
    }

    /** 明确拒绝分类仅覆盖退款提交，不改变其他交易类型的冲突语义。 */
    @Test
    void shouldPreserveNonRefundConflictEvenWithRefundRejectionCode() {
        responseStatus.set(409);
        responseBody.set("{\"code\":\"ORDER_NOT_REFUNDABLE\"}");
        assertThatThrownBy(() -> paymentClient(properties()).execute(cardlessPaymentRequest("PAYIN-CONFLICT")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 服务端错误不能仅凭正文中的业务码推断退款未发生。 */
    @Test
    void shouldNotTrustRefundRejectionCodeOnServerError() {
        responseStatus.set(500);
        responseBody.set("{\"code\":\"ORDER_NOT_REFUNDABLE\"}");
        assertThatThrownBy(() -> paymentClient(properties()).execute(refundRequest()))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 无法识别的 409 正文仍需查询确认，不能伪造拒绝事实。 */
    @Test
    void shouldKeepMalformedRefundConflictUncertain() {
        responseStatus.set(409);
        for (String body : new String[]{"", "invalid-json", "{\"message\":\"Conflict\"}"}) {
            responseBody.set(body);
            assertThatThrownBy(() -> paymentClient(properties()).execute(refundRequest()))
                    .isInstanceOfSatisfying(ChannelResponseException.class,
                            exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
        }
    }

    /** 冲突响应若同时声明资金成功，必须保留矛盾证据并等待核实。 */
    @Test
    void shouldKeepContradictoryRefundConflictUncertain() {
        responseStatus.set(409);
        responseBody.set("{\"code\":\"ORDER_NOT_REFUNDABLE\",\"data\":{\"status\":\"SUCCESS\"}}");
        assertThatThrownBy(() -> paymentClient(properties()).execute(refundRequest()))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 查询拒绝不能证明此前的退款提交已经失败。 */
    @Test
    void shouldKeepRefundQueryConflictUncertain() {
        responseStatus.set(409);
        responseBody.set("{\"code\":\"ORDER_NOT_REFUNDABLE\"}");
        ChannelPaymentRequest request = refundRequest();
        request.setTransactionType("QUERY");
        request.getExtension().put("merchantRefundNo", "REFUND-QUERY-CONFLICT");
        assertThatThrownBy(() -> paymentClient(properties()).execute(request))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 本次退款纠错不扩大到其他公开交易类型的协议校验。 */
    @Test
    void shouldPreservePayinValidationOutsideRefundFix() {
        responseBody.set("{\"code\":\"FAILED\",\"data\":{\"status\":\"FAILED\"}}");
        assertThatThrownBy(() -> paymentClient(properties()).execute(cardlessPaymentRequest("PAYIN-COMPATIBILITY")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** 畸形成功仍为未知，但异常路径必须留下实际 HTTP 和脱敏响应证据。 */
    @Test
    void shouldRetainSafeEvidenceWhenRefundResponseCannotBeInterpreted() {
        responseBody.set("{\"code\":\"SUCCESS\",\"data\":{\"status\":\"SUCCESS\",\"cardNumber\":\"4111111111111111\",\"cvv\":\"123\"}}");

        assertThatThrownBy(() -> paymentClient(properties()).execute(refundRequest()))
                .isInstanceOfSatisfying(ChannelResponseException.class, exception -> {
                    assertThat(exception.isOutcomeUncertain()).isTrue();
                    assertThat(exception.getResponseEvidence()).isNotNull();
                    assertThat(exception.getResponseEvidence().getHttpStatus()).isEqualTo(200);
                    assertThat(exception.getResponseEvidence().getRequestUrlMasked()).endsWith("/payins/refunds");
                    assertThat(exception.getResponseEvidence().getResponseBodyJsonMasked())
                            .doesNotContain("4111111111111111", "\"123\"");
                });
    }

    private ChannelPaymentRequest refundRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-REGRESSION");
        request.setMerchantOrderId("REFUND-REGRESSION");
        request.setAmount(new java.math.BigDecimal("0.78"));
        request.setCurrency("USD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");
        return request;
    }

    /**
     * 幂等冲突不能证明首个资金请求失败，必须等待查询或回调确认。
     **/
    @Test
    void shouldKeepConflictOutcomeUncertain() {
        responseStatus.set(409);
        responseBody.set("{\"code\":\"DUPLICATE_REQUEST\",\"message\":\"Request already exists\"}");
        PaymentApiClient client = paymentClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-CONFLICT")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /**
     * 退款查询必须调用退款查询接口，并使用 merchantRefundNo 作为唯一查询标识。
     **/
    @Test
    void shouldQueryRefundByMerchantRefundNoAndMapRefundId() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-ORIGINAL-PAYMENT\",\"transactionId\":\"CMT-ORIGINAL-PAYMENT\",\"merchantRefundNo\":\"REFUND-001\",\"refundId\":\"CMR-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setChannelOrderNo("CMT-ORIGINAL-PAYMENT");
        request.setChannelTransactionId("CH-REFUND-ACTION");
        request.setMerchantOrderNo("SDKPAY-001");
        request.getExtension().put("merchantRefundNo", "REFUND-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds/query");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(payload).doesNotContainKey("refundId");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMR-001");
        assertThat(response.getRawResponse()).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(response.getRawResponse()).containsEntry("refundId", "CMR-001");
    }

    @Test
    void shouldQueryRefundByItsOwnProviderIdAndKeepTheOriginalPaymentIdAsEvidence() {
        responseBody.set("""
                {"code":"SUCCESS","data":{"merchantOrderNo":"PLATFORM-SOURCE","merchantRefundNo":"PLATFORM-REFUND",
                 "transactionId":"CMT-SOURCE","refundId":"CMR-REFUND","status":"SUCCESS","responseCode":"SUCCESS"}}
                """);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.getExtension().put("refundId", "CMR-REFUND");

        var response = paymentClient(properties()).execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds/query");
        assertThat(parsePayload()).containsOnlyKeys("refundId").containsEntry("refundId", "CMR-REFUND");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMR-REFUND");
        assertThat(response.getRawResponse()).containsEntry("transactionId", "CMT-SOURCE");
    }

    @Test
    void shouldMapPlatformProcessingCodesToProcessing() {
        responseBody.set("{\"code\":\"T202\",\"message\":\"Processing\",\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"responseCode\":\"T202\",\"currency\":\"USD\",\"amount\":10.25}}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setTransactionId("CMT-001");
        request.setMerchantOrderNo("SDKPAY-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("PROCESSING");
        assertThat(response.getChannelResponseCode()).isEqualTo("T202");
    }

    @Test
    void shouldMapMissingOrderQueryToDeterministicFailure() {
        responseStatus.set(404);
        responseBody.set("{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"Order not found\"}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setMerchantOrderNo("ORDER-MISSING");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getRawChannelStatus()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void shouldCreateAuthorizationWithPlatformTransactionId() {
        responseStatus.set(201);
        responseBody.set(cardResponse("AUTH", null, "000101"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.AUTHORIZATION, "CMT-AUTH-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations");
        assertThat(parsePayload())
                .containsEntry("merchantOrderNo", "CMT-AUTH-001")
                .containsEntry("amount", new java.math.BigDecimal("10.25"))
                .containsEntry("currency", "USD")
                .doesNotContainKey("authorizationId");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getAuthCode()).isEqualTo("000101");
        assertThat(response.getAcquirerReferenceNo()).isNull();
    }

    @Test
    void shouldUsePreAuthorizationPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("PREAUTH", null, "000102"));
        PaymentApiClient client = paymentClient(properties());

        client.execute(cardRequest(ChannelCapability.PRE_AUTHORIZATION, "CMT-PREAUTH-001"));

        assertThat(requestPath).hasValue("/api/v7/card/preauthorizations");
    }

    @Test
    void shouldCaptureByAuthorizationIdAndMapArn() {
        responseStatus.set(201);
        responseBody.set(cardResponse("CAPTURE", "20261031000000000000101", "000103"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.CAPTURE, "CMT-CAPTURE-001");
        request.setChannelOrderNo("CMA-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/captures");
        assertThat(parsePayload())
                .containsEntry("authorizationId", "CMA-001")
                .containsEntry("merchantOrderNo", "CMT-CAPTURE-001")
                .doesNotContainKey("currency");
        assertThat(response.getAcquirerReferenceNo()).isEqualTo("20261031000000000000101");
        assertThat(response.getAuthCode()).isEqualTo("000103");
    }

    @Test
    void shouldUsePreAuthorizationCompletionPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("PREAUTH_COMPLETE", "20261031000000000000102", "000104"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.PRE_AUTH_COMPLETION, "CMT-COMPLETE-001");
        request.setChannelOrderNo("CMA-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/preauthorizations/completions");
    }

    @Test
    void shouldUseIncrementalAuthorizationPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("INCREMENT", null, "000105"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.INCREMENTAL_AUTHORIZATION, "CMT-INCREMENT-001");
        request.setChannelOrderNo("CMA-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/increments");
    }

    @Test
    void shouldVoidWithoutAmount() {
        responseStatus.set(201);
        responseBody.set(cardResponse("VOID", null, null));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.VOID, "CMT-VOID-001");
        request.setChannelOrderNo("CMA-001");
        request.setAmount(null);
        request.setCurrency(null);

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/voids");
        assertThat(parsePayload())
                .containsEntry("authorizationId", "CMA-001")
                .containsEntry("merchantOrderNo", "CMT-VOID-001")
                .doesNotContainKeys("amount", "currency");
        assertThat(response.getAuthCode()).isNull();
        assertThat(response.getAcquirerReferenceNo()).isNull();
    }

    /**
     * VOID 请求收到渠道 CANCELLED 时表示撤销完成，不得映射成渠道失败。
     **/
    @Test
    void shouldMapCancelledVoidAsVoided() {
        responseStatus.set(201);
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-VOID-002\",\"transactionId\":\"TX-VOID-002\",\"authorizationId\":\"CMA-001\",\"operationType\":\"VOID\",\"status\":\"CANCELLED\",\"responseCode\":\"SUCCESS\"}}");
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.VOID, "CMT-VOID-002");
        request.setChannelOrderNo("CMA-001");
        request.setAmount(null);
        request.setCurrency(null);

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("VOIDED");
    }

    @Test
    void shouldQueryCardActionAndMapAuthorizationReferences() {
        responseBody.set(cardResponse("CAPTURE", "20261031000000000000103", "000106"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.setTransactionId("CMT-CAPTURE-QUERY-001");
        request.getExtension().put("originalTransactionType", ChannelCapability.CAPTURE.getCode());

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/query");
        assertThat(parsePayload())
                .containsOnlyKeys("merchantOrderNo")
                .containsEntry("merchantOrderNo", "CMT-CAPTURE-QUERY-001");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getAuthCode()).isEqualTo("000106");
        assertThat(response.getAcquirerReferenceNo()).isEqualTo("20261031000000000000103");
    }

    @Test
    void shouldQueryLatestCardActionByAuthorizationId() {
        responseBody.set(cardResponse("INCREMENT", null, "000107"));
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.getExtension().put("authorizationId", "CMA-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/query");
        assertThat(parsePayload())
                .containsOnlyKeys("authorizationId")
                .containsEntry("authorizationId", "CMA-001");
        assertThat(response.getAuthCode()).isEqualTo("000107");
        assertThat(response.getRawResponse()).containsEntry("authorizationCode", "000107");
    }

    @Test
    void shouldKeepMalformedSuccessfulResponseUncertain() {
        responseStatus.set(201);
        responseBody.set("not-json");
        PaymentApiClient client = paymentClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-MALFORMED")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldKeepIncompleteAuthorizationResponseUncertain() {
        responseStatus.set(201);
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"status\":\"SUCCESS\"}}");
        PaymentApiClient client = paymentClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-INCOMPLETE")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldKeepMissingSuccessfulStatusUncertain() {
        responseStatus.set(201);
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"transactionId\":\"CMT-CHANNEL-001\",\"authorizationId\":\"CMA-001\"}}");
        PaymentApiClient client = paymentClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-NO-STATUS")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldNotInferChannelMoneyFromRequestWhenResponseOmitsIt() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"authorizationId\":\"CMA-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\"}}");
        PaymentApiClient client = paymentClient(properties());

        ChannelPaymentResponse response = client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-NO-MONEY"));

        assertThat(response.getChannelAmount()).isNull();
        assertThat(response.getChannelCurrency()).isNull();
    }

    @Test
    void shouldMaskCardDataInRequestAudit() {
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId("CMT-PAYMENT-MASK-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        request.setCardNo("4111111111111111");
        request.setSecurityCode("123");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getRequestBodyJsonMasked())
                .doesNotContain("4111111111111111")
                .doesNotContain("\"securityCode\":\"123\"")
                .contains("411111******1111")
                .contains("\"securityCode\":\"***\"");
    }

    @Test
    void shouldMergeOpenPaymentDataWithStandardCardFields() {
        PaymentApiClient client = paymentClient(properties());
        ChannelPaymentRequest request = cardlessPaymentRequest("CMT-PAYMENT-DATA-001");
        request.setPaymentMethod("BANK_CARD");
        request.setCardNo("4111111111111111");
        request.getExtension().put("paymentData", "{\"walletReference\":\"WALLET-001\"}");

        client.execute(request);

        Map<String, Object> payload = parsePayload();
        Map<?, ?> paymentData = (Map<?, ?>) payload.get("paymentData");
        assertThat(paymentData.get("walletReference")).isEqualTo("WALLET-001");
        assertThat(paymentData.get("cardNo")).isEqualTo("4111111111111111");
    }

    @Test
    void shouldKeepRateLimitOutcomeUncertain() {
        responseStatus.set(429);
        responseBody.set("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
        PaymentApiClient client = paymentClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-RATE-LIMITED")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldIgnoreUnknownResponseFields() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"futureRoot\":true,\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"futureData\":{\"nested\":true}}}");
        PaymentApiClient client = paymentClient(properties());

        ChannelPaymentResponse response = client.execute(cardlessPaymentRequest("CMT-001"));

        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelTransactionId()).isEqualTo("TX-001");
    }

    private ChannelPaymentRequest cardRequest(ChannelCapability capability, String transactionId) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(capability.getCode());
        request.setTransactionId(transactionId);
        request.setMerchantOrderNo("MERCHANT-ORDER-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        return request;
    }

    private ChannelPaymentRequest cardlessPaymentRequest(String transactionId) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId(transactionId);
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("UPI");
        return request;
    }

    private String refundResponse() {
        return "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-ORIGINAL-PAYMENT\",\"transactionId\":\"CMT-ORIGINAL-PAYMENT\",\"merchantRefundNo\":\"REFUND-001\",\"refundId\":\"CMR-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}";
    }

    private String cardResponse(String operationType, String arn, String authorizationCode) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("merchantOrderNo", "CMT-PLATFORM-001");
        data.put("transactionId", "CMT-CHANNEL-001");
        data.put("authorizationId", "CMA-001");
        data.put("operationType", operationType);
        data.put("amount", new java.math.BigDecimal("10.25"));
        data.put("currency", "USD");
        data.put("status", "SUCCESS");
        data.put("responseCode", "SUCCESS");
        data.put("responseDescription", "Processed");
        if (arn != null) {
            data.put("acquirerReferenceNumber", arn);
        }
        if (authorizationCode != null) {
            data.put("authorizationCode", authorizationCode);
        }
        return JsonUtils.toJsonString(Map.of("code", "SUCCESS", "message", "OK", "data", data));
    }

    private ChannelProperties properties() {
        ChannelProperties properties = new ChannelProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setVersion("v7");
        properties.setMerchantId("MID-TEST");
        properties.setApiKey("KEY-TEST");
        return properties;
    }

    /**
     * 创建允许本地测试服务器的渠道客户端。
     *
     * @param properties 测试渠道配置
     * @return 使用开发模式出站策略的客户端
     **/
    private PaymentApiClient paymentClient(ChannelProperties properties) {
        return new PaymentApiClient(properties,
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.DEVELOPMENT));
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPath.set(exchange.getRequestURI().getPath());
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload() {
        return (Map<String, Object>) JsonUtils.parseObject(requestBody.get(), Map.class);
    }
}

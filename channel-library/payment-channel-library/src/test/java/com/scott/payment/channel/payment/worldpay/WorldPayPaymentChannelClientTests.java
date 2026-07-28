package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayPaymentChannelClientTests
 * @date : 2026-07-19 23:30
 * @email : scott_x@163.com
 * @description : WorldPay 渠道客户端边界测试，验证 WPGXML/WPGJSON 能按后台 MID 配置发起请求、映射响应并保留脱敏审计字段。
 * @status : create
 */
class WorldPayPaymentChannelClientTests {

    /**
     * WPGXML 应通过请求对象模型生成 XML Direct 支付报文，并把 XML 响应映射为平台统一响应。
     */
    @Test
    void shouldExecuteWorldPayXmlPaymentWithObjectMappedXml() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayXmlApiClient apiClient = new WorldPayXmlApiClient(
                new WorldPayXmlRequestMapper(), new WorldPayXmlResponseMapper(), httpClient);
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = worldPayXmlPaymentRequest();

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGXML.getCode());
        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getChannelCode()).isEqualTo(PaymentChannelCode.WPGXML.getCode());
        assertThat(response.getRawChannelStatus()).isEqualTo("AUTHORISED");
        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelResponseCode()).isEqualTo("0");
        assertThat(response.getAuthCode()).isEqualTo("AUTH-XML-001");
        assertThat(response.getChannelOrderNo()).isEqualTo("ORDER-WPGXML-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-XML-PAYMENT-001");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getRequestUrlMasked())
                .isEqualTo("https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp");
        assertThat(response.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(response.getRequestBodyJsonMasked()).contains("<paymentService");
        assertThat(response.getRequestBodyJsonMasked()).contains("merchantCode=\"AWAPGTEST\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("orderCode=\"ORDER-WPGXML-001\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("captureDelay=\"0\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("<amount currencyCode=\"USD\" exponent=\"2\" value=\"1234\"/>");
        assertThat(response.getRequestBodyJsonMasked()).contains("<cardNumber>512345******0008</cardNumber>");
        assertThat(response.getRequestBodyJsonMasked()).contains("<cvc>***</cvc>");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("5123450000000008", "<cvc>100</cvc>", "xml-password");
        assertThat(response.getRawResponse()).containsEntry("stan", "654321");
        assertThat(httpClient.lastRequest().method()).isEqualTo("POST");
        assertThat(httpClient.lastRequest().headers().firstValue("Content-Type").orElseThrow()).contains("text/xml");
        assertThat(httpClient.lastRequest().headers().firstValue("Accept").orElseThrow()).contains("text/xml");
        assertThat(httpClient.decodedAuthorization()).isEqualTo("xml-user:xml-password");
        assertThat(httpClient.lastBody()).contains("<paymentService");
        assertThat(httpClient.lastBody()).contains("<submit>");
        assertThat(httpClient.lastBody()).contains("<CARD-SSL>");
        assertThat(httpClient.lastBody()).contains("<cardNumber>5123450000000008</cardNumber>");
        assertThat(httpClient.lastBody()).contains("<cvc>100</cvc>");
        assertThat(httpClient.lastBody()).contains("<session id=\"SESSION-XML-001\" shopperIPAddress=\"203.0.113.10\"/>");
        assertThat(httpClient.lastBody()).doesNotContain("<captureDelay>0</captureDelay>");
    }

    /**
     * Spring 容器应能创建 WPGXML 客户端 Bean，避免生产构造器和测试构造器并存时退化为查找无参构造器。
     */
    @Test
    void shouldCreateWorldPayXmlClientBeansInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(WorldPayXmlRequestMapper.class,
                    WorldPayXmlResponseMapper.class,
                    WorldPayXmlApiClient.class,
                    WorldPayXmlPaymentChannelClient.class);
            context.refresh();

            assertThat(context.getBean(WorldPayXmlPaymentChannelClient.class).channelCode())
                    .isEqualTo(PaymentChannelCode.WPGXML.getCode());
        }
    }

    /**
     * Spring 容器应能创建 WPGJSON 客户端 Bean，避免生产构造器和测试构造器并存时退化为查找无参构造器。
     */
    @Test
    void shouldCreateWorldPayJsonClientBeansInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(WorldPayJsonRequestMapper.class,
                    WorldPayJsonResponseMapper.class,
                    WorldPayJsonApiClient.class,
                    WorldPayJsonPaymentChannelClient.class);
            context.refresh();

            assertThat(context.getBean(WorldPayJsonPaymentChannelClient.class).channelCode())
                    .isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        }
    }

    /**
     * WPGJSON 应使用后台配置的 MID 三要素发起 Access Worldpay JSON 支付请求，并把渠道响应映射为统一响应。
     */
    @Test
    void shouldExecuteWorldPayJsonPaymentWithConfiguredMidCredentials() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setOperationId("OP-WPG-001");
        request.setTransactionId("TX-WPG-PAYMENT-001");
        request.setChannelOrderNo("ORDER-WPG-001");
        request.setChannelTransactionId("CH-WPG-PAYMENT-001");
        request.setMerchantId("200045");
        request.setMerchantOrderNo("MO-WPG-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.setCardBrand("MASTERCARD");
        request.getExtension().put("requestId", "CR-WPG-001");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");
        request.getExtension().put("mid.endpointPath", "/api/payments");

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getChannelCode()).isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelResponseCode()).isEqualTo("sentForSettlement");
        assertThat(response.getChannelOrderNo()).isEqualTo("ORDER-WPG-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-PAYMENT-001");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getRequestUrlMasked()).isEqualTo("https://try.access.worldpay.com/api/payments");
        assertThat(response.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"transactionReference\":\"CH-WPG-PAYMENT-001\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"entity\":\"AWAPGTEST\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"amount\":1234");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"currency\":\"USD\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"type\":\"plain\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cardExpiryDate\":{\"month\":\"01\",\"year\":\"2030\"}");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("\"expiryDate\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("\"operation\"", "\"actionLink\"", "\"metadata\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cvc\":\"***\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("5123450000000008", "\"cvc\":\"100\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("json-password");
        assertThat(response.getResponseHeaderJsonMasked()).contains("WP-CorrelationId");
        assertThat(response.getRawResponse()).containsEntry("wpCorrelationId", "WP-CORR-001");
        assertThat(response.getRawResponse()).containsEntry("worldpaySettleLink", "https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        assertThat(httpClient.lastRequest().method()).isEqualTo("POST");
        assertThat(httpClient.lastRequest().headers().firstValue("Content-Type")).contains("application/vnd.worldpay.payments-v7+json");
        assertThat(httpClient.lastRequest().headers().firstValue("Accept")).contains("application/vnd.worldpay.payments-v7+json");
        assertThat(httpClient.decodedAuthorization()).isEqualTo("json-user:json-password");
        assertThat(httpClient.lastBody()).contains("\"transactionReference\":\"CH-WPG-PAYMENT-001\"");
        assertThat(httpClient.lastBody()).contains("\"requestAutoSettlement\":{\"enabled\":true}");
        assertThat(httpClient.lastBody()).contains("\"cardNumber\":\"5123450000000008\"");
        assertThat(httpClient.lastBody()).contains("\"type\":\"plain\"");
        assertThat(httpClient.lastBody()).contains("\"cardExpiryDate\":{\"month\":\"01\",\"year\":\"2030\"}");
        assertThat(httpClient.lastBody()).doesNotContain("\"expiryDate\"", "\"operation\"", "\"actionLink\"", "\"metadata\"");
    }

    /**
     * WPGJSON 显式选择 Card Payments v7 API 族时，应使用 cardPayments endpoint、媒体类型和 card/plain 支付工具类型。
     */
    @Test
    void shouldUseCardPaymentsV7ShapeWhenConfigured() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = worldPayJsonPaymentRequest();
        request.getExtension().remove("mid.endpointPath");
        request.getExtension().put("mid.apiFamily", "CARD_PAYMENTS");

        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getRequestUrlMasked())
                .isEqualTo("https://try.access.worldpay.com/api/cardPayments/customerInitiatedTransactions");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"type\":\"card/plain\"");
        assertThat(httpClient.lastBody()).contains("\"type\":\"card/plain\"");
        assertThat(httpClient.lastRequest().headers().firstValue("Content-Type"))
                .contains("application/vnd.worldpay.cardPayments-v7+json");
        assertThat(httpClient.lastRequest().headers().firstValue("Accept"))
                .contains("application/vnd.worldpay.cardPayments-v7+json");
    }

    /**
     * WPGJSON 后续请款必须使用 Worldpay action link 或显式配置路径，不能把平台交易号硬拼成渠道 URL。
     */
    @Test
    void shouldExecuteWorldPayJsonCaptureWithActionLink() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.CAPTURE.getCode());
        request.setOperationId("OP-WPG-001");
        request.setTransactionId("TX-WPG-CAPTURE-001");
        request.setChannelOrderNo("ORDER-WPG-001");
        request.setChannelTransactionId("CH-WPG-CAPTURE-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.getExtension().put("requestId", "CR-WPG-CAPTURE-001");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("worldpaySettleLink", "https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");

        ChannelPaymentResponse response = client.capture(request);

        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(httpClient.lastRequest().uri().toString())
                .isEqualTo("https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        assertThat(httpClient.lastBody()).contains("\"transactionReference\":\"CH-WPG-CAPTURE-001\"");
        assertThat(httpClient.lastBody()).contains("\"value\":{\"amount\":1234,\"currency\":\"USD\"}");
        assertThat(httpClient.lastBody()).doesNotContain("\"instruction\"", "cardNumber", "cvc");
    }

    /**
     * WPGJSON 最小单位金额应按 service-payment 从数据库币种表透传的辅币位换算，不能固定按两位小数处理。
     */
    @Test
    void shouldUsePropagatedCurrencyExponentWhenBuildingMinorAmount() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId("TX-WPG-BHD-001");
        request.setChannelOrderNo("ORDER-WPG-BHD-001");
        request.setChannelTransactionId("CH-WPG-BHD-001");
        request.setAmount(new BigDecimal("12.345"));
        request.setCurrency("BHD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "3");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");

        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getRequestBodyJsonMasked()).contains("\"amount\":12345");
        assertThat(httpClient.lastBody()).contains("\"currency\":\"BHD\"");
        assertThat(httpClient.lastBody()).contains("\"amount\":12345");
    }

    /**
     * WPGJSON 脱敏必须覆盖 cardNumber、cvc、cavv、password 和 Authorization，避免渠道请求日志泄露卡数据或认证凭据。
     */
    @Test
    void shouldMaskWorldPayJsonSensitiveFields() {
        String json = "{\"card\":{\"cardNumber\":\"5123450000000008\",\"cvc\":\"100\"},"
                + "\"threeDs\":{\"cavv\":\"AAABBIIFmAAAAAAAAAAAAAAAAAA=\"},"
                + "\"authenticationValue\":\"AUTHVALUE\","
                + "\"billingAddress\":{\"address1\":\"1 Main Street\",\"postalCode\":\"12345\"},"
                + "\"cardHolderName\":\"Jane Doe\","
                + "\"password\":\"secret-value\",\"Authorization\":\"Basic abcdef\"}";

        String masked = WorldPayJsonApiClient.maskWorldPayJson(json);

        assertThat(masked).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(masked).contains("\"cvc\":\"***\"");
        assertThat(masked).contains("\"cavv\":\"***\"");
        assertThat(masked).contains("\"authenticationValue\":\"***\"");
        assertThat(masked).contains("\"address1\":\"***\"");
        assertThat(masked).contains("\"postalCode\":\"***\"");
        assertThat(masked).contains("\"cardHolderName\":\"***\"");
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"Authorization\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "\"cvc\":\"100\"", "AAABBIIFmAAAAAAAAAAAAAAAAAA=",
                "AUTHVALUE", "1 Main Street", "\"postalCode\":\"12345\"", "Jane Doe", "secret-value", "Basic abcdef");
    }

    /**
     * WPGXML 响应携带官方 DOCTYPE 时也应可解析，但不得加载外部实体。
     */
    @Test
    void shouldParseWorldPayXmlResponseWithDoctype() {
        WorldPayXmlResponseMapper mapper = new WorldPayXmlResponseMapper();
        ChannelPaymentRequest request = worldPayXmlPaymentRequest();

        ChannelPaymentResponse response = mapper.toChannelResponse(request, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE paymentService PUBLIC "-//WorldPay//DTD WorldPay PaymentService v1//EN" "http://dtd.worldpay.com/paymentService_v1.dtd">
                <paymentService version="1.4" merchantCode="AWAPGTEST">
                  <reply>
                    <orderStatus orderCode="ORDER-WPGXML-001">
                      <payment id="WP-XML-PAYMENT-001" lastEvent="AUTHORISED">
                        <AuthorisationId id="AUTH-XML-001"/>
                        <ISO8583ReturnCode code="0" description="Approved"/>
                      </payment>
                    </orderStatus>
                  </reply>
                </paymentService>
                """);

        assertThat(response.getRawChannelStatus()).isEqualTo("AUTHORISED");
        assertThat(response.getChannelResponseCode()).isEqualTo("0");
        assertThat(response.getAuthCode()).isEqualTo("AUTH-XML-001");
    }

    /**
     * WPGXML 后续请款应通过对象模型生成 modify/orderModification/capture 报文。
     */
    @Test
    void shouldExecuteWorldPayXmlCaptureWithModifyXml() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayXmlApiClient apiClient = new WorldPayXmlApiClient(
                new WorldPayXmlRequestMapper(), new WorldPayXmlResponseMapper(), httpClient);
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient(apiClient);
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(PaymentChannelCode.WPGXML.getCode());
        request.setTransactionType(ChannelCapability.CAPTURE.getCode());
        request.setOperationId("OP-WPGXML-001");
        request.setTransactionId("TX-WPGXML-CAPTURE-001");
        request.setChannelOrderNo("ORDER-WPGXML-001");
        request.setChannelTransactionId("CH-WPGXML-CAPTURE-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.getExtension().put("requestUrl", "https://secure-test.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "xml-user");
        request.getExtension().put("mid.password", "xml-password");

        ChannelPaymentResponse response = client.capture(request);

        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURE_REQUESTED");
        assertThat(response.getChannelTradeStatus()).isEqualTo("PENDING");
        assertThat(httpClient.lastBody()).contains("<modify>");
        assertThat(httpClient.lastBody()).contains("<orderModification orderCode=\"ORDER-WPGXML-001\">");
        assertThat(httpClient.lastBody()).contains("<capture>");
        assertThat(httpClient.lastBody()).contains("<amount currencyCode=\"USD\" exponent=\"2\" value=\"1234\"/>");
        assertThat(httpClient.lastBody()).doesNotContain("cardNumber", "cvc");
    }

    /**
     * WPGXML 查询应生成 inquiry/orderInquiry 报文，并解析 orderStatus 中的资金确认状态。
     */
    @Test
    void shouldExecuteWorldPayXmlInquiry() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayXmlApiClient apiClient = new WorldPayXmlApiClient(
                new WorldPayXmlRequestMapper(), new WorldPayXmlResponseMapper(), httpClient);
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient(apiClient);
        ChannelQueryRequest request = new ChannelQueryRequest();
        request.setChannelCode(PaymentChannelCode.WPGXML.getCode());
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.setTransactionId("TX-WPGXML-QUERY-001");
        request.setChannelOrderNo("ORDER-WPGXML-001");
        request.getExtension().put("requestUrl", "https://secure-test.worldpay.com");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "xml-user");
        request.getExtension().put("mid.password", "xml-password");

        ChannelPaymentResponse response = client.query(request);

        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-XML-PAYMENT-001");
        assertThat(httpClient.lastBody()).contains("<inquiry>");
        assertThat(httpClient.lastBody()).contains("<orderInquiry orderCode=\"ORDER-WPGXML-001\"/>");
        assertThat(httpClient.lastBody()).doesNotContain("cardNumber", "cvc");
    }

    /**
     * WPGXML 最小单位金额应按数据库币种表透传的辅币位换算，不能固定按两位小数处理。
     */
    @Test
    void shouldUsePropagatedCurrencyExponentWhenBuildingWorldPayXmlAmount() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayXmlApiClient apiClient = new WorldPayXmlApiClient(
                new WorldPayXmlRequestMapper(), new WorldPayXmlResponseMapper(), httpClient);
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = worldPayXmlPaymentRequest();
        request.setTransactionId("TX-WPGXML-BHD-001");
        request.setChannelOrderNo("ORDER-WPGXML-BHD-001");
        request.setChannelTransactionId("CH-WPGXML-BHD-001");
        request.setAmount(new BigDecimal("12.345"));
        request.setCurrency("BHD");
        request.getExtension().put("currencyExponent", "3");

        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getRequestBodyJsonMasked()).contains("currencyCode=\"BHD\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("exponent=\"3\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("value=\"12345\"");
        assertThat(httpClient.lastBody()).contains("currencyCode=\"BHD\"");
        assertThat(httpClient.lastBody()).contains("value=\"12345\"");
    }

    /**
     * WPGXML 脱敏必须覆盖 cardNumber、cvc、cavv、password 和 Authorization，避免渠道请求日志泄露卡数据或认证凭据。
     */
    @Test
    void shouldMaskWorldPayXmlSensitiveFields() {
        String xml = """
                Authorization: Basic abcdef
                <paymentService merchantCode="AWAPGTEST">
                  <cardNumber>5123450000000008</cardNumber>
                  <cvc>100</cvc>
                  <cavv>AAABBIIFmAAAAAAAAAAAAAAAAAA=</cavv>
                  <password>secret-value</password>
                </paymentService>
                """;

        String masked = WorldPayXmlApiClient.maskWorldPayXml(xml);

        assertThat(masked).contains("<cardNumber>512345******0008</cardNumber>");
        assertThat(masked).contains("<cvc>***</cvc>");
        assertThat(masked).contains("<cavv>***</cavv>");
        assertThat(masked).contains("<password>***</password>");
        assertThat(masked).contains("Authorization: Basic ***");
        assertThat(masked).doesNotContain("5123450000000008", "<cvc>100</cvc>", "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "secret-value", "Basic abcdef");
    }

    private ChannelPaymentRequest worldPayXmlPaymentRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGXML.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setOperationId("OP-WPGXML-001");
        request.setTransactionId("TX-WPGXML-PAYMENT-001");
        request.setChannelOrderNo("ORDER-WPGXML-001");
        request.setChannelTransactionId("CH-WPGXML-PAYMENT-001");
        request.setMerchantId("200045");
        request.setMerchantOrderNo("MO-WPGXML-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.getExtension().put("requestUrl", "https://secure-test.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "xml-user");
        request.getExtension().put("mid.password", "xml-password");
        request.getExtension().put("clientIp", "203.0.113.10");
        request.getExtension().put("sessionId", "SESSION-XML-001");
        request.getExtension().put("userAgent", "Mozilla/5.0");
        return request;
    }

    private ChannelPaymentRequest worldPayJsonPaymentRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setOperationId("OP-WPG-001");
        request.setTransactionId("TX-WPG-PAYMENT-001");
        request.setChannelOrderNo("ORDER-WPG-001");
        request.setChannelTransactionId("CH-WPG-PAYMENT-001");
        request.setMerchantId("200045");
        request.setMerchantOrderNo("MO-WPG-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.setCardBrand("MASTERCARD");
        request.getExtension().put("requestId", "CR-WPG-001");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");
        request.getExtension().put("mid.endpointPath", "/api/payments");
        return request;
    }

    private static class CapturingHttpClient extends HttpClient {

        /**
         * 最近一次 HTTP 请求，用于断言 Worldpay 客户端实际发出的 method、URL 和请求头。
         */
        private HttpRequest lastRequest;

        /**
         * 最近一次请求体，仅在测试中读取，生产日志不得输出未脱敏报文。
         */
        private String lastBody;

        private HttpRequest lastRequest() {
            return lastRequest;
        }

        private String lastBody() {
            return lastBody;
        }

        private String decodedAuthorization() {
            String authorization = lastRequest.headers().firstValue("Authorization").orElseThrow();
            return new String(java.util.Base64.getDecoder().decode(authorization.substring("Basic ".length())));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, null, new SecureRandom());
                return context;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request;
            this.lastBody = request.bodyPublisher()
                    .map(ignored -> requestBodyFromKnownPublisher(request))
                    .orElse("");
            String responseBody = responseBody(this.lastBody);
            @SuppressWarnings("unchecked")
            T body = (T) responseBody;
            return new SimpleHttpResponse<>(request, body, contentType(responseBody));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("async not used"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("async not used"));
        }

        private String requestBodyFromKnownPublisher(HttpRequest request) {
            java.util.concurrent.Flow.Publisher<java.nio.ByteBuffer> publisher = request.bodyPublisher().orElse(null);
            if (publisher == null) {
                return "";
            }
            BodySubscriber subscriber = new BodySubscriber();
            publisher.subscribe(subscriber);
            return subscriber.body();
        }

        private String responseBody(String requestBody) {
            if (requestBody != null && requestBody.contains("<paymentService")) {
                if (requestBody.contains("<capture>")) {
                    return """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <paymentService version="1.4" merchantCode="AWAPGTEST">
                              <reply>
                                <ok>
                                  <captureReceived orderCode="ORDER-WPGXML-001"/>
                                </ok>
                              </reply>
                            </paymentService>
                            """;
                }
                if (requestBody.contains("<inquiry>")) {
                    return worldPayXmlOrderStatus("CAPTURED");
                }
                return worldPayXmlOrderStatus("AUTHORISED");
            }
            return "{\"outcome\":\"sentForSettlement\",\"paymentId\":\"WP-PAYMENT-001\","
                    + "\"orderCode\":\"ORDER-WPG-001\",\"requestId\":\"CR-WPG-001\","
                    + "\"authorizationCode\":\"123456\",\"stan\":\"654321\",\"rrn\":\"RRN-WPG-001\","
                    + "\"acquirerReference\":\"ARN-WPG-001\","
                    + "\"paymentInstrument\":{\"type\":\"CARD\",\"brand\":\"MASTERCARD\","
                    + "\"cardNumberMasked\":\"512345******0008\"},"
                    + "\"_links\":{\"cardPayments:settle\":{\"href\":\"https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements\",\"method\":\"POST\"},"
                    + "\"payments:events\":{\"href\":\"https://try.access.worldpay.com/api/payments/events?transactionRef=CH-WPG-PAYMENT-001&entity=AWAPGTEST\",\"method\":\"GET\"}}}";
        }

        private String worldPayXmlOrderStatus(String lastEvent) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <paymentService version="1.4" merchantCode="AWAPGTEST">
                      <reply>
                        <orderStatus orderCode="ORDER-WPGXML-001">
                          <payment id="WP-XML-PAYMENT-001" lastEvent="%s">
                            <amount value="1234" currencyCode="USD" exponent="2"/>
                            <AuthorisationId id="AUTH-XML-001"/>
                            <ISO8583ReturnCode code="0" description="Approved"/>
                            <stan>654321</stan>
                          </payment>
                        </orderStatus>
                      </reply>
                    </paymentService>
                    """.formatted(lastEvent);
        }

        private String contentType(String responseBody) {
            return responseBody != null && responseBody.contains("<paymentService")
                    ? "text/xml"
                    : "application/vnd.worldpay.payments-v7+json";
        }
    }

    private static class BodySubscriber implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {

        /**
         * 请求体缓冲区，仅用于单元测试读取 JDK HttpRequest BodyPublisher 内容。
         */
        private final StringBuilder body = new StringBuilder();

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer item) {
            body.append(StandardCharsets.UTF_8.decode(item));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return body.toString();
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request, T body, String contentType) implements HttpResponse<T> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(
                    "WP-CorrelationId", java.util.List.of("WP-CORR-001"),
                    "Content-Type", java.util.List.of(contentType)
            ), (name, value) -> true);
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}

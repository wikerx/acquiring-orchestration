package com.scott.payment.channel.payment.worldpay.adapter;

import com.scott.payment.channel.payment.worldpay.support.ChannelCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * WorldPay 渠道客户端边界测试，验证 WPGXML/WPGJSON 能按后台 MID 配置发起请求、映射响应并保留脱敏审计字段。
 */
class PaymentChannelAdapterTests {

    /**
     * WPGXML 应通过请求对象模型生成 XML Direct 支付报文，并把 XML 响应映射为平台统一响应。
     */
    @Test
    void shouldExecuteWorldPayXmlPaymentWithObjectMappedXml() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        com.scott.payment.channel.payment.worldpay.xml.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter(apiClient);
        ChannelPaymentRequest request = worldPayXmlPaymentRequest();

        assertThat(client.channelCode()).isEqualTo(ChannelCode.WPGXML);
        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getChannelCode()).isEqualTo(ChannelCode.WPGXML);
        assertThat(response.getRawChannelStatus()).isEqualTo("AUTHORISED");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.AUTHORIZED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("0");
        assertThat(response.getAuthCode()).isEqualTo("AUTH-XML-001");
        assertThat(response.getChannelOrderNo()).isEqualTo("ORDER-WPGXML-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-XML-PAYMENT-001");
        assertThat(response.getChannelCurrency()).isEqualTo("USD");
        assertThat(response.getChannelAmount()).isEqualByComparingTo("12.34");
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
            context.register(com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter.class,
                    com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter.class,
                    com.scott.payment.channel.payment.worldpay.xml.client.ApiClient.class,
                    com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter.class);
            context.refresh();

            assertThat(context.getBean(com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter.class).channelCode())
                    .isEqualTo(ChannelCode.WPGXML);
        }
    }

    /**
     * Spring 容器应能创建 WPGJSON 客户端 Bean，避免生产构造器和测试构造器并存时退化为查找无参构造器。
     */
    @Test
    void shouldCreateWorldPayJsonClientBeansInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter.class,
                    com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter.class,
                    com.scott.payment.channel.payment.worldpay.json.client.ApiClient.class,
                    com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter.class);
            context.refresh();

            assertThat(context.getBean(com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter.class).channelCode())
                    .isEqualTo(ChannelCode.WPGJSON);
        }
    }

    /**
     * WPGJSON 应使用后台配置的 MID 三要素发起 Access Worldpay JSON 支付请求，并把渠道响应映射为统一响应。
     */
    @Test
    void shouldExecuteWorldPayJsonPaymentWithConfiguredMidCredentials() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        com.scott.payment.channel.payment.worldpay.json.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(ChannelCode.WPGJSON);
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

        assertThat(client.channelCode()).isEqualTo(ChannelCode.WPGJSON);
        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getChannelCode()).isEqualTo(ChannelCode.WPGJSON);
        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.CAPTURED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("sentForSettlement");
        assertThat(response.getChannelOrderNo()).isEqualTo("ORDER-WPG-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-PAYMENT-001");
        assertThat(response.getChannelCurrency()).isEqualTo("USD");
        assertThat(response.getChannelAmount()).isEqualByComparingTo("12.34");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getRequestUrlMasked()).isEqualTo("https://try.access.worldpay.com/api/payments");
        assertThat(response.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"transactionReference\":\"CH-WPG-PAYMENT-001\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"entity\":\"AWAPGTEST\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"amount\":1234");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"currency\":\"USD\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"type\":\"plain\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cardExpiryDate\":{\"month\":\"***\",\"year\":\"***\"}");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("\"expiryDate\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("\"operation\"", "\"actionLink\"", "\"metadata\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cvc\":\"***\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("5123450000000008", "\"month\":\"01\"", "\"year\":\"2030\"", "\"cvc\":\"100\"");
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
        com.scott.payment.channel.payment.worldpay.json.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter(apiClient);
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
        com.scott.payment.channel.payment.worldpay.json.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter(apiClient);
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(ChannelCode.WPGJSON);
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
        com.scott.payment.channel.payment.worldpay.json.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(ChannelCode.WPGJSON);
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
                + "\"cardExpiryDate\":{\"month\":\"01\",\"year\":\"2039\"},"
                + "\"billingAddress\":{\"address1\":\"1 Main Street\",\"postalCode\":\"12345\",\"city\":\"Boston\",\"state\":\"MA\"},"
                + "\"cardHolderName\":\"Jane Doe\",\"shopperEmailAddress\":\"shopper@example.com\","
                + "\"password\":\"secret-value\",\"Authorization\":\"Basic abcdef\"}";

        String masked = com.scott.payment.channel.payment.worldpay.json.client.ApiClient.maskJson(json);

        assertThat(masked).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(masked).contains("\"cvc\":\"***\"");
        assertThat(masked).contains("\"cavv\":\"***\"");
        assertThat(masked).contains("\"authenticationValue\":\"***\"");
        assertThat(masked).contains("\"cardExpiryDate\":{\"month\":\"***\",\"year\":\"***\"}");
        assertThat(masked).contains("\"address1\":\"***\"");
        assertThat(masked).contains("\"postalCode\":\"***\"");
        assertThat(masked).contains("\"city\":\"***\"");
        assertThat(masked).contains("\"state\":\"***\"");
        assertThat(masked).contains("\"cardHolderName\":\"***\"");
        assertThat(masked).contains("\"shopperEmailAddress\":\"***\"");
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"Authorization\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "\"cvc\":\"100\"", "AAABBIIFmAAAAAAAAAAAAAAAAAA=",
                "AUTHVALUE", "\"month\":\"01\"", "\"year\":\"2039\"", "1 Main Street", "\"postalCode\":\"12345\"",
                "Boston", "Jane Doe", "shopper@example.com", "secret-value", "Basic abcdef");
    }

    /**
     * WPGXML 响应携带官方 DOCTYPE 时也应可解析，但不得加载外部实体。
     */
    @Test
    void shouldParseWorldPayXmlResponseWithDoctype() {
        com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter mapper = new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter();
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
        com.scott.payment.channel.payment.worldpay.xml.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter(apiClient);
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(ChannelCode.WPGXML);
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
        com.scott.payment.channel.payment.worldpay.xml.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter(apiClient);
        ChannelQueryRequest request = new ChannelQueryRequest();
        request.setChannelCode(ChannelCode.WPGXML);
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.setTransactionId("TX-WPGXML-QUERY-001");
        request.setChannelOrderNo("ORDER-WPGXML-001");
        request.getExtension().put("requestUrl", "https://secure-test.worldpay.com");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "xml-user");
        request.getExtension().put("mid.password", "xml-password");

        ChannelPaymentResponse response = client.query(request);

        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.CAPTURED.getCode());
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
        com.scott.payment.channel.payment.worldpay.xml.client.ApiClient apiClient = new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), httpClient);
        com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter client = new com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter(apiClient);
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
                  <expiryDate><date month="01" year="2039"/></expiryDate>
                  <cvc>100</cvc>
                  <cavv>AAABBIIFmAAAAAAAAAAAAAAAAAA=</cavv>
                  <cardHolderName>Jane Doe</cardHolderName>
                  <address1>1 Main Street</address1>
                  <postalCode>12345</postalCode>
                  <city>Boston</city>
                  <state>MA</state>
                  <shopperEmailAddress>shopper@example.com</shopperEmailAddress>
                  <session id="SESSION-001" shopperIPAddress="203.0.113.10"/>
                  <userAgentHeader>Mozilla/5.0</userAgentHeader>
                  <password>secret-value</password>
                </paymentService>
                """;

        String masked = com.scott.payment.channel.payment.worldpay.xml.client.ApiClient.maskXml(xml);

        assertThat(masked).contains("<cardNumber>512345******0008</cardNumber>");
        assertThat(masked).contains("<expiryDate><date month=\"***\" year=\"***\"/></expiryDate>");
        assertThat(masked).contains("<cvc>***</cvc>");
        assertThat(masked).contains("<cavv>***</cavv>");
        assertThat(masked).contains("<cardHolderName>***</cardHolderName>");
        assertThat(masked).contains("<address1>***</address1>");
        assertThat(masked).contains("<postalCode>***</postalCode>");
        assertThat(masked).contains("<city>***</city>");
        assertThat(masked).contains("<state>***</state>");
        assertThat(masked).contains("<shopperEmailAddress>***</shopperEmailAddress>");
        assertThat(masked).contains("shopperIPAddress=\"***\"");
        assertThat(masked).contains("<userAgentHeader>***</userAgentHeader>");
        assertThat(masked).contains("<password>***</password>");
        assertThat(masked).contains("Authorization: Basic ***");
        assertThat(masked).doesNotContain("5123450000000008", "month=\"01\"", "year=\"2039\"",
                "<cvc>100</cvc>", "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "Jane Doe", "1 Main Street", "<postalCode>12345</postalCode>",
                "Boston", "shopper@example.com", "203.0.113.10", "Mozilla/5.0", "secret-value", "Basic abcdef");
    }

    /**
     * Worldpay application logs retain correlation metadata but never include request or response payload structures.
     */
    @Test
    void shouldLogOnlyWorldPayPayloadMetadata() {
        ChannelPaymentRequest jsonRequest = worldPayJsonPaymentRequest();
        jsonRequest.setBillingInfo(personalBillingInfo());
        List<String> jsonLogs = captureLogs(com.scott.payment.channel.payment.worldpay.json.client.ApiClient.class, () -> new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), new CapturingHttpClient())
                .execute(jsonRequest));

        ChannelPaymentRequest xmlRequest = worldPayXmlPaymentRequest();
        xmlRequest.setBillingInfo(personalBillingInfo());
        List<String> xmlLogs = captureLogs(com.scott.payment.channel.payment.worldpay.xml.client.ApiClient.class, () -> new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(), new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), new CapturingHttpClient())
                .execute(xmlRequest));

        assertPayloadMetadataLogs(jsonLogs);
        assertPayloadMetadataLogs(xmlLogs);
    }

    /**
     * WPGJSON 对超时、歧义 HTTP 状态、非法 2xx 和空 2xx 响应必须保留结果不确定语义。
     */
    @Test
    void shouldPreserveWorldPayJsonUncertainOutcomes() {
        String errorBody = "{\"error\":{\"code\":\"SERVER_ERROR\",\"message\":\"Temporary failure\"}}";
        for (int httpStatus : List.of(408, 409, 425, 429, 500)) {
            assertUncertainOutcome(() -> jsonClient(new CapturingHttpClient(httpStatus, errorBody))
                    .execute(worldPayJsonPaymentRequest()), ChannelResponseException.class);
        }
        assertUncertainOutcome(() -> jsonClient(new CapturingHttpClient(200, "not-json"))
                .execute(worldPayJsonPaymentRequest()), ChannelResponseException.class);
        assertUncertainOutcome(() -> jsonClient(new CapturingHttpClient(200, ""))
                .execute(worldPayJsonPaymentRequest()), ChannelResponseException.class);
        assertUncertainOutcome(() -> jsonClient(new CapturingHttpClient(
                new HttpTimeoutException("simulated timeout"))).execute(worldPayJsonPaymentRequest()),
                ChannelTimeoutException.class);
    }

    /**
     * WPGJSON 明确的 400 error 响应表示请求被渠道拒绝，应映射失败并保留渠道错误码。
     */
    @Test
    void shouldMapWorldPayJsonExplicitBadRequestAsFailure() {
        ChannelPaymentResponse response = jsonClient(new CapturingHttpClient(400,
                        "{\"error\":{\"code\":\"INVALID_REQUEST\",\"message\":\"Invalid payment request\"}}"))
                .execute(worldPayJsonPaymentRequest());

        assertThat(response.getRawChannelStatus()).isEqualTo("ERROR");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
    }

    /**
     * WPGXML 对超时、歧义 HTTP 状态、非法 2xx 和空 2xx 响应必须保留结果不确定语义。
     */
    @Test
    void shouldPreserveWorldPayXmlUncertainOutcomes() {
        String errorBody = """
                <paymentService version="1.4" merchantCode="AWAPGTEST">
                  <reply><error code="SERVER_ERROR">Temporary failure</error></reply>
                </paymentService>
                """;
        for (int httpStatus : List.of(408, 409, 425, 429, 500)) {
            assertUncertainOutcome(() -> xmlClient(new CapturingHttpClient(httpStatus, errorBody))
                    .execute(worldPayXmlPaymentRequest()), ChannelResponseException.class);
        }
        assertUncertainOutcome(() -> xmlClient(new CapturingHttpClient(200, "not-xml"))
                .execute(worldPayXmlPaymentRequest()), ChannelResponseException.class);
        assertUncertainOutcome(() -> xmlClient(new CapturingHttpClient(200, ""))
                .execute(worldPayXmlPaymentRequest()), ChannelResponseException.class);
        assertUncertainOutcome(() -> xmlClient(new CapturingHttpClient(
                new HttpTimeoutException("simulated timeout"))).execute(worldPayXmlPaymentRequest()),
                ChannelTimeoutException.class);
    }

    /**
     * WPGXML 明确的 400 error 响应表示请求被渠道拒绝，应映射失败并保留渠道错误码。
     */
    @Test
    void shouldMapWorldPayXmlExplicitBadRequestAsFailure() {
        ChannelPaymentResponse response = xmlClient(new CapturingHttpClient(400, """
                <paymentService version="1.4" merchantCode="AWAPGTEST">
                  <reply><error code="INVALID_REQUEST">Invalid payment request</error></reply>
                </paymentService>
                """)).execute(worldPayXmlPaymentRequest());

        assertThat(response.getRawChannelStatus()).isEqualTo("ERROR");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
    }

    /**
     * 创建注入指定 HTTP 测试桩的 WPGJSON 客户端。
     *
     * @param httpClient HTTP 测试桩
     * @return WPGJSON API 客户端
     */
    private com.scott.payment.channel.payment.worldpay.json.client.ApiClient jsonClient(HttpClient httpClient) {
        return new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter(),
                new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(), httpClient);
    }

    /**
     * 创建注入指定 HTTP 测试桩的 WPGXML 客户端。
     *
     * @param httpClient HTTP 测试桩
     * @return WPGXML API 客户端
     */
    private com.scott.payment.channel.payment.worldpay.xml.client.ApiClient xmlClient(HttpClient httpClient) {
        return new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(
                new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(),
                new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(), httpClient);
    }

    /**
     * 断言渠道异常明确标记结果不确定，防止上层把可能已受理的资金动作直接置为失败。
     *
     * @param action 渠道调用
     * @param exceptionType 期望异常类型
     */
    private void assertUncertainOutcome(Runnable action, Class<? extends ChannelException> exceptionType) {
        Throwable throwable = catchThrowable(action::run);
        assertThat(throwable).isInstanceOf(exceptionType);
        assertThat(((ChannelException) throwable).isOutcomeUncertain()).isTrue();
    }

    private List<String> captureLogs(Class<?> loggerType, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerType);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertPayloadMetadataLogs(List<String> logs) {
        assertThat(logs)
                .anySatisfy(message -> assertThat(message)
                        .contains("event: CHANNEL_REQUEST_START", "payloadLength:", "payloadDigest: sha256:"))
                .anySatisfy(message -> assertThat(message)
                        .contains("event: CHANNEL_RESPONSE_END", "payloadLength:", "payloadDigest: sha256:"))
                .allSatisfy(message -> assertThat(message).doesNotContain(
                        "requestSummary:", "responseSummary:", "<paymentService", "\"paymentInstrument\"",
                        "5123450000000008", "Jane Doe", "1 Main Street", "shopper@example.com"));
    }

    private ChannelPaymentRequest.BillingInfo personalBillingInfo() {
        ChannelPaymentRequest.BillingInfo billingInfo = new ChannelPaymentRequest.BillingInfo();
        billingInfo.setFirstName("Jane");
        billingInfo.setLastName("Doe");
        billingInfo.setEmail("shopper@example.com");
        billingInfo.setStreet("1 Main Street");
        billingInfo.setPostal("12345");
        billingInfo.setCity("Boston");
        billingInfo.setState("MA");
        billingInfo.setCountry("US");
        return billingInfo;
    }

    private ChannelPaymentRequest worldPayXmlPaymentRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(ChannelCode.WPGXML);
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
        request.setChannelCode(ChannelCode.WPGJSON);
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
         * 模拟渠道 HTTP 状态码。
         */
        private final int responseStatus;

        /**
         * 模拟渠道响应体；为空引用时按请求类型生成默认成功响应。
         */
        private final String responseBodyOverride;

        /**
         * 模拟网络异常；非空时在同步发送阶段抛出。
         */
        private final IOException sendException;

        /**
         * 最近一次 HTTP 请求，用于断言 Worldpay 客户端实际发出的 method、URL 和请求头。
         */
        private HttpRequest lastRequest;

        /**
         * 最近一次请求体，仅在测试中读取，生产日志不得输出未脱敏报文。
         */
        private String lastBody;

        /**
         * 创建返回默认 HTTP 200 成功响应的测试客户端。
         */
        private CapturingHttpClient() {
            this(200, null, null);
        }

        /**
         * 创建返回指定 HTTP 状态和响应体的测试客户端。
         *
         * @param responseStatus HTTP 状态码
         * @param responseBodyOverride 响应体
         */
        private CapturingHttpClient(int responseStatus, String responseBodyOverride) {
            this(responseStatus, responseBodyOverride, null);
        }

        /**
         * 创建在同步发送阶段抛出指定异常的测试客户端。
         *
         * @param sendException 模拟网络异常
         */
        private CapturingHttpClient(IOException sendException) {
            this(200, null, sendException);
        }

        /**
         * 创建带完整模拟结果的测试客户端。
         *
         * @param responseStatus HTTP 状态码
         * @param responseBodyOverride 响应体；为空引用时生成默认成功响应
         * @param sendException 模拟网络异常；为空时正常返回响应
         */
        private CapturingHttpClient(int responseStatus,
                                    String responseBodyOverride,
                                    IOException sendException) {
            this.responseStatus = responseStatus;
            this.responseBodyOverride = responseBodyOverride;
            this.sendException = sendException;
        }

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

        /**
         * 不提供 Cookie 管理器，确保测试请求不携带进程外会话状态。
         */
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        /**
         * 返回固定连接超时，仅满足 JDK HTTP 客户端契约。
         */
        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        /**
         * 禁止自动重定向，避免请求地址断言被隐藏跳转改变。
         */
        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        /**
         * 不使用代理，保证测试桩不依赖本机网络配置。
         */
        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        /**
         * 创建独立 TLS 上下文以满足抽象客户端契约，不建立真实网络连接。
         */
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

        /**
         * 返回默认 TLS 参数；测试不协商真实协议或密码套件。
         */
        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        /**
         * 不注册 JDK Authenticator，Basic 凭据由待测客户端显式生成请求头。
         */
        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        /**
         * 固定声明 HTTP/1.1，使测试响应协议版本保持确定。
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        /**
         * 不提供异步执行器，因为当前测试仅走同步发送路径。
         */
        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            if (sendException != null) {
                throw sendException;
            }
            this.lastRequest = request;
            this.lastBody = request.bodyPublisher()
                    .map(ignored -> requestBodyFromKnownPublisher(request))
                    .orElse("");
            String responseBody = responseBodyOverride == null ? responseBody(this.lastBody) : responseBodyOverride;
            @SuppressWarnings("unchecked")
            T body = (T) responseBody;
            return new SimpleHttpResponse<>(request, body, contentType(responseBody), responseStatus);
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
                    + "\"value\":{\"amount\":1234,\"currency\":\"USD\"},"
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

        /**
         * 请求有限请求体发布器的全部数据，供测试完整还原 HTTP 请求体。
         */
        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        /**
         * 将每个 UTF-8 数据块追加到测试缓冲区。
         */
        @Override
        public void onNext(java.nio.ByteBuffer item) {
            body.append(StandardCharsets.UTF_8.decode(item));
        }

        /**
         * 将请求体发布异常转换为测试失败，禁止静默产生不完整报文。
         */
        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        /**
         * 有限请求体读取完成后无需额外动作，缓冲区已包含全部数据。
         */
        @Override
        public void onComplete() {
        }

        private String body() {
            return body.toString();
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request,
                                         T body,
                                         String contentType,
                                         int responseStatus) implements HttpResponse<T> {

        /**
         * 固定返回 HTTP 200，渠道成功或失败由响应体中的业务状态表达。
         */
        @Override
        public int statusCode() {
            return responseStatus;
        }

        /**
         * 返回固定关联号和按响应体选择的内容类型，供渠道响应解析断言。
         */
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(
                    "WP-CorrelationId", java.util.List.of("WP-CORR-001"),
                    "Content-Type", java.util.List.of(contentType)
            ), (name, value) -> true);
        }

        /**
         * 固定表示不存在重定向前响应。
         */
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        /**
         * 返回原测试请求 URI，使响应与被捕获请求保持关联。
         */
        @Override
        public URI uri() {
            return request.uri();
        }

        /**
         * 固定返回 HTTP/1.1，与测试客户端声明保持一致。
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        /**
         * 不返回 TLS 会话，因为响应由内存测试桩生成。
         */
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}

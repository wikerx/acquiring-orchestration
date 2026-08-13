package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelClientThreeDsTests
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : MPGS 统一 3DS SPI 行为测试，验证 provider 按浏览器交互阶段执行单次 API 调用、协议请求映射和平台状态归一化。
 * @status : create
 */
@Slf4j
class MpgsPaymentChannelClientThreeDsTests {

    /**
     * 验证初始化阶段只调用 INITIATE_AUTHENTICATION；未要求 3DS Method 时通知平台继续 Authenticate Payer。
     */
    @Test
    void shouldInitiateOnlyAndReturnReadyToAuthenticate() {
        log.info("MPGS统一3DS测试开始，case: 初始化后可继续认证付款人");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse initiate = mpgsResponse();
        initiate.setAuthenticationStatus("AUTHENTICATION_AVAILABLE");
        when(apiClient.initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(initiate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);

        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.INITIALIZE);
        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getPhase()).isEqualTo(ChannelThreeDsPhase.INITIALIZE);
        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.READY_TO_AUTHENTICATE);
        assertThat(response.getChannelCode()).isEqualTo("MPGS");
        assertThat(response.getAuthenticationTransactionId()).isEqualTo("3DSTX-001");
        assertThat(response.getThreeDsTransactionId()).isEqualTo("3DS-PROTOCOL-001");
        assertThat(response.getEci()).isEqualTo("05");
        assertThat(response.getCavv()).isEqualTo("authentication-value");

        ArgumentCaptor<MpgsThreeDsAuthenticationRequest> requestCaptor =
                ArgumentCaptor.forClass(MpgsThreeDsAuthenticationRequest.class);
        verify(apiClient).initiateAuthentication(requestCaptor.capture());
        verify(apiClient, never()).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
        assertThat(requestCaptor.getValue().getChannelOrderNo()).isEqualTo("TX-001");
        assertThat(requestCaptor.getValue().getAuthenticationTransactionId()).isEqualTo("3DSTX-001");
        assertThat(requestCaptor.getValue().getRedirectResponseUrl()).isEqualTo("https://checkout.example.test/3ds/return");
        assertThat(requestCaptor.getValue().getExtension()).containsEntry("mid.merchantId", "MID-001");
        log.info("MPGS统一3DS测试完成，status: {}, authenticationTransactionId: {}",
                response.getStatus(), response.getAuthenticationTransactionId());
    }

    /**
     * 验证初始化阶段返回的 HTML 被识别为 3DS Method，不继续执行付款人认证。
     */
    @Test
    void shouldReturnMethodActionWhenInitiationProvidesRedirectHtml() {
        log.info("MPGS统一3DS测试开始，case: 初始化阶段要求3DS Method");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse initiate = mpgsResponse();
        initiate.setAuthenticationStatus("AUTHENTICATION_PENDING");
        initiate.setRedirectHtml("<form id=\"three-ds\"></form>");
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("PROCEED");
        when(apiClient.initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(initiate);
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request());

        assertThat(response.getPhase()).isEqualTo(ChannelThreeDsPhase.INITIALIZE);
        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.METHOD_REQUIRED);
        assertThat(response.getRedirectHtml()).isEqualTo("<form id=\"three-ds\"></form>");
        verify(apiClient).initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient, never()).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
        log.info("MPGS统一3DS测试完成，status: {}, hasRedirectHtml: true", response.getStatus());
    }

    /**
     * 验证付款人认证阶段只调用 AUTHENTICATE_PAYER，并把免质询成功映射为 PASSED。
     */
    @Test
    void shouldAuthenticatePayerOnlyAndReturnPassed() {
        log.info("MPGS统一3DS测试开始，case: 付款人免质询认证通过");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("PROCEED");
        authenticate.setAuthenticationStatus("AUTHENTICATION_SUCCESSFUL");
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getPhase()).isEqualTo(ChannelThreeDsPhase.AUTHENTICATE);
        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.PASSED);
        verify(apiClient, never()).initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
        log.info("MPGS统一3DS测试完成，status: {}", response.getStatus());
    }

    /**
     * 验证仅尝试过认证不等于认证成功，强制 3DS 交易必须阻止后续支付提交。
     */
    @Test
    void shouldMapAttemptedAuthenticationToFailed() {
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("PROCEED");
        authenticate.setAuthenticationStatus("AUTHENTICATION_ATTEMPTED");
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.FAILED);
    }

    /**
     * 验证强制 3DS 交易不支持认证时直接失败，不能降级为非 3DS 支付。
     */
    @Test
    void shouldMapUnsupportedAuthenticationToFailed() {
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("PROCEED");
        authenticate.setAuthenticationStatus("AUTHENTICATION_NOT_SUPPORTED");
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.FAILED);
    }

    /**
     * 验证网关建议继续不代表持卡人认证成功，缺少明确成功状态时不得放行支付。
     */
    @Test
    void shouldNotPassOnProceedRecommendationAlone() {
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("PROCEED");
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.PROCESSING);
    }

    /**
     * 验证付款人认证阶段返回的 HTML 被识别为 ACS Challenge。
     */
    @Test
    void shouldReturnChallengeWhenAuthenticatePayerProvidesRedirectHtml() {
        log.info("MPGS统一3DS测试开始，case: 付款人认证要求ACS Challenge");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setAuthenticationStatus("AUTHENTICATION_PENDING");
        authenticate.setRedirectHtml("<form id=\"acs-challenge\"></form>");
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getPhase()).isEqualTo(ChannelThreeDsPhase.AUTHENTICATE);
        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.CHALLENGE_REQUIRED);
        assertThat(response.getRedirectHtml()).isEqualTo("<form id=\"acs-challenge\"></form>");
        verify(apiClient, never()).initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
        log.info("MPGS统一3DS测试完成，status: {}, hasRedirectHtml: true", response.getStatus());
    }

    /**
     * 验证 MPGS 明确建议停止交易时由 provider 归一化为 FAILED。
     */
    @Test
    void shouldMapDoNotProceedRecommendationToFailed() {
        log.info("MPGS统一3DS测试开始，case: 渠道明确拒绝继续交易");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse initiate = new MpgsThreeDsAuthenticationResponse();
        initiate.setAuthenticationStatus("AUTHENTICATION_AVAILABLE");
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setGatewayRecommendation("DO_NOT_PROCEED");
        authenticate.setAuthenticationStatus("AUTHENTICATION_FAILED");
        authenticate.setResponseCode("AUTHENTICATION_FAILED");
        authenticate.setResponseMessage("Authentication was rejected");
        when(apiClient.initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(initiate);
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getFailureMessage()).isEqualTo("Authentication was rejected");
        log.info("MPGS统一3DS测试完成，status: {}, failureCode: {}",
                response.getStatus(), response.getFailureCode());
    }

    /**
     * 验证 MPGS 已明确返回非 2xx 时归一化为 FAILED，不能作为未知结果永久停留在 PROCESSING。
     */
    @Test
    void shouldMapNonSuccessfulHttpStatusToFailed() {
        log.info("MPGS统一3DS测试开始，case: 渠道明确返回HTTP错误");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse initiate = mpgsResponse();
        initiate.setAuthenticationStatus(null);
        initiate.setResponseCode("INVALID_REQUEST");
        initiate.setResponseMessage("Request validation failed");
        initiate.getExtension().put("httpStatus", "400");
        when(apiClient.initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(initiate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request());

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getFailureMessage()).isEqualTo("Request validation failed");
        verify(apiClient).initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient, never()).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
        log.info("MPGS统一3DS测试完成，status: {}, failureCode: {}",
                response.getStatus(), response.getFailureCode());
    }

    /**
     * 验证 MPGS 未返回明确通过、失败或质询信号时保持 PROCESSING，等待可靠查询或回调。
     */
    @Test
    void shouldKeepUnknownAuthenticationStatusProcessing() {
        log.info("MPGS统一3DS测试开始，case: 渠道认证状态尚未确定");
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse initiate = new MpgsThreeDsAuthenticationResponse();
        initiate.setAuthenticationStatus("AUTHENTICATION_AVAILABLE");
        MpgsThreeDsAuthenticationResponse authenticate = mpgsResponse();
        authenticate.setAuthenticationStatus("AUTHENTICATION_PENDING");
        when(apiClient.initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(initiate);
        when(apiClient.authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class))).thenReturn(authenticate);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.PROCESSING);
        assertThat(response.getThreeDsStatus()).isEqualTo("AUTHENTICATION_PENDING");
        log.info("MPGS统一3DS测试完成，status: {}, threeDsStatus: {}",
                response.getStatus(), response.getThreeDsStatus());
    }

    @Test
    void shouldVerifyChallengeResultUsingRetrieveAuthenticationOnly() {
        MpgsApiClient apiClient = mock(MpgsApiClient.class);
        MpgsThreeDsAuthenticationResponse retrieved = mpgsResponse();
        retrieved.setGatewayRecommendation("PROCEED");
        retrieved.setAuthenticationStatus("AUTHENTICATION_SUCCESSFUL");
        when(apiClient.retrieveAuthentication(any(MpgsThreeDsAuthenticationRequest.class)))
                .thenReturn(retrieved);
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(apiClient);
        ChannelThreeDsAuthenticationRequest request = request();
        request.setPhase(ChannelThreeDsPhase.VERIFY);

        ChannelThreeDsAuthenticationResponse response = client.authenticateThreeDs(request);

        assertThat(response.getPhase()).isEqualTo(ChannelThreeDsPhase.VERIFY);
        assertThat(response.getStatus()).isEqualTo(ChannelThreeDsStatus.PASSED);
        verify(apiClient).retrieveAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient, never()).initiateAuthentication(any(MpgsThreeDsAuthenticationRequest.class));
        verify(apiClient, never()).authenticatePayer(any(MpgsThreeDsAuthenticationRequest.class));
    }

    private ChannelThreeDsAuthenticationRequest request() {
        ChannelThreeDsAuthenticationRequest request = new ChannelThreeDsAuthenticationRequest();
        request.setChannelCode("MPGS");
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setChannelOrderNo("TX-001");
        request.setAuthenticationTransactionId("3DSTX-001");
        request.setMerchantId("MERCHANT-001");
        request.setMerchantOrderNo("ORDER-001");
        request.setMerchantOrderId("ATTEMPT-001");
        request.setPaymentMethod("BANK_CARD");
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("01");
        request.setExpirationYear("2039");
        request.setSecurityCode("100");
        request.setCardBrand("MASTERCARD");
        request.setRedirectResponseUrl("https://checkout.example.test/3ds/return");
        request.setBrowserInfoJson("{\"javaEnabled\":false}");
        ChannelPaymentRequest.BillingInfo billingInfo = new ChannelPaymentRequest.BillingInfo();
        billingInfo.setCountry("USA");
        request.setBillingInfo(billingInfo);
        request.getExtension().put("mid.merchantId", "MID-001");
        return request;
    }

    private MpgsThreeDsAuthenticationResponse mpgsResponse() {
        MpgsThreeDsAuthenticationResponse response = new MpgsThreeDsAuthenticationResponse();
        response.setChannelCode("MPGS");
        response.setOperationId("OP-001");
        response.setTransactionId("TX-001");
        response.setChannelOrderNo("TX-001");
        response.setAuthenticationTransactionId("3DSTX-001");
        response.setThreeDsVersion("2.2.0");
        response.setThreeDsTransactionId("3DS-PROTOCOL-001");
        response.setThreeDsServerTransactionId("SERVER-001");
        response.setAcsTransactionId("ACS-001");
        response.setDsTransactionId("DS-001");
        response.setEci("05");
        response.setCavv("authentication-value");
        response.setRawResponseMasked("{\"authentication\":\"***\"}");
        response.getExtension().put("gatewayCode", "APPROVED");
        return response;
    }
}

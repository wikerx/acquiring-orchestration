package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.channel.payment.registry.PaymentChannelRegistry;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientResponseDTO;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentAuthenticationRecordService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentCheckoutThreeDsServiceTests
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 收银台统一 3DS 编排服务测试，通过公开 Channel SPI 验证路由、请求转换和挑战结果映射，不依赖具体渠道协议类型。
 * @status : create
 */
@Slf4j
class DefaultPaymentCheckoutThreeDsServiceTests {

    @Test
    void shouldPreserveCardBrandInPaymentRouteCommand() {
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        AtomicReference<PaymentCreateCommandDTO> routedCommand = new AtomicReference<>();
        PaymentChannelRouteService routeService = command -> {
            routedCommand.set(command);
            return routeResult();
        };
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), routeService, policyClient(false));

        service.authenticate(session(), attempt(), submitCommand(),
                "https://checkout.example.test/3ds/return");

        assertThat(routedCommand.get()).isNotNull();
        assertThat(routedCommand.get().getTransactionInfo()).isNotNull();
        assertThat(routedCommand.get().getTransactionInfo().getCardBrand()).isEqualTo("MASTERCARD");
    }

    @Test
    void shouldKeepBelowThresholdPaymentOnNonThreeDsPath() {
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        PaymentChannelExecutor executor = new PaymentChannelExecutor(registry);
        PaymentChannelRouteService routeService = command -> routeResult();
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                executor, routeService, policyClient(false));
        PaymentCheckoutSessionDO session = session();
        session.setLabelAmount(new BigDecimal("19.00"));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session, attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.notRequired()).isTrue();
        assertThat(result.getThreeDsPolicyAction()).isEqualTo("NONE");
        assertThat(result.getChannelMidConfigId()).isEqualTo(1001L);
        assertThat(channelClient.request).isNull();
    }

    @Test
    void shouldFailClosedWhenForcedThreeDsRouteCapabilityIsDisabled() {
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        PaymentChannelExecutor executor = new PaymentChannelExecutor(registry);
        PaymentChannelRouteService routeService = command -> {
            PaymentRouteResultDTO result = routeResult();
            result.setThreeDsSupported(false);
            return result;
        };
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                executor, routeService, policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureCode()).isEqualTo("THREE_DS_CAPABILITY_UNAVAILABLE");
        assertThat(channelClient.request).isNull();
    }

    @Test
    void shouldFailClosedWhenForcedThreeDsSpiCapabilityIsMissing() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(
                Optional.of(List.of(new NonThreeDsChannelClient())));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureCode()).isEqualTo("THREE_DS_CAPABILITY_UNAVAILABLE");
    }

    /**
     * 验证收银台 3DS 编排通过 Registry 和统一 SPI 发起初始化，并保留路由身份与 Method 结果。
     */
    @Test
    void shouldAuthenticateThroughUnifiedChannelSpi() {
        log.info("统一3DS编排测试开始，case: 路由后返回3DS Method结果");
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        PaymentChannelExecutor executor = new PaymentChannelExecutor(registry);
        PaymentChannelRouteService routeService = command -> routeResult();
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                executor, routeService, policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.getPhase()).isEqualTo("INITIALIZE");
        assertThat(result.getStatus()).isEqualTo("METHOD_REQUIRED");
        assertThat(result.getAuthenticationTransactionId()).isEqualTo("3DSTX-001");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX-001");
        assertThat(result.getChannelTransactionId()).isNull();
        assertThat(result.getChannelMidConfigId()).isEqualTo(1001L);
        assertThat(result.getRedirectHtml()).isEqualTo("<form id=\"three-ds\"></form>");

        ChannelThreeDsAuthenticationRequest request = channelClient.request;
        assertThat(request.getChannelCode()).isEqualTo("MPGS");
        assertThat(request.getPhase()).isEqualTo(ChannelThreeDsPhase.INITIALIZE);
        assertThat(request.getOperationId()).isEqualTo("OP-001");
        assertThat(request.getTransactionId()).isEqualTo("TX-001");
        assertThat(request.getAuthenticationTransactionId()).isEqualTo("3DSTX-001");
        assertThat(request.getCardNo()).isEqualTo("5123450000000008");
        assertThat(request.getSecurityCode()).isEqualTo("100");
        assertThat(request.getCardholderName()).isEqualTo("Test Buyer");
        assertThat(request.getPayerIp()).isEqualTo("203.0.113.9");
        assertThat(request.getRedirectResponseUrl()).isEqualTo("https://checkout.example.test/3ds/return");
        assertThat(request.getExtension())
                .containsEntry("requestUrl", "https://gateway.example.test/api/rest")
                .containsEntry("connectTimeoutSeconds", "3")
                .containsEntry("readTimeoutSeconds", "8")
                .containsEntry("midNo", "MID-001")
                .containsEntry("mid.merchantId", "MID-001");
        log.info("统一3DS编排测试完成，status: {}, channelCode: {}", result.getStatus(), request.getChannelCode());
    }

    @Test
    void shouldUsePlatformGatewayForOptionalMpgsNotificationWithoutMerchantUrls() {
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        SystemConfigReadService configReadService = mock(SystemConfigReadService.class);
        when(configReadService.findEnabledValue("platform.gateway.base-url"))
                .thenReturn(Optional.of("https://218258jc58.goho.co/"));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true), configReadService);
        PaymentCheckoutSessionDO session = session();
        session.setMerchantNotifyUrl("https://merchant.example/notify");

        service.authenticate(session, attempt(), submitCommand(),
                "https://21872i5858.imdo.co/checkout/api/v1/3ds/bridge");

        assertThat(channelClient.request.getNotificationUrl())
                .isEqualTo("https://218258jc58.goho.co/channel/v1/callbacks/MPGS/3ds");
        assertThat(channelClient.request.getRedirectResponseUrl())
                .isEqualTo("https://21872i5858.imdo.co/checkout/api/v1/3ds/bridge");
        assertThat(channelClient.request.toString())
                .doesNotContain("merchant.example.com", "merchant-notify-url-ciphertext");
    }

    @Test
    void shouldContinueMpgsThreeDsWhenOptionalNotificationUrlIsNotConfigured() {
        CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        SystemConfigReadService configReadService = mock(SystemConfigReadService.class);
        when(configReadService.findEnabledValue("platform.gateway.base-url")).thenReturn(Optional.empty());
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true), configReadService);

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.getStatus()).isEqualTo("METHOD_REQUIRED");
        assertThat(channelClient.request.getNotificationUrl()).isNull();
    }

    @Test
    void shouldIgnoreUnsafeOptionalNotificationOriginsWithoutBlockingThreeDs() {
        for (String configured : List.of(
                "http://gateway.example.test",
                "https://user:password@gateway.example.test",
                "https://gateway.example.test?source=merchant",
                "https://gateway.example.test#fragment",
                "https://gateway.example.test/unexpected/path")) {
            CapturingThreeDsChannelClient channelClient = new CapturingThreeDsChannelClient();
            PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
            SystemConfigReadService configReadService = mock(SystemConfigReadService.class);
            when(configReadService.findEnabledValue("platform.gateway.base-url"))
                    .thenReturn(Optional.of(configured));
            DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                    new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true), configReadService);

            PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                    session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

            assertThat(result.getStatus()).isEqualTo("METHOD_REQUIRED");
            assertThat(channelClient.request).isNotNull();
            assertThat(channelClient.request.getNotificationUrl()).isNull();
        }
    }

    @Test
    void shouldAuthenticatePayerImmediatelyWhenMethodIsNotRequired() {
        ReadyThenPassedThreeDsChannelClient channelClient = new ReadyThenPassedThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        PaymentAuthenticationRecordService authenticationRecordService =
                mock(PaymentAuthenticationRecordService.class);
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true),
                null, authenticationRecordService);

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.passed()).isTrue();
        assertThat(result.getPhase()).isEqualTo("AUTHENTICATE");
        assertThat(channelClient.phases)
                .containsExactly(ChannelThreeDsPhase.INITIALIZE, ChannelThreeDsPhase.AUTHENTICATE);
        verify(authenticationRecordService).recordChannelResult(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(response ->
                        ChannelThreeDsPhase.INITIALIZE.equals(response.getPhase())
                                && ChannelThreeDsStatus.READY_TO_AUTHENTICATE.equals(response.getStatus())));
        verify(authenticationRecordService).recordChannelResult(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(response ->
                        ChannelThreeDsPhase.AUTHENTICATE.equals(response.getPhase())
                                && ChannelThreeDsStatus.PASSED.equals(response.getStatus())));
    }

    /**
     * 验证渠道超时时平台保持 PROCESSING，并保留后续查询所需的认证号和 MID 身份。
     */
    @Test
    void shouldKeepChannelTimeoutProcessing() {
        log.info("统一3DS编排测试开始，case: 渠道超时结果未知");
        PaymentChannelRegistry registry = new PaymentChannelRegistry(
                Optional.of(List.of(new TimeoutThreeDsChannelClient())));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        assertThat(result.getAuthenticationTransactionId()).isEqualTo("3DSTX-001");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX-001");
        assertThat(result.getChannelTransactionId()).isNull();
        assertThat(result.getChannelMidConfigId()).isEqualTo(1001L);
        assertThat(result.getFailureCode()).isEqualTo("ChannelTimeoutException");
        log.info("统一3DS编排测试完成，status: {}, failureCode: {}",
                result.getStatus(), result.getFailureCode());
    }

    @Test
    void shouldFailWhenChannelRequestIsRejectedBeforeSending() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(
                Optional.of(List.of(new InvalidRequestThreeDsChannelClient())));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureCode()).isEqualTo("ChannelRequestException");
    }

    @Test
    void shouldFailWhenChannelReturnsCertainHttpError() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(
                Optional.of(List.of(new RejectedResponseThreeDsChannelClient())));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), command -> routeResult(), policyClient(true));

        PaymentCheckoutThreeDsResultDTO result = service.authenticate(
                session(), attempt(), submitCommand(), "https://checkout.example.test/3ds/return");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureCode()).isEqualTo("ChannelResponseException");
    }

    @Test
    void shouldRestoreOriginalMidAndVerifyChallengeServerSide() {
        VerifyingThreeDsChannelClient channelClient = new VerifyingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        CapturingRestoreRouteService routeService = new CapturingRestoreRouteService();
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), routeService, policyClient(true));
        PaymentCheckoutAttemptDO attempt = attempt();
        attempt.setChannelCode("MPGS");
        attempt.setChannelMidConfigId(1001L);

        PaymentCheckoutThreeDsResultDTO result = service.continueAuthentication(
                session(), attempt, submitCommand(), "https://checkout.example.test/3ds/return",
                ChannelThreeDsPhase.VERIFY);

        assertThat(result.passed()).isTrue();
        assertThat(channelClient.phase).isEqualTo(ChannelThreeDsPhase.VERIFY);
        assertThat(routeService.routeCalls).isZero();
        assertThat(routeService.restoreCalls).isEqualTo(1);
        assertThat(result.getChannelMidConfigId()).isEqualTo(1001L);
    }

    @Test
    void shouldReuseAuthenticationIdentityAcrossAllThreeDsPhasesWithoutFundsTransactionId() {
        IdentityCapturingThreeDsChannelClient channelClient = new IdentityCapturingThreeDsChannelClient();
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(channelClient)));
        DefaultPaymentCheckoutThreeDsService service = new DefaultPaymentCheckoutThreeDsService(
                new PaymentChannelExecutor(registry), new CapturingRestoreRouteService(), policyClient(true));
        PaymentCheckoutAttemptDO attempt = attempt();
        attempt.setChannelOrderNo("MPGS-ORDER-001");
        attempt.setChannelTransactionId("FUNDS-TX-001");
        attempt.setChannelCode("MPGS");
        attempt.setChannelMidConfigId(1001L);

        PaymentCheckoutThreeDsResultDTO authenticated = service.authenticate(
                session(), attempt, submitCommand(), "https://checkout.example.test/3ds/return", routeResult());
        PaymentCheckoutThreeDsResultDTO verified = service.continueAuthentication(
                session(), attempt, submitCommand(), "https://checkout.example.test/3ds/return",
                ChannelThreeDsPhase.VERIFY);

        assertThat(authenticated.passed()).isTrue();
        assertThat(verified.passed()).isTrue();
        assertThat(channelClient.requests)
                .extracting(ThreeDsRequestIdentity::phase)
                .containsExactly(ChannelThreeDsPhase.INITIALIZE, ChannelThreeDsPhase.AUTHENTICATE,
                        ChannelThreeDsPhase.VERIFY);
        assertThat(channelClient.requests)
                .allSatisfy(identity -> {
                    assertThat(identity.channelOrderNo()).isEqualTo("MPGS-ORDER-001");
                    assertThat(identity.authenticationTransactionId()).isEqualTo("3DSTX-001");
                    assertThat(identity.channelTransactionId()).isNull();
                });
    }

    private PaymentCheckoutSessionDO session() {
        PaymentCheckoutSessionDO session = new PaymentCheckoutSessionDO();
        session.setMerchantId("MERCHANT-001");
        session.setMerchantOrderNo("ORDER-001");
        session.setPaymentAction("PAYMENT");
        session.setLabelAmount(new BigDecimal("10.25"));
        session.setLabelCurrency("USD");
        return session;
    }

    private PaymentCheckoutAttemptDO attempt() {
        PaymentCheckoutAttemptDO attempt = new PaymentCheckoutAttemptDO();
        attempt.setAttemptRequestId("ATTEMPT-001");
        attempt.setOperationId("OP-001");
        attempt.setTransactionId("TX-001");
        attempt.setTransactionDateTime(LocalDateTime.of(2026, 8, 11, 10, 30));
        attempt.setPaymentMethod("BANK_CARD");
        attempt.setPaymentBrand("MASTERCARD");
        attempt.setThreeDsTransactionId("3DSTX-001");
        return attempt;
    }

    private PaymentCheckoutPaymentSubmitCommandDTO submitCommand() {
        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO card = new PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO();
        card.setCardNo("5123450000000008");
        card.setExpirationMonth("01");
        card.setExpirationYear("2039");
        card.setSecurityCode("100");
        card.setCardholderName("Test Buyer");
        PaymentCheckoutPaymentSubmitCommandDTO command = new PaymentCheckoutPaymentSubmitCommandDTO();
        command.setCardInfo(card);
        command.setPayerIp("203.0.113.9");
        command.setBrowserInfoJson("{\"javaEnabled\":false}");
        return command;
    }

    private PaymentRouteResultDTO routeResult() {
        PaymentRouteResultDTO result = PaymentRouteResultDTO.routed("MPGS");
        result.setChannelId(101L);
        result.setMidConfigId(1001L);
        result.setMidNo("MID-001");
        result.setThreeDsSupported(true);
        result.setRequestUrl("https://gateway.example.test/api/rest");
        result.setConnectTimeoutSeconds(3);
        result.setReadTimeoutSeconds(8);
        result.getMetadataValues().put("merchantId", "MID-001");
        return result;
    }

    private RiskInternalClient policyClient(boolean required) {
        return new RiskInternalClient() {
            @Override
            public RiskPaymentEvaluateClientResponseDTO evaluatePayment(
                    RiskPaymentEvaluateClientRequestDTO requestDTO) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RiskThreeDsPolicyClientResponseDTO evaluateThreeDsPolicy(
                    RiskThreeDsPolicyClientRequestDTO requestDTO) {
                RiskThreeDsPolicyClientResponseDTO result = new RiskThreeDsPolicyClientResponseDTO();
                result.setRequired(required);
                result.setAction(required ? "FORCE_3DS" : "NONE");
                result.setRuleId(required ? 30L : null);
                return result;
            }
        };
    }

    private static class CapturingThreeDsChannelClient implements PaymentChannelClient {

        private ChannelThreeDsAuthenticationRequest request;

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            this.request = request;
            ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
            response.setPhase(ChannelThreeDsPhase.INITIALIZE);
            response.setStatus(ChannelThreeDsStatus.METHOD_REQUIRED);
            response.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            response.setChannelOrderNo(request.getChannelOrderNo());
            response.setChannelTransactionId(request.getAuthenticationTransactionId());
            response.setThreeDsStatus("AUTHENTICATION_PENDING");
            response.setRedirectHtml("<form id=\"three-ds\"></form>");
            return response;
        }
    }

    private static class TimeoutThreeDsChannelClient implements PaymentChannelClient {

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            throw new ChannelTimeoutException("MPGS 3DS request timed out");
        }
    }

    private static class InvalidRequestThreeDsChannelClient implements PaymentChannelClient {

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            throw new ChannelRequestException("MPGS 3DS browser details are incomplete");
        }
    }

    private static class RejectedResponseThreeDsChannelClient implements PaymentChannelClient {

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            throw new ChannelResponseException(
                    "MPGS 3DS HTTP response is not successful, status: 400", null, false);
        }
    }

    private static class ReadyThenPassedThreeDsChannelClient implements PaymentChannelClient {

        private final List<ChannelThreeDsPhase> phases = new ArrayList<>();

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            phases.add(request.getPhase());
            ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
            response.setPhase(request.getPhase());
            response.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            response.setChannelOrderNo(request.getChannelOrderNo());
            response.setChannelTransactionId(request.getAuthenticationTransactionId());
            response.setStatus(ChannelThreeDsPhase.INITIALIZE.equals(request.getPhase())
                    ? ChannelThreeDsStatus.READY_TO_AUTHENTICATE
                    : ChannelThreeDsStatus.PASSED);
            return response;
        }
    }

    private static class NonThreeDsChannelClient implements PaymentChannelClient {

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.PAYMENT);
        }
    }

    private static class VerifyingThreeDsChannelClient implements PaymentChannelClient {
        private ChannelThreeDsPhase phase;

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            phase = request.getPhase();
            ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
            response.setPhase(request.getPhase());
            response.setStatus(ChannelThreeDsStatus.PASSED);
            response.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            response.setChannelOrderNo(request.getChannelOrderNo());
            return response;
        }
    }

    private static class IdentityCapturingThreeDsChannelClient implements PaymentChannelClient {
        private final List<ThreeDsRequestIdentity> requests = new ArrayList<>();

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.THREE_DS_AUTHENTICATION);
        }

        @Override
        public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
            requests.add(new ThreeDsRequestIdentity(request.getPhase(), request.getChannelOrderNo(),
                    request.getAuthenticationTransactionId(), request.getChannelTransactionId()));
            ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
            response.setPhase(request.getPhase());
            response.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            response.setChannelOrderNo(request.getChannelOrderNo());
            response.setStatus(ChannelThreeDsPhase.INITIALIZE.equals(request.getPhase())
                    ? ChannelThreeDsStatus.READY_TO_AUTHENTICATE : ChannelThreeDsStatus.PASSED);
            return response;
        }
    }

    private record ThreeDsRequestIdentity(ChannelThreeDsPhase phase,
                                          String channelOrderNo,
                                          String authenticationTransactionId,
                                          String channelTransactionId) {
    }

    private class CapturingRestoreRouteService implements PaymentChannelRouteService {
        private int routeCalls;
        private int restoreCalls;

        @Override
        public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
            routeCalls++;
            throw new AssertionError("3DS continuation must not reroute");
        }

        @Override
        public PaymentRouteResultDTO restore(String channelCode, Long channelId, Long midConfigId, String fallbackMidNo) {
            restoreCalls++;
            PaymentRouteResultDTO result = routeResult();
            result.setChannelCode(channelCode);
            result.setChannelId(channelId == null ? 101L : channelId);
            result.setMidConfigId(midConfigId);
            return result;
        }
    }
}

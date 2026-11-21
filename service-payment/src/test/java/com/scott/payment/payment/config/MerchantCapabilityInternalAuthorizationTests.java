package com.scott.payment.payment.config;

import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import com.scott.payment.component.web.internal.InternalServiceRequestBodyFilter;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 用真实签名拦截器验证配置模板的诊断接口授权，避免新增调用只在独立启动配置中可用。 */
class MerchantCapabilityInternalAuthorizationTests {
    private static final String EVALUATION_PATH = "/internal/payment/merchant-capabilities/evaluate";
    private static final String TEST_SECRET = "authorization-contract-fixture-".repeat(2);

    @Test
    void authenticatedAdminCanEvaluateWithThePublishedServiceConfiguration() throws Exception {
        var properties = properties();
        var interceptor = new InternalServiceAuthInterceptor(properties, (caller, nonce, ttl) -> true);
        var request = signedRequest("service-admin", UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object()))
                .as("valid Admin request must not be rejected by a missing exact path: %s", response.getContentAsString())
                .isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalServiceAuthInterceptor.AUTHENTICATED_CALLER_ATTRIBUTE))
                .isEqualTo("service-admin");
        assertThat(properties.isPathAllowed("service-admin", "/internal/payment/merchant-capabilities/unregistered"))
                .isFalse();
    }

    @Test
    void otherCallersCannotUseTheAdminDiagnosticEndpointEvenWithValidSignatures() throws Exception {
        var interceptor = new InternalServiceAuthInterceptor(properties(), (caller, nonce, ttl) -> true);
        for (String caller : List.of("service-merchant", "service-openapi", "service-job")) {
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(signedRequest(caller, UUID.randomUUID().toString()), response, new Object()))
                    .as(caller).isFalse();
            assertThat(response.getStatus()).as(caller).isEqualTo(403);
        }
    }

    @Test
    void missingSignaturesAndRepeatedNoncesRemainRejected() throws Exception {
        Set<String> nonces = new HashSet<>();
        var interceptor = new InternalServiceAuthInterceptor(properties(),
                (caller, nonce, ttl) -> nonces.add(caller + ':' + nonce));
        var missing = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", EVALUATION_PATH), missing, new Object()))
                .isFalse();
        assertThat(missing.getStatus()).isEqualTo(401);
        String nonce = UUID.randomUUID().toString();
        assertThat(interceptor.preHandle(signedRequest("service-admin", nonce), new MockHttpServletResponse(), new Object()))
                .isTrue();
        var replay = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(signedRequest("service-admin", nonce), replay, new Object())).isFalse();
        assertThat(replay.getStatus()).isEqualTo(401);
    }

    private InternalServiceAuthProperties properties() throws Exception {
        Path path = Path.of("docs/deployment/nacos/service-payment-dev.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..").resolve(path);
        }
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("published-payment-config", new FileSystemResource(path)).get(0);
        var properties = new InternalServiceAuthProperties();
        for (String caller : List.of("service-admin", "service-merchant", "service-openapi", "service-job")) {
            List<String> allowedPaths = new ArrayList<>();
            for (int i = 0; ; i++) {
                Object value = source.getProperty("internal-service.auth.callers." + caller + ".allowed-paths[" + i + "]");
                if (value == null) {
                    break;
                }
                allowedPaths.add(value.toString());
            }
            assertThat(allowedPaths).as("configured path boundary for %s", caller).isNotEmpty();
            var credential = new InternalServiceAuthProperties.CallerCredential();
            credential.setActiveSecret(TEST_SECRET);
            credential.setAllowedPaths(allowedPaths);
            properties.getCallers().put(caller, credential);
        }
        properties.validate();
        return properties;
    }

    private MockHttpServletRequest signedRequest(String caller, String nonce) {
        var request = new MockHttpServletRequest("POST", EVALUATION_PATH);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);
        String digest = InternalServiceSignature.payloadSha256(body);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        request.setAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE, digest);
        request.addHeader(InternalServiceSignature.HEADER_CALLER, caller);
        request.addHeader(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(InternalServiceSignature.HEADER_NONCE, nonce);
        request.addHeader(InternalServiceSignature.HEADER_SIGNATURE,
                InternalServiceSignature.sign("POST", EVALUATION_PATH, timestamp, nonce, caller, digest, TEST_SECRET));
        return request;
    }
}

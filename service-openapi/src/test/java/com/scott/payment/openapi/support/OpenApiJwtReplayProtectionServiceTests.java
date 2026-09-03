package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiJwtReplayProtectionServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : OpenAPI JWT 防重放 Redis 行为测试。
 * @status : create
 */
class OpenApiJwtReplayProtectionServiceTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReplayProtectionTestConfiguration.class);

    @Test
    void shouldFailStartupWhenReplayIsRequiredAndRedisTemplateIsMissing() {
        contextRunner
                .withPropertyValues("openapi.security.replay.required=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("OpenAPI JWT replay protection requires StringRedisTemplate");
                });
    }

    @Test
    void shouldStartWhenReplayIsOptionalAndRedisTemplateIsMissing() {
        contextRunner
                .withPropertyValues("openapi.security.replay.required=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenApiJwtReplayProtectionService.class);
                });
    }

    @Test
    void shouldRejectInvalidClaimsWhenOptionalRedisTemplateIsMissing() {
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(null),
                false,
                new PaymentRedisProperties()
        );

        assertThatThrownBy(() -> service.checkAndMark(
                " ",
                "jwt-id",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.AUTHORIZATION_JWT_INVALID.getCode()));
        assertThatThrownBy(() -> service.checkAndMark(
                "M202607290001",
                null,
                Instant.now().plusSeconds(300).getEpochSecond()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.AUTHORIZATION_JWT_INVALID.getCode()));
    }

    @Test
    void shouldUseEnvironmentPrefixAndJtiDigestWithoutConstantMarkerValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                true,
                redisProperties);
        String merchantId = "M202607290001";
        String jwtId = "merchant-order-raw-jti-001";

        service.checkAndMark(merchantId, jwtId, Instant.now().plusSeconds(300).getEpochSecond());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).setIfAbsent(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue())
                .isEqualTo("acquiring:test:security:openapi:jwt-replay:"
                        + merchantId + ":" + RedisKeyDigest.sha256(jwtId))
                .doesNotContain(jwtId);
        assertThat(valueCaptor.getValue())
                .matches("\\d{13}")
                .isNotEqualTo("1");
        assertThat(ttlCaptor.getValue()).isPositive();
    }

    @Test
    void shouldRejectDuplicatedJtiWithinReplayWindow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(Boolean.FALSE);
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                true,
                new PaymentRedisProperties());

        assertThatThrownBy(() -> service.checkAndMark(
                "M202607290001",
                "duplicated-jti",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.AUTHORIZATION_JWT_INVALID.getCode()));
    }

    @Test
    void shouldFailClosedWhenRedisConnectionFailsAndReplayIsRequired() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                true,
                new PaymentRedisProperties()
        );

        assertThatThrownBy(() -> service.checkAndMark(
                "M202607290001",
                "connection-failure-jti",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldFailClosedWhenRedisWriteResultIsUnknownAndReplayIsRequired() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(null);
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                true,
                new PaymentRedisProperties()
        );

        assertThatThrownBy(() -> service.checkAndMark(
                "M202607290001",
                "unknown-write-result-jti",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldFailOpenWhenRedisConnectionFailsAndReplayIsOptional() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                false,
                new PaymentRedisProperties()
        );

        assertThatCode(() -> service.checkAndMark(
                "M202607290001",
                "optional-connection-failure-jti",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailOpenWhenRedisWriteResultIsUnknownAndReplayIsOptional() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(null);
        OpenApiJwtReplayProtectionService service = new OpenApiJwtReplayProtectionService(
                provider(redisTemplate),
                false,
                new PaymentRedisProperties()
        );

        assertThatCode(() -> service.checkAndMark(
                "M202607290001",
                "optional-unknown-write-result-jti",
                Instant.now().plusSeconds(300).getEpochSecond()))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        return provider;
    }

    @Configuration(proxyBeanMethods = false)
    static class ReplayProtectionTestConfiguration {

        @Bean
        PaymentRedisProperties paymentRedisProperties() {
            return new PaymentRedisProperties();
        }

        @Bean
        OpenApiJwtReplayProtectionService replayProtectionService(
                ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                @Value("${openapi.security.replay.required:false}") boolean replayRequired,
                PaymentRedisProperties redisProperties) {
            return new OpenApiJwtReplayProtectionService(
                    stringRedisTemplateProvider,
                    replayRequired,
                    redisProperties
            );
        }
    }
}

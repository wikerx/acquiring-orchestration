package com.scott.payment.merchant.client.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 商户受控用例的 Admin 权威边界。只给固定目标和已知路径签名。 */
@Component
public class MerchantPaymentGovernanceClient {
    private static final String CALLER = "service-merchant";
    private static final String SETTINGS = "merchant.admin-payment-method-client.";
    private static final Set<String> MERCHANT_ERRORS = Set.of(ApiResultEnum.FORBIDDEN.getCode(),
            ApiResultEnum.PARAM_INVALID.getCode(), ApiResultEnum.ORDER_NOT_FOUND.getCode(),
            ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode());

    private final RestTemplate discovery;
    private final RestTemplate direct;
    private final ObjectMapper json;
    private final Environment environment;

    public MerchantPaymentGovernanceClient(
            @Qualifier("merchantPaymentInternalLoadBalancedRestTemplate") RestTemplate discovery,
            @Qualifier("merchantPaymentInternalRestTemplate") RestTemplate direct,
            ObjectMapper json, Environment environment) {
        this.discovery = discovery;
        this.direct = direct;
        this.json = json;
        this.environment = environment;
    }

    public PageResult<MethodRow> methods(String merchant, Identity actor, Search query) {
        return call(merchant, "/search", new SignedSearch(actor, query), new TypeReference<>() {});
    }

    public AccessSummary accessSummary(String merchant, Identity actor) {
        return call(merchant, "/access-summary", new SignedIdentity(actor), new TypeReference<>() {});
    }

    public FeeAuthorizationProjection feeCoverage(String merchant, Identity actor) {
        return call(merchant, "/fee-coverage", new SignedIdentity(actor), new TypeReference<>() {});
    }

    public MethodRow method(String merchant, Identity actor, String item) {
        return call(merchant, "/method/" + segment(item), new SignedIdentity(actor), new TypeReference<>() {});
    }

    public BatchReceipt apply(String merchant, Identity actor, Apply command) {
        return call(merchant, "/applications", new SignedApply(actor, command), new TypeReference<>() {});
    }

    public PageResult<ApplicationRow> applications(String merchant, Identity actor, Search query) {
        return call(merchant, "/applications/search", new SignedSearch(actor, query), new TypeReference<>() {});
    }

    public ApplicationRow application(String merchant, Identity actor, String application) {
        return call(merchant, "/applications/" + segment(application), new SignedIdentity(actor), new TypeReference<>() {});
    }

    public ItemReceipt change(String merchant, Identity actor, long item, Change command, boolean resubmit) {
        return call(merchant, "/items/" + item + (resubmit ? "/resubmit" : "/withdraw"),
                new SignedChange(actor, command), new TypeReference<>() {});
    }

    private <T> T call(String merchant, String path, Object request, TypeReference<CommonResult<T>> type) {
        String secret = environment.getProperty(SETTINGS + "internal-secret");
        if (secret == null || secret.length() < InternalServiceAuthProperties.MIN_SECRET_LENGTH) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
        URI uri = target(merchant, path);
        try {
            byte[] body = json.writeValueAsBytes(request);
            HttpHeaders headers = headers(uri, body, secret);
            RestTemplate client = "service-admin".equals(uri.getHost()) ? discovery : direct;
            byte[] response = client.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class).getBody();
            return unwrap(response, type);
        } catch (HttpStatusCodeException exception) {
            return unwrap(exception.getResponseBodyAsByteArray(), type);
        } catch (JsonProcessingException | RestClientException exception) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
    }

    private <T> T unwrap(byte[] response, TypeReference<CommonResult<T>> type) {
        if (response == null || response.length == 0) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
        try {
            CommonResult<T> result = json.readValue(new String(response, StandardCharsets.UTF_8), type);
            if (CommonResult.isSuccess(result) && result.getData() != null) {
                return result.getData();
            }
            if (result != null && result.getCode() != null && MERCHANT_ERRORS.contains(result.getCode())) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
    }

    private URI target(String merchant, String path) {
        String address = environment.getProperty(SETTINGS + "base-url", "http://service-admin");
        try {
            URI base = URI.create(address);
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !Set.of("http", "https").contains(base.getScheme())) {
                throw new IllegalArgumentException("invalid admin owner address");
            }
            return UriComponentsBuilder.fromUri(base)
                    .path("/internal/merchant/" + segment(merchant) + "/payment-methods" + path).build(true).toUri();
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
    }

    private static String segment(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_.:-]{1,64}") || ".".equals(raw) || "..".equals(raw)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return raw;
    }

    private static HttpHeaders headers(URI uri, byte[] body, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAcceptLanguageAsLocales(List.of(LocaleContextHolder.getLocale()));
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        headers.set(InternalServiceSignature.HEADER_CALLER, CALLER);
        headers.set(InternalServiceSignature.HEADER_TIMESTAMP, Long.toString(timestamp));
        headers.set(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.set(InternalServiceSignature.HEADER_SIGNATURE, InternalServiceSignature.sign(HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()), timestamp, nonce, CALLER,
                InternalServiceSignature.payloadSha256(body), secret));
        return headers;
    }

    public record Identity(long accountId, long userId) {}
    private record SignedIdentity(Identity actor) {}
    private record SignedSearch(Identity actor, Search query) {}
    private record SignedApply(Identity actor, Apply command) {}
    private record SignedChange(Identity actor, Change command) {}
}

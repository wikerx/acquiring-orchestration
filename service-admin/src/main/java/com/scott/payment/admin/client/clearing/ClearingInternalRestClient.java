package com.scott.payment.admin.client.clearing;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.ClearingInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalRecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin 清分内部 REST 协议适配器，统一 HMAC、防重放、直连/服务发现选择和 CommonResult 解包，不承担 RBAC 或清分状态机。
 */
@Service
@Slf4j
public class ClearingInternalRestClient implements ClearingInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final String ROOT = "/internal/clearing/v1";
    private static final String TRANSACTIONS = ROOT + "/transactions";
    private static final String RESERVE_ADJUSTMENTS = ROOT + "/reserve-adjustments";
    private static final String TIER_PERIOD_REPLAYS = ROOT + "/tier-period-replays";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final ClearingInternalClientProperties properties;

    /**
     * 创建清分服务内部 REST 客户端。
     */
    public ClearingInternalRestClient(
            @Qualifier("adminClearingInternalRestTemplate") RestTemplate direct,
            @Qualifier("adminClearingInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            ClearingInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 调用清分内部接口，按管理端条件分页查询清分交易。
     */
    @Override
    public SearchResponse search(SearchRequest request) {
        return post(TRANSACTIONS + "/search", request,
                new TypeReference<CommonResult<SearchResponse>>() { });
    }

    /**
     * 调用清分内部接口，查询指定交易及其清分明细。
     */
    @Override
    public DetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl() + TRANSACTIONS + "/" + transactionId)
                .queryParam("transactionDateTime", transactionDateTime).build().encode().toUri();
        return exchange(uri, HttpMethod.GET, null,
                new TypeReference<CommonResult<DetailResponse>>() { });
    }

    /**
     * 调用清分内部接口，重试处于可恢复状态的清分交易。
     */
    @Override
    public CommandResponse retry(String transactionId, InternalActionRequest request) {
        return post(TRANSACTIONS + "/" + transactionId + "/retry", request,
                new TypeReference<CommonResult<CommandResponse>>() { });
    }

    /**
     * 调用清分内部接口，提交清分交易人工复核结果。
     */
    @Override
    public CommandResponse review(String transactionId, InternalActionRequest request) {
        return post(TRANSACTIONS + "/" + transactionId + "/review", request,
                new TypeReference<CommonResult<CommandResponse>>() { });
    }

    /**
     * 调用清分内部接口，按指定费用版本重新计算清分交易。
     */
    @Override
    public CommandResponse recalculate(String transactionId, InternalRecalculateRequest request) {
        return post(TRANSACTIONS + "/" + transactionId + "/recalculate", request,
                new TypeReference<CommonResult<CommandResponse>>() { });
    }

    /**
     * 调用清分内部接口，提交保证金差额调整申请。
     */
    @Override
    public ReserveAdjustmentResponse submitReserveAdjustment(InternalReserveAdjustmentSubmitRequest request) {
        return post(RESERVE_ADJUSTMENTS, request,
                new TypeReference<CommonResult<ReserveAdjustmentResponse>>() { });
    }

    /**
     * 调用清分内部接口，复核指定保证金调整申请。
     */
    @Override
    public ReserveAdjustmentResponse reviewReserveAdjustment(
            String adjustmentNo, InternalReserveAdjustmentReviewRequest request) {
        return post(RESERVE_ADJUSTMENTS + "/" + adjustmentNo + "/review", request,
                new TypeReference<CommonResult<ReserveAdjustmentResponse>>() { });
    }

    /**
     * 调用清分内部接口，提交阶梯费用期间重放申请。
     */
    @Override
    public TierPeriodReplayResponse submitTierPeriodReplay(InternalTierPeriodReplaySubmitRequest request) {
        return post(TIER_PERIOD_REPLAYS, request,
                new TypeReference<CommonResult<TierPeriodReplayResponse>>() { });
    }

    /**
     * 调用清分内部接口，复核指定阶梯费用期间重放申请。
     */
    @Override
    public TierPeriodReplayResponse reviewTierPeriodReplay(
            String replayNo, InternalTierPeriodReplayReviewRequest request) {
        return post(TIER_PERIOD_REPLAYS + "/" + replayNo + "/review", request,
                new TypeReference<CommonResult<TierPeriodReplayResponse>>() { });
    }

    /**
     * 通过内部鉴权签名发送清分管理命令，并统一解包业务响应；浏览器不能直接调用该边界。
     *
     * @param path 清分内部命令路径
     * @param request 已注入可信操作人和并发前置条件的命令
     * @param type 业务响应泛型
     * @param <T> 清分命令结果类型
     * @return 清分服务返回的命令结果
     * @throws ServiceException 内部鉴权、网络或清分业务响应失败时抛出
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> type) {
        return exchange(URI.create(baseUrl() + path), HttpMethod.POST, request, type);
    }

    /**
     * 执行已签名内部调用并保留清分业务错误码；网络和 HTTP 协议异常统一映射为网关错误。
     */
    private <T> T exchange(URI uri, HttpMethod method, Object request,
                           TypeReference<CommonResult<T>> type) {
        try {
            String body = choose(uri).exchange(uri, method, signed(uri, method, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, type);
            if (result == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-clearing response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("清分内部接口请求被拒绝, event: ADMIN_CLEARING_INTERNAL_HTTP_REJECTED, method: {}, path: {}, status: {}",
                    method.name(), uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing management call rejected");
        } catch (RestClientException exception) {
            log.warn("清分内部接口调用失败, event: ADMIN_CLEARING_INTERNAL_CALL_FAILED, method: {}, path: {}, exceptionType: {}",
                    method.name(), uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing management call failed");
        }
    }

    /**
     * 使用 method、原始 path/query、时间戳、一次性随机数、调用方和 payload SHA-256 生成内部签名。
     */
    private HttpEntity<String> signed(URI uri, HttpMethod method, Object request) {
        String body = request == null ? null : JsonUtils.toJsonString(request);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(
                method.name(), InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        if (request != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        headers.add(InternalServiceSignature.HEADER_CALLER, properties.getInternalCaller());
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    /**
     * IP、localhost 和完整域名走直连，逻辑服务名走负载均衡客户端。
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-clearing host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    /**
     * 解析内部服务调用的基础地址。
     */
    private String baseUrl() {
        String value = properties.getBaseUrl();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("admin clearing-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

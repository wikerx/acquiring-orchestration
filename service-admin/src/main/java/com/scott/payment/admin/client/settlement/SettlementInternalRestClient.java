package com.scott.payment.admin.client.settlement;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.SettlementInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalRestClient
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算 HMAC REST 客户端；签名覆盖方法、路径、查询、时间戳、nonce、caller 和请求体摘要。
 * @status : create
 */
@Service
@Slf4j
public class SettlementInternalRestClient implements SettlementInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    /**
     * {@code BATCHES}常量，统一 {@code SettlementInternalRestClient} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String BATCHES = "/internal/settlement/v1/batches";
    /**
     * {@code REVIEWS}常量，统一 {@code SettlementInternalRestClient} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String REVIEWS = "/internal/settlement/v1/reviews";
    /**
     * {@code REVERSALS}常量，统一 {@code SettlementInternalRestClient} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String REVERSALS = "/internal/settlement/v1/reversal-orders";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final SettlementInternalClientProperties properties;

    public SettlementInternalRestClient(
            @Qualifier("adminSettlementInternalRestTemplate") RestTemplate direct,
            @Qualifier("adminSettlementInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            SettlementInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    public BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request) {
        return post(BATCHES + "/" + settlementBatchNo + "/cancel", request,
                new TypeReference<CommonResult<BatchCommandResponse>>() { });
    }

    /** {@inheritDoc} */
    @Override
    public ReviewCommandResponse submitReview(InternalReviewSubmitRequest request) {
        return post(REVIEWS, request,
                new TypeReference<CommonResult<ReviewCommandResponse>>() { });
    }

    /** {@inheritDoc} */
    @Override
    public ReviewCommandResponse decideReview(String reviewOrderNo,
                                              InternalReviewDecisionRequest request) {
        return post(REVIEWS + "/" + reviewOrderNo + "/decisions", request,
                new TypeReference<CommonResult<ReviewCommandResponse>>() { });
    }

    /** {@inheritDoc} */
    @Override
    public ReversalCommandResponse submitReversal(InternalReversalSubmitRequest request) {
        return post(REVERSALS, request,
                new TypeReference<CommonResult<ReversalCommandResponse>>() { });
    }

    /** {@inheritDoc} */
    @Override
    public ReversalCommandResponse decideReversal(String reversalOrderNo,
                                                  InternalReversalDecisionRequest request) {
        return post(REVERSALS + "/" + reversalOrderNo + "/decisions", request,
                new TypeReference<CommonResult<ReversalCommandResponse>>() { });
    }

    /**
     * 通过内部鉴权签名发送结算领域命令，确保 Admin 查询与结算资金状态变更边界保持隔离。
     *
     * @param path 结算内部命令路径
     * @param request 已注入可信操作人、权限快照和幂等键的命令
     * @param type 业务响应泛型
     * @param <T> 结算命令结果类型
     * @return 结算服务返回的命令结果
     * @throws ServiceException 内部鉴权、网络或结算业务响应失败时抛出
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> type) {
        return exchange(URI.create(baseUrl() + path), HttpMethod.POST, request, type);
    }

    /** 对内部请求签名并统一解包 CommonResult，远端异常不向浏览器暴露服务内部正文。 */
    private <T> T exchange(URI uri, HttpMethod method, Object request,
                           TypeReference<CommonResult<T>> type) {
        try {
            String body = choose(uri).exchange(
                    uri, method, signed(uri, method, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, type);
            if (result == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-settlement response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                log.warn("service-settlement internal authentication rejected, path: {}, status: {}",
                        uri.getPath(), exception.getStatusCode().value());
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-settlement internal authentication failed");
            }
            log.warn("service-settlement management call rejected, path: {}, status: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement management call rejected");
        } catch (RestClientException exception) {
            log.warn("service-settlement management call failed, path: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement management call failed");
        }
    }

    /**
     * 使用固定 service-admin 身份、时间戳、随机 nonce 和载荷摘要签名内部请求。
     *
     * @param uri 规范化 service-settlement 内部地址
     * @param method HTTP 方法
     * @param request 不含浏览器自报操作人的内部命令
     * @return 含 HMAC 防重放请求头和冻结 JSON 的 HTTP 实体
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

    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    private String baseUrl() {
        String value = properties.getBaseUrl();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("admin settlement-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

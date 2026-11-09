package com.scott.payment.admin.client.settlement;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.SettlementInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewStartRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileVersionCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileReadinessResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalSubmitRequest;
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
 * Admin 结算 HMAC REST 客户端；签名覆盖方法、路径、查询、时间戳、nonce、caller 和请求体摘要。
 */
@Service
@Slf4j
public class SettlementInternalRestClient implements SettlementInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final String BATCHES = "/internal/settlement/v1/batches";
    private static final String REVIEWS = "/internal/settlement/v1/reviews";
    private static final String REVERSALS = "/internal/settlement/v1/reversal-orders";
    private static final String PROFILES = "/internal/settlement/v1/profiles";
    private static final String PRINCIPAL_CORRECTIONS =
            "/internal/settlement/v1/principal-corrections";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final SettlementInternalClientProperties properties;

    /**
     * 创建结算服务内部 REST 客户端。
     */
    public SettlementInternalRestClient(
            @Qualifier("adminSettlementInternalRestTemplate") RestTemplate direct,
            @Qualifier("adminSettlementInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            SettlementInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 调用结算内部接口创建商户首个活动档案。
     */
    @Override
    public ProfileCommandResponse createProfile(InternalProfileCreateRequest request) {
        return post(PROFILES, request,
                new TypeReference<CommonResult<ProfileCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口从当前活动档案创建新版本。
     */
    @Override
    public ProfileCommandResponse createProfileVersion(
            String settlementProfileNo, InternalProfileVersionCreateRequest request) {
        return post(PROFILES + "/" + settlementProfileNo + "/versions", request,
                new TypeReference<CommonResult<ProfileCommandResponse>>() { });
    }

    /**
     * 查询商户结算档案和目标账户 readiness。
     */
    @Override
    public ProfileReadinessResponse getProfileReadiness(String merchantId) {
        return get(PROFILES + "/merchants/" + merchantId + "/readiness",
                new TypeReference<CommonResult<ProfileReadinessResponse>>() { });
    }

    /**
     * 调用结算内部接口，在入账前取消指定正式结算批次。
     */
    @Override
    public BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request) {
        return post(BATCHES + "/" + settlementBatchNo + "/cancel", request,
                new TypeReference<CommonResult<BatchCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口，重试处于可恢复状态的正式结算批次。
     */
    @Override
    public BatchCommandResponse retry(String settlementBatchNo, InternalBatchCommandRequest request) {
        return post(BATCHES + "/" + settlementBatchNo + "/retry", request,
                new TypeReference<CommonResult<BatchCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口，创建并提交结算预审单。
     */
    @Override
    public ReviewCommandResponse submitReview(InternalReviewSubmitRequest request) {
        return post(REVIEWS, request,
                new TypeReference<CommonResult<ReviewCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口，审批、驳回或取消指定预审单。
     */
    @Override
    public ReviewCommandResponse decideReview(String reviewOrderNo,
                                              InternalReviewDecisionRequest request) {
        return post(REVIEWS + "/" + reviewOrderNo + "/decisions", request,
                new TypeReference<CommonResult<ReviewCommandResponse>>() { });
    }

    /**
     * 创建手动交易结算预审的只读预览。
     */
    @Override
    public ManualReviewTaskResponse previewManualReview(InternalManualReviewPreviewRequest request) {
        return post(REVIEWS + "/manual-transaction-tasks/preview", request,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 启动手动交易结算预审任务。
     */
    @Override
    public ManualReviewTaskResponse startManualReview(String taskNo,
                                                      ManualReviewStartRequest request) {
        return post(REVIEWS + "/manual-transaction-tasks/" + taskNo + "/start", request,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 查询手动交易结算预审任务。
     */
    @Override
    public ManualReviewTaskResponse getManualReviewTask(String taskNo) {
        return get(REVIEWS + "/manual-transaction-tasks/" + taskNo,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 创建手动保证金结算预审的只读预览。
     */
    @Override
    public ManualReviewTaskResponse previewManualReserveReview(
            InternalManualReviewPreviewRequest request) {
        return post(REVIEWS + "/manual-reserve-tasks/preview", request,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 启动手动保证金结算预审任务。
     */
    @Override
    public ManualReviewTaskResponse startManualReserveReview(
            String taskNo, ManualReviewStartRequest request) {
        return post(REVIEWS + "/manual-reserve-tasks/" + taskNo + "/start", request,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 查询手动保证金结算预审任务。
     */
    @Override
    public ManualReviewTaskResponse getManualReserveReviewTask(String taskNo) {
        return get(REVIEWS + "/manual-reserve-tasks/" + taskNo,
                new TypeReference<CommonResult<ManualReviewTaskResponse>>() { });
    }

    /**
     * 提交可恢复的结算预审异步决策任务。
     */
    @Override
    public ReviewDecisionTaskResponse submitReviewDecisionTask(
            String reviewOrderNo,
            InternalReviewDecisionRequest request) {
        return post(REVIEWS + "/" + reviewOrderNo + "/decision-tasks", request,
                new TypeReference<CommonResult<ReviewDecisionTaskResponse>>() { });
    }

    /**
     * 查询结算复核异步决策任务。
     */
    @Override
    public ReviewDecisionTaskResponse getReviewDecisionTask(String taskNo) {
        return get(REVIEWS + "/decision-tasks/" + taskNo,
                new TypeReference<CommonResult<ReviewDecisionTaskResponse>>() { });
    }

    /**
     * 恢复可重试的结算预审决策任务。
     */
    @Override
    public ReviewDecisionTaskResponse resumeReviewDecisionTask(
            String taskNo, InternalReviewDecisionTaskResumeRequest request) {
        return post(REVIEWS + "/decision-tasks/" + taskNo + "/resume", request,
                new TypeReference<CommonResult<ReviewDecisionTaskResponse>>() { });
    }

    /**
     * 调用结算内部接口，为已入账批次提交冲正申请。
     */
    @Override
    public ReversalCommandResponse submitReversal(InternalReversalSubmitRequest request) {
        return post(REVERSALS, request,
                new TypeReference<CommonResult<ReversalCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口，复核指定结算冲正申请。
     */
    @Override
    public ReversalCommandResponse decideReversal(String reversalOrderNo,
                                                  InternalReversalDecisionRequest request) {
        return post(REVERSALS + "/" + reversalOrderNo + "/decisions", request,
                new TypeReference<CommonResult<ReversalCommandResponse>>() { });
    }

    /**
     * 调用结算内部接口冻结已入账历史标签本金差额。
     */
    @Override
    public CommandResponse submitPrincipalCorrection(InternalSubmitRequest request) {
        return post(PRINCIPAL_CORRECTIONS, request,
                new TypeReference<CommonResult<CommandResponse>>() { });
    }

    /**
     * 调用结算内部接口复核并执行或拒绝历史标签本金差额调整。
     */
    @Override
    public CommandResponse decidePrincipalCorrection(
            String correctionNo, InternalDecisionRequest request) {
        return post(PRINCIPAL_CORRECTIONS + "/" + correctionNo + "/decisions", request,
                new TypeReference<CommonResult<CommandResponse>>() { });
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

    /**
     * 调用结算内部查询接口并解包响应。
     */
    private <T> T get(String path, TypeReference<CommonResult<T>> type) {
        return exchange(URI.create(baseUrl() + path), HttpMethod.GET, null, type);
    }

    /**
     * 对内部请求签名并统一解包 CommonResult，远端异常不向浏览器暴露服务内部正文。
     */
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
                log.warn("结算内部接口鉴权被拒绝, event: ADMIN_SETTLEMENT_INTERNAL_AUTH_REJECTED, method: {}, path: {}, status: {}",
                        method.name(), uri.getPath(), exception.getStatusCode().value());
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-settlement internal authentication failed");
            }
            log.warn("结算内部接口请求被拒绝, event: ADMIN_SETTLEMENT_INTERNAL_HTTP_REJECTED, method: {}, path: {}, status: {}",
                    method.name(), uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement management call rejected");
        } catch (RestClientException exception) {
            log.warn("结算内部接口调用失败, event: ADMIN_SETTLEMENT_INTERNAL_CALL_FAILED, method: {}, path: {}, exceptionType: {}",
                    method.name(), uri.getPath(), exception.getClass().getSimpleName());
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

    /**
     * 根据内部服务地址选择直连或负载均衡客户端。
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement host is empty");
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
            throw new IllegalArgumentException("admin settlement-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

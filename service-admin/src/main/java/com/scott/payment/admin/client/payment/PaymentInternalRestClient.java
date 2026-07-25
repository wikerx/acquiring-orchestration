package com.scott.payment.admin.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.PaymentInternalClientProperties;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
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

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-14 23:57
 * @email : scott_x@163.com
 * @description : service-payment 内部查询 REST 客户端，位于 service-admin 客户端层，为管理后台交易查询封装 HMAC 签名和统一响应解包。
 * @status : create
 */
@Service
@Slf4j
public class PaymentInternalRestClient implements PaymentInternalClient {

    /**
     * IPv4 地址格式。
     */
    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * 本机地址。
     */
    private static final String LOCALHOST = "localhost";

    /**
     * IPv6 本机地址。
     */
    private static final String IPV6_LOOPBACK = "::1";

    /**
     * 域名点分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    private final RestTemplate directRestTemplate;

    private final RestTemplate loadBalancedRestTemplate;

    private final PaymentInternalClientProperties properties;

    /**
     * 创建 service-payment 内部查询 REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param properties               内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("paymentInternalRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("paymentInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * 分页查询交易主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    @Override
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        CommonResult<PageResult<TransactionOrderResponse>> result = post(
                properties.getOrderSearchUrl(),
                query,
                new TypeReference<CommonResult<PageResult<TransactionOrderResponse>>>() {
                });
        return unwrapData(result);
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    @Override
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        CommonResult<PageResult<TransactionOperationResponse>> result = post(
                properties.getOperationSearchUrl(),
                query,
                new TypeReference<CommonResult<PageResult<TransactionOperationResponse>>>() {
                });
        return unwrapData(result);
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    @Override
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        CommonResult<TransactionOperationSearchResponse> result = post(
                properties.getOperationSearchWithSummaryUrl(),
                query,
                new TypeReference<CommonResult<TransactionOperationSearchResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起退款动作。
     *
     * @param requestDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    @Override
    public TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                properties.getCaptureUrl(),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起退款动作。
     *
     * @param requestDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    @Override
    public TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                properties.getRefundUrl(),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param requestDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    @Override
    public TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                properties.getVoidUrl(),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    @Override
    public TransactionDetailResponse detail(String transactionId) {
        CommonResult<TransactionDetailResponse> result = get(
                properties.getDetailBaseUrl() + "/" + transactionId,
                new TypeReference<CommonResult<TransactionDetailResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query) {
        CommonResult<PageResult<Map<String, Object>>> result = post(
                properties.getChannelLogSearchUrl(),
                query,
                new TypeReference<CommonResult<PageResult<Map<String, Object>>>>() {
                });
        return unwrapData(result);
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query) {
        CommonResult<PageResult<Map<String, Object>>> result = post(
                properties.getChannelCallbackSearchUrl(),
                query,
                new TypeReference<CommonResult<PageResult<Map<String, Object>>>>() {
                });
        return unwrapData(result);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query) {
        CommonResult<PageResult<Map<String, Object>>> result = post(
                properties.getMerchantNotificationSearchUrl(),
                query,
                new TypeReference<CommonResult<PageResult<Map<String, Object>>>>() {
                });
        return unwrapData(result);
    }

    private <T> T get(String url, TypeReference<T> typeReference) {
        URI uri = URI.create(url);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(uri, HttpMethod.GET,
                    buildSignedEntity(uri, HttpMethod.GET, null), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException(HttpMethod.GET, uri, exception);
        } catch (RestClientException exception) {
            log.warn("service-payment get call failed, targetUri={}", uri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment get call failed");
        }
    }

    private <T> T post(String url, Object body, TypeReference<T> typeReference) {
        URI uri = URI.create(url);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(uri, HttpMethod.POST,
                    buildSignedEntity(uri, HttpMethod.POST, body), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException(HttpMethod.POST, uri, exception);
        } catch (RestClientException exception) {
            log.warn("service-payment post call failed, targetUri={}", uri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment post call failed");
        }
    }

    private HttpEntity<String> buildSignedEntity(URI uri, HttpMethod method, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                method.name(),
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                properties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body == null ? null : JsonUtils.toJsonString(body), headers);
    }

    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host)
                || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches()
                || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    private <T> T unwrapData(CommonResult<T> result) {
        if (result == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment response data is empty");
        }
        return result.getData();
    }

    private ApiException translateHttpException(HttpMethod method, URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment {} call returned non-success status, targetUri={}, status={}",
                method.name(),
                uri,
                exception.getStatusCode().value(),
                exception);
        if (exception.getStatusCode().value() == 401) {
            return new ApiException(ApiResultEnum.UNAUTHORIZED, "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ApiException(ApiResultEnum.FORBIDDEN, "service-payment call forbidden");
        }
        return new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment call failed");
    }
}

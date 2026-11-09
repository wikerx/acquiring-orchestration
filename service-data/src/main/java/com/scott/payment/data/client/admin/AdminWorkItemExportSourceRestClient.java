package com.scott.payment.data.client.admin;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageRequest;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OperationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OperationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.config.export.DataAdminClientProperties;
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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin 导出源 REST 客户端，使用 HMAC 请求签名并统一解包内部响应。
 */
@Slf4j
@Service
public class AdminWorkItemExportSourceRestClient
        implements AdminWorkItemExportSourceClient, AdminTransactionExportSourceClient,
        AdminSettlementExportSourceClient {
    private static final String WORK_ITEM_PATH = "/internal/admin/operations/work-items/export-page";
    private static final String ORDER_PATH = "/internal/admin/transactions/orders/export-page";
    private static final String OPERATION_PATH = "/internal/admin/transactions/operations/export-page";
    private static final String MERCHANT_NOTIFICATION_PATH =
            "/internal/admin/transactions/merchant-notifications/export-page";
    private static final String REFUND_PATH = "/internal/admin/transactions/refunds/export-page";
    private static final String SETTLEMENT_TRANSACTION_PATH =
            "/internal/admin/settlement/result-items/export-page";
    private static final String SETTLEMENT_RESERVE_PATH =
            "/internal/admin/settlement/reserve-items/export-page";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final DataAdminClientProperties properties;

    /**
     * 创建只访问 Admin 导出源路径的内部客户端。
     */
    public AdminWorkItemExportSourceRestClient(
            @Qualifier("dataAdminInternalRestTemplate") RestTemplate direct,
            @Qualifier("dataAdminInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            DataAdminClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 请求一页数据，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public PageResponse loadPage(PageRequest request) {
        return post(WORK_ITEM_PATH, request, new TypeReference<CommonResult<PageResponse>>() { });
    }

    /**
     * 请求一页交易主单，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public OrderPageResponse loadOrderPage(OrderPageRequest request) {
        return post(ORDER_PATH, request, new TypeReference<CommonResult<OrderPageResponse>>() { });
    }

    /**
     * 请求一页交易操作流水，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public OperationPageResponse loadOperationPage(OperationPageRequest request) {
        return post(OPERATION_PATH, request,
                new TypeReference<CommonResult<OperationPageResponse>>() { });
    }

    /**
     * 请求一页商户通知记录，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public MerchantNotificationPageResponse loadMerchantNotificationPage(
            MerchantNotificationPageRequest request) {
        return post(MERCHANT_NOTIFICATION_PATH, request,
                new TypeReference<CommonResult<MerchantNotificationPageResponse>>() { });
    }

    /**
     * 请求一页退款记录，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public RefundPageResponse loadRefundPage(RefundPageRequest request) {
        return post(REFUND_PATH, request, new TypeReference<CommonResult<RefundPageResponse>>() { });
    }

    /**
     * 请求一页结算交易明细，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public SettlementTransactionPageResponse loadSettlementTransactionPage(
            SettlementTransactionPageRequest request) {
        return post(SETTLEMENT_TRANSACTION_PATH, request,
                new TypeReference<CommonResult<SettlementTransactionPageResponse>>() { });
    }

    /**
     * 请求一页保证金结算明细，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public SettlementReservePageResponse loadSettlementReservePage(
            SettlementReservePageRequest request) {
        return post(SETTLEMENT_RESERVE_PATH, request,
                new TypeReference<CommonResult<SettlementReservePageResponse>>() { });
    }

    /**
     * 使用统一内部签名调用 Admin 导出来源并解包强类型响应。
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> responseType) {
        properties.validate();
        URI uri = URI.create(properties.getBaseUrl().trim() + path);
        try {
            String body = choose(uri).exchange(uri, HttpMethod.POST, signed(uri, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, responseType);
            if (result == null || !CommonResult.isSuccess(result) || result.getData() == null) {
                throw new ServiceException(result == null ? ApiResultEnum.BAD_GATEWAY.getCode() : result.getCode(),
                        result == null ? "service-admin export source response is empty" : result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("管理端导出源调用被拒绝, event: DATA_EXPORT_SOURCE_REJECTED, path: {}, httpStatus: {}",
                    path, exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin export source rejected");
        } catch (RestClientException exception) {
            log.warn("管理端导出源调用失败, event: DATA_EXPORT_SOURCE_FAILED, path: {}, exceptionType: {}",
                    path, exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin export source failed");
        }
    }

    /**
     * 构造绑定方法、路径、正文摘要和调用方的内部签名实体。
     */
    private HttpEntity<String> signed(URI uri, Object request) {
        String body = JsonUtils.toJsonString(request);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, properties.getInternalCaller());
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 固定主机走直连，逻辑服务名走负载均衡客户端。
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }
}

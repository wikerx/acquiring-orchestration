package com.scott.payment.data.client.merchant;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageResponse;
import com.scott.payment.data.config.export.DataMerchantClientProperties;
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
 * Merchant 导出来源 REST 客户端，使用独立 HMAC 请求签名并统一解包安全投影。
 */
@Slf4j
@Service
public class MerchantExportSourceRestClient implements MerchantExportSourceClient {
    private static final String TRANSACTION_PATH = "/internal/merchant/export-sources/transaction-operations";
    private static final String REFUND_PATH = "/internal/merchant/export-sources/refunds";
    private static final String SETTLEMENT_BATCH_PATH =
            "/internal/merchant/export-sources/settlement-batches";
    private static final String SETTLEMENT_SUMMARY_PATH =
            "/internal/merchant/export-sources/settlement-summaries";
    private static final String SETTLEMENT_TRANSACTION_PATH =
            "/internal/merchant/export-sources/settlement-transactions";
    private static final String SETTLEMENT_RESERVE_PATH =
            "/internal/merchant/export-sources/settlement-reserves";
    private static final String FUND_LEDGER_PATH = "/internal/merchant/export-sources/fund-ledgers";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final DataMerchantClientProperties properties;

    /**
     * 创建只访问 Merchant 导出来源路径的内部客户端。
     */
    public MerchantExportSourceRestClient(
            @Qualifier("dataMerchantInternalRestTemplate") RestTemplate direct,
            @Qualifier("dataMerchantInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            DataMerchantClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 读取一页商户交易安全投影。
     */
    @Override
    public TransactionPageResponse loadTransactionPage(TransactionPageRequest request) {
        return post(TRANSACTION_PATH, request,
                new TypeReference<CommonResult<TransactionPageResponse>>() { });
    }

    /**
     * 读取一页商户退款安全投影。
     */
    @Override
    public RefundPageResponse loadRefundPage(RefundPageRequest request) {
        return post(REFUND_PATH, request,
                new TypeReference<CommonResult<RefundPageResponse>>() { });
    }

    /**
     * 读取一页商户结算批次安全投影。
     */
    @Override
    public SettlementBatchPageResponse loadSettlementBatchPage(SettlementBatchPageRequest request) {
        return post(SETTLEMENT_BATCH_PATH, request,
                new TypeReference<CommonResult<SettlementBatchPageResponse>>() { });
    }

    /**
     * 读取指定批次的一页商户结算汇总安全投影。
     */
    @Override
    public SettlementSummaryPageResponse loadSettlementSummaryPage(SettlementSummaryPageRequest request) {
        return post(SETTLEMENT_SUMMARY_PATH, request,
                new TypeReference<CommonResult<SettlementSummaryPageResponse>>() { });
    }

    /**
     * 读取一页商户交易结算安全投影。
     */
    @Override
    public SettlementTransactionPageResponse loadSettlementTransactionPage(
            SettlementTransactionPageRequest request) {
        return post(SETTLEMENT_TRANSACTION_PATH, request,
                new TypeReference<CommonResult<SettlementTransactionPageResponse>>() { });
    }

    /**
     * 读取一页商户保证金结算安全投影。
     */
    @Override
    public SettlementReservePageResponse loadSettlementReservePage(SettlementReservePageRequest request) {
        return post(SETTLEMENT_RESERVE_PATH, request,
                new TypeReference<CommonResult<SettlementReservePageResponse>>() { });
    }

    /**
     * 读取一页商户余额流水安全投影。
     */
    @Override
    public FundLedgerPageResponse loadFundLedgerPage(FundLedgerPageRequest request) {
        return post(FUND_LEDGER_PATH, request,
                new TypeReference<CommonResult<FundLedgerPageResponse>>() { });
    }

    /**
     * 使用独立内部签名调用 Merchant 来源接口并解包强类型响应。
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> responseType) {
        properties.validate();
        URI uri = URI.create(properties.getBaseUrl().trim() + path);
        try {
            String body = choose(uri).exchange(uri, HttpMethod.POST, signed(uri, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, responseType);
            if (result == null || !CommonResult.isSuccess(result) || result.getData() == null) {
                throw new ServiceException(result == null ? ApiResultEnum.BAD_GATEWAY.getCode() : result.getCode(),
                        result == null ? "service-merchant export source response is empty" : result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("商户导出源调用被拒绝, event: DATA_MERCHANT_EXPORT_SOURCE_REJECTED, path: {}, httpStatus: {}",
                    path, exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-merchant export source rejected");
        } catch (RestClientException exception) {
            log.warn("商户导出源调用失败, event: DATA_MERCHANT_EXPORT_SOURCE_FAILED, path: {}, exceptionType: {}",
                    path, exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-merchant export source failed");
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
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-merchant host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }
}

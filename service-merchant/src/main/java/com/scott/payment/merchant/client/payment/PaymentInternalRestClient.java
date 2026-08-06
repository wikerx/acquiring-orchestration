package com.scott.payment.merchant.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.config.PaymentInternalClientProperties;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部 REST 客户端，位于 service-merchant 客户端层，为商户后台退款等状态变更动作封装 HMAC 签名和统一响应解包。
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

    /**
     * 商户后台调用支付核心的内部动作路径是服务契约，不通过配置中心覆盖。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /** 商户发起请款时调用的支付核心内部接口。 */
    private static final String CAPTURE_PATH = "/internal/payment/capture";

    /** 商户发起退款时调用的支付核心内部接口。 */
    private static final String REFUND_PATH = "/internal/payment/refund";

    /** 商户发起撤销时调用的支付核心内部接口。 */
    private static final String VOID_PATH = "/internal/payment/void";

    private static final String REFUND_SEARCH_PATH = "/internal/payment/refunds/search";

    private static final String REFUND_DETAIL_PATH = "/internal/payment/refunds";

    /**
     * direct Rest Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final RestTemplate directRestTemplate;

    /**
     * load Balanced Rest Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * properties 依赖，用于 Payment Internal Rest Client 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentInternalClientProperties properties;

    /**
     * 创建 service-payment 内部 REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param properties               内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("merchantPaymentInternalRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("merchantPaymentInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /** 查询当前商户退款分页和统计。 */
    @Override
    public RefundSearchResponse searchRefunds(RefundQuery query) {
        CommonResult<RefundSearchResponse> result = post(
                servicePaymentUrl(REFUND_SEARCH_PATH), query,
                new TypeReference<CommonResult<RefundSearchResponse>>() {
                });
        return unwrapData(result);
    }

    /** 查询当前商户退款详情，merchantId 同时在 Payment 查询条件中强制校验。 */
    @Override
    public RefundDetailResponse refundDetail(String transactionId,
                                             LocalDateTime transactionDateTime,
                                             String merchantId) {
        String url = UriComponentsBuilder.fromUriString(servicePaymentUrl(REFUND_DETAIL_PATH) + "/" + transactionId)
                .queryParam("transactionDateTime", transactionDateTime)
                .queryParam("merchantId", merchantId)
                .build().encode().toUriString();
        CommonResult<RefundDetailResponse> result = get(
                url, new TypeReference<CommonResult<RefundDetailResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    @Override
    public TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                servicePaymentUrl(CAPTURE_PATH),
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
                servicePaymentUrl(REFUND_PATH),
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
                servicePaymentUrl(VOID_PATH),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
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
            log.warn("service-payment post call failed, targetUri: {}", uri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment post call failed");
        }
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
            log.warn("service-payment get call failed, targetUri: {}", uri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment get call failed");
        }
    }

    /**
     * 拼接 service-payment 内部接口地址。
     *
     * @param path 内部交易接口路径
     * @return 服务发现基地址与路径组成的 URL
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
    }

    /**
     * 创建带服务间签名头的 JSON 请求实体。
     * <p>
     * 签名绑定方法、URI 路径、毫秒时间戳、随机 nonce 和调用方标识；内部密钥、签名和交易
     * 请求体均不得写入日志。
     * </p>
     *
     * @param uri    目标内部接口 URI
     * @param method HTTP 方法
     * @param body   交易请求 DTO
     * @return 已签名请求实体
     */
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

    /**
     * 根据 URI 主机形式选择直连或服务发现 RestTemplate。
     *
     * @param uri 目标 URI
     * @return IP、localhost 和域名使用直连模板；服务名使用负载均衡模板
     * @throws ApiException URI 缺少主机时抛出
     */
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

    /**
     * 转换转换HTTP异常，把下游响应、异常或包装结果映射为当前模块统一语义。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param method HTTP 方法或内部调用方法名，用于构造请求、签名或异常摘要
     * @param uri 请求地址或路径，用于定位内部服务、渠道接口或商户回调目标
     * @param exception 下游调用、校验或持久化阶段捕获的异常对象
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ApiException translateHttpException(HttpMethod method, URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment {} call returned non-success status, targetUri: {}, status: {}",
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

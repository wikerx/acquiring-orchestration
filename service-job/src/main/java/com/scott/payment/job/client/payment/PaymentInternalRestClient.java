package com.scott.payment.job.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;
import com.scott.payment.job.config.PaymentInternalClientProperties;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部补偿 REST 客户端，位于 service-job 客户端层，负责内部 HMAC 签名、服务发现选择和统一响应解包。
 * @status : create
 */
@Slf4j
@Service
public class PaymentInternalRestClient implements PaymentInternalClient {

    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * {@code LOCALHOST}，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String LOCALHOST = "localhost";

    /**
     * {@code IPV6_LOOPBACK}常量，统一 {@code PaymentInternalRestClient} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String IPV6_LOOPBACK = "::1";

    /**
     * {@code DOMAIN_SEPARATOR}，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * service-job 调用支付核心补偿接口的路径是固定服务契约，不由 Nacos 或参数表覆盖。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /**
     * 扫描待渠道补匹配交易的内部接口；任务参数负责限定时间分片和单批数量。
     */
    private static final String CHANNEL_MATCH_DUE_PATH = "/internal/payment/transactions/channel-match/match-due";

    /** 收银台未提交订单超时关闭内部接口。 */
    private static final String CHECKOUT_EXPIRE_DUE_PATH = "/internal/payment/checkout/session/expire-due";

    private final RestTemplate directRestTemplate;

    private final RestTemplate loadBalancedRestTemplate;

    private final PaymentInternalClientProperties properties;

    /**
     * 创建 service-payment 内部补偿 REST 客户端。
     *
     * @param directRestTemplate 直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param properties 内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("jobPaymentInternalRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("jobPaymentInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * 触发指定交易时间分表中的渠道交易查询勾兑。
     *
     * @param requestDTO 查询勾兑请求
     * @return 查询勾兑处理结果
     */
    @Override
    public PaymentChannelMatchClientResultDTO matchDueChannelTransactions(PaymentChannelMatchClientRequestDTO requestDTO) {
        CommonResult<PaymentChannelMatchClientResultDTO> result = post(
                servicePaymentUrl(CHANNEL_MATCH_DUE_PATH),
                requestDTO,
                new TypeReference<CommonResult<PaymentChannelMatchClientResultDTO>>() {
                });
        return unwrapData(result);
    }

    /**
     * 调用支付核心关闭超过付款截止时间且从未提交的收银台订单。
     *
     * @param limit 单次扫描上限
     * @return 实际超时关闭数量
     */
    @Override
    public int expireDueCheckoutSessions(int limit) {
        CommonResult<Integer> result = post(
                servicePaymentUrl(CHECKOUT_EXPIRE_DUE_PATH) + "?limit=" + limit,
                null,
                new TypeReference<CommonResult<Integer>>() {
                });
        Integer expiredCount = unwrapData(result);
        return expiredCount == null ? 0 : expiredCount;
    }

    private <T> T post(String url, Object body, TypeReference<T> typeReference) {
        URI uri = URI.create(url);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(uri, HttpMethod.POST,
                    buildSignedEntity(uri, body), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException(uri, exception);
        } catch (RestClientException exception) {
            log.warn("service-payment compensation call failed, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-payment compensation call failed");
        }
    }

    /**
     * 拼接 service-payment 内部接口地址。
     *
     * @param path 以斜杠开头的内部接口路径
     * @return 服务发现基地址与路径组成的 URL
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
    }

    /**
     * 创建带服务间签名头的 JSON 请求实体。
     * <p>
     * 签名绑定 HTTP 方法、URI 路径、毫秒时间戳、随机 nonce 和调用方标识；内部密钥与签名
     * 原文不得写入日志。
     * </p>
     *
     * @param uri  目标内部接口 URI
     * @param body 请求 DTO
     * @return 已签名的 JSON 请求实体
     */
    private HttpEntity<String> buildSignedEntity(URI uri, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String requestBody = body == null ? null : JsonUtils.toJsonString(body);
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp,
                nonce,
                caller,
                InternalServiceSignature.payloadSha256(requestBody),
                properties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(requestBody, headers);
    }

    /**
     * 根据 URI 主机形式选择直连或服务发现 RestTemplate。
     *
     * @param uri 目标 URI
     * @return IP、localhost 和域名使用直连模板；服务名使用负载均衡模板
     * @throws ServiceException URI 缺少主机时抛出
     */
    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment url host is empty");
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
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ServiceException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    private ServiceException translateHttpException(URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment compensation call returned non-success status, targetPath: {}, status: {}, exceptionType: {}",
                uri.getPath(),
                exception.getStatusCode().value(),
                exception.getClass().getSimpleName());
        if (exception.getStatusCode().value() == 401) {
            return new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "service-payment call forbidden");
        }
        return new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed");
    }
}

package com.scott.payment.openapi.client.payout;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;
import com.scott.payment.openapi.config.PayoutClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalRestClient
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutInternalRestClient 内部或渠道客户端，用于封装远程调用、协议参数和异常转换，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PayoutInternalRestClient implements PayoutInternalClient {

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
     * 域名分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * 直连 RestTemplate。
     */
    private final RestTemplate directRestTemplate;

    /**
     * 负载均衡 RestTemplate。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 代付内部客户端配置。
     */
    private final PayoutClientProperties payoutClientProperties;

    /**
     * 创建 service-payout REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param payoutClientProperties   代付内部客户端配置
     */
    public PayoutInternalRestClient(@Qualifier("payoutRestTemplate") RestTemplate directRestTemplate,
                                    @Qualifier("payoutLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                    PayoutClientProperties payoutClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.payoutClientProperties = payoutClientProperties;
    }

    /**
     * 调用 service-payout 创建代付交易。
     *
     * @param requestDTO 创建代付内部请求
     * @return 创建代付内部响应
     */
    @Override
    public PayoutCreateClientResponseDTO createPayout(PayoutCreateClientRequestDTO requestDTO) {
        try {
            String createUrl = payoutClientProperties.getCreateUrl();
            String responseBody = chooseRestTemplate(createUrl).postForObject(
                    createUrl,
                    buildSignedEntity(URI.create(createUrl), requestDTO),
                    String.class
            );
            CommonResult<PayoutCreateClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<PayoutCreateClientResponseDTO>>() {
                    }
            );
            return unwrapResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payout call failed");
        }
    }

    /**
     * 根据 URL 选择客户端。
     *
     * @param createUrl service-payout 创建接口地址
     * @return 对应的 RestTemplate
     */
    private RestTemplate chooseRestTemplate(String createUrl) {
        URI uri = URI.create(createUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payout url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host) || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches() || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    /**
     * 构造带内部服务签名头的请求实体。
     *
     * @param uri        内部服务地址
     * @param requestDTO 创建代付内部请求
     * @return 带签名头的请求实体
     */
    private HttpEntity<PayoutCreateClientRequestDTO> buildSignedEntity(URI uri, PayoutCreateClientRequestDTO requestDTO) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = payoutClientProperties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                "POST",
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                payoutClientProperties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(requestDTO, headers);
    }

    /**
     * 解包内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 创建代付内部响应
     */
    private PayoutCreateClientResponseDTO unwrapResult(CommonResult<PayoutCreateClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payout response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payout response data is empty");
        }
        return result.getData();
    }
}

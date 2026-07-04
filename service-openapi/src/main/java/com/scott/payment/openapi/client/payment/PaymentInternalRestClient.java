package com.scott.payment.openapi.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-05-31 21:15
 * @email : scott_x@163.com
 * @description : service-payment REST 客户端，支持 Nacos 服务名负载均衡和本地 IP 直连
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayment Internal Rest Client，位于 service-openapi 的外部调用层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class PaymentInternalRestClient implements PaymentInternalClient {

    /**
     * IPv4 地址格式，用于识别本地或固定地址直连场景。
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
     * URL 主机分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * 直连 RestTemplate，用于 localhost、IP 或完整域名。
     */
    private final RestTemplate directRestTemplate;

    /**
     * 负载均衡 RestTemplate，用于 `http://service-payment/...` 这类 Nacos 服务名。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 支付内部客户端配置。
     */
    private final PaymentClientProperties paymentClientProperties;

    /**
     * 创建 service-payment REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param paymentClientProperties  支付内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("paymentRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("paymentLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentClientProperties paymentClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.paymentClientProperties = paymentClientProperties;
    }

    /**
     * 调用 service-payment 创建收单授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    /**
     * 创建或保存商户 OpenAPI数据，保持请求校验、默认值和审计字段一致。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
        try {
            String authorizationUrl = paymentClientProperties.getAuthorizationUrl();
            String responseBody = chooseRestTemplate(authorizationUrl).postForObject(
                    authorizationUrl,
                    requestDTO,
                    String.class
            );
            CommonResult<PaymentCreateClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<PaymentCreateClientResponseDTO>>() {
                    }
            );
            return unwrapResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment call failed");
        }
    }

    /**
     * 根据配置的授权接口地址选择调用客户端。
     * <p>
     * 单段主机名如 `service-payment` 代表服务发现名称，走负载均衡；localhost、IP 和带点域名代表
     * 明确网络地址，直接调用，方便本地联调和固定域名部署。
     *
     * @param authorizationUrl service-payment 授权接口地址
     * @return 匹配当前地址类型的 RestTemplate
     */
    private RestTemplate chooseRestTemplate(String authorizationUrl) {
        URI uri = URI.create(authorizationUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host) || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches() || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    /**
     * 解包内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 创建交易内部响应
     */
    private PaymentCreateClientResponseDTO unwrapResult(CommonResult<PaymentCreateClientResponseDTO> result) {
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
}

package com.scott.payment.payment.client.risk;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.config.RiskClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalRestClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk REST 客户端，位于 service-payment 客户端层，负责内部 HMAC 签名、服务发现选择和统一响应解包。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "payment.risk-client", name = "remote-enabled", havingValue = "true")
public class RiskInternalRestClient implements RiskInternalClient {

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
     * 负载均衡 RestTemplate，用于 `http://service-risk/...` 这类服务名。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 风控内部客户端配置。
     */
    private final RiskClientProperties riskClientProperties;

    /**
     * 创建 service-risk REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param riskClientProperties     风控内部客户端配置
     */
    public RiskInternalRestClient(@Qualifier("riskRestTemplate") RestTemplate directRestTemplate,
                                  @Qualifier("riskLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                  RiskClientProperties riskClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.riskClientProperties = riskClientProperties;
    }

    /**
     * 调用 service-risk 执行支付路由前风控评估。
     *
     * @param requestDTO 风控评估请求
     * @return 风控评估响应
     */
    @Override
    public RiskPaymentEvaluateClientResponseDTO evaluatePayment(RiskPaymentEvaluateClientRequestDTO requestDTO) {
        try {
            String evaluateUrl = riskClientProperties.getEvaluateUrl();
            String responseBody = chooseRestTemplate(evaluateUrl).postForObject(
                    evaluateUrl,
                    buildSignedEntity(URI.create(evaluateUrl), requestDTO),
                    String.class
            );
            CommonResult<RiskPaymentEvaluateClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<RiskPaymentEvaluateClientResponseDTO>>() {
                    }
            );
            return unwrapResult(result);
        } catch (RestClientException exception) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk call failed", exception);
        }
    }

    /**
     * 完成 choose Rest Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param evaluateUrl evaluate Url 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private RestTemplate chooseRestTemplate(String evaluateUrl) {
        URI uri = URI.create(evaluateUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host) || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches() || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    /**
     * 构建 build Signed Entity 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param uri uri 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     * @return 转换或构建后的目标对象
     */
    private HttpEntity<RiskPaymentEvaluateClientRequestDTO> buildSignedEntity(URI uri, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = riskClientProperties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                "POST",
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                riskClientProperties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(requestDTO, headers);
    }

    /**
     * 完成 unwrap Result 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private RiskPaymentEvaluateClientResponseDTO unwrapResult(CommonResult<RiskPaymentEvaluateClientResponseDTO> result) {
        if (result == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ServiceException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk response data is empty");
        }
        return result.getData();
    }
}

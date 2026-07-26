package com.scott.payment.job.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;
import com.scott.payment.job.client.payment.dto.PaymentMerchantNotificationNotifyDueClientRequestDTO;
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
     * LOCALHOST 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String LOCALHOST = "localhost";

    /**
     * IPV6 LOOPBACK 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV6_LOOPBACK = "::1";

    /**
     * DOMAIN SEPARATOR 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * direct Rest Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RestTemplate directRestTemplate;

    /**
     * load Balanced Rest Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * 触发指定交易时间分表中的到期商户通知补偿。
     *
     * @param requestDTO 补偿请求
     * @return 成功通知数量
     */
    @Override
    public Integer notifyDueMerchantNotifications(PaymentMerchantNotificationNotifyDueClientRequestDTO requestDTO) {
        CommonResult<Integer> result = post(
                properties.getMerchantNotificationNotifyDueUrl(),
                requestDTO,
                new TypeReference<CommonResult<Integer>>() {
                });
        return unwrapData(result);
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
                properties.getChannelMatchDueUrl(),
                requestDTO,
                new TypeReference<CommonResult<PaymentChannelMatchClientResultDTO>>() {
                });
        return unwrapData(result);
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
            log.warn("service-payment compensation call failed, targetUri={}", uri, exception);
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed", exception);
        }
    }

    /**
     * 构建 build Signed Entity 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param uri uri 输入值，含义由调用方法名称和所属业务对象限定
     * @param body body 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private HttpEntity<String> buildSignedEntity(URI uri, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(),
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
     * 完成 choose Rest Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param uri uri 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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

    /**
     * 完成 translate Http Exception 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param uri uri 输入值，含义由调用方法名称和所属业务对象限定
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException translateHttpException(URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment compensation call returned non-success status, targetUri={}, status={}",
                uri,
                exception.getStatusCode().value(),
                exception);
        if (exception.getStatusCode().value() == 401) {
            return new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "service-payment call forbidden");
        }
        return new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed");
    }
}

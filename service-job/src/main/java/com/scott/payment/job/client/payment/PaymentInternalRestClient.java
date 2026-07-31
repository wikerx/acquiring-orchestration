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
     * LOCALHOST，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String LOCALHOST = "localhost";

    /**
     * IPV 6 LOOPBACK，用于保存 Payment Internal Rest Client 中与 ipv6loopback 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String IPV6_LOOPBACK = "::1";

    /**
     * DOMAIN SEPARATOR，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * service-job 调用支付核心补偿接口的路径是固定服务契约，不由 Nacos 或参数表覆盖。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    private static final String MERCHANT_NOTIFICATION_NOTIFY_DUE_PATH =
            "/internal/payment/transactions/merchant-notifications/notify-due";

    /**
     * 扫描待渠道补匹配交易的内部接口；任务参数负责限定时间分片和单批数量。
     */
    private static final String CHANNEL_MATCH_DUE_PATH = "/internal/payment/transactions/channel-match/match-due";

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
                servicePaymentUrl(MERCHANT_NOTIFICATION_NOTIFY_DUE_PATH),
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
                servicePaymentUrl(CHANNEL_MATCH_DUE_PATH),
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
            log.warn("service-payment compensation call failed, targetUri: {}", uri, exception);
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed", exception);
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

    /**
     * 转换转换HTTP异常，把下游响应、异常或包装结果映射为当前模块统一语义。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param uri 请求地址或路径，用于定位内部服务、渠道接口或商户回调目标
     * @param exception 下游调用、校验或持久化阶段捕获的异常对象
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException translateHttpException(URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment compensation call returned non-success status, targetUri: {}, status: {}",
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

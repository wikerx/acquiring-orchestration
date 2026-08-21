package com.scott.payment.admin.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.PaymentInternalClientProperties;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalClientRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-14 23:57
 * @email : scott_x@163.com
 * @description : service-payment 内部命令 REST 客户端，为管理端交易变更、退款审批和异常案件处置封装 HMAC 签名与统一响应解包。
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
     * service-payment 内部管理接口路径属于代码级服务契约，避免环境配置覆盖真实调用目标。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /** 请款动作内部接口。 */
    private static final String CAPTURE_PATH = "/internal/payment/capture";

    /** 退款动作内部接口。 */
    private static final String REFUND_PATH = "/internal/payment/refund";

    /** 撤销动作内部接口。 */
    private static final String VOID_PATH = "/internal/payment/void";

    /** 单笔渠道勾兑内部接口。 */
    private static final String CHANNEL_MATCH_REQUERY_PATH = "/internal/payment/channel-match";

    private static final String REFUND_APPROVAL_PATH = "/internal/payment/refund-approvals";

    private static final String CHANNEL_MATCH_ABNORMAL_PATH =
            "/internal/payment/channel-match/abnormalities";

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
     * 创建 service-payment 内部命令 REST 客户端。
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

    /** 审批通过退款。 */
    @Override
    public ApprovalResult approveRefund(String approvalId, ApprovalClientRequest request) {
        return decideRefund(approvalId, "approve", request);
    }

    /** 拒绝退款审批。 */
    @Override
    public ApprovalResult rejectRefund(String approvalId, ApprovalClientRequest request) {
        return decideRefund(approvalId, "reject", request);
    }

    /** 领取或转派勾兑异常案件。 */
    @Override
    public com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord
    assignChannelMatchAbnormality(String eventId,
            com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignClientCommand command) {
        return postAbnormalAction(eventId, "claim", command,
                new TypeReference<CommonResult<com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord>>() {
                });
    }

    /** 单笔重新勾兑。 */
    @Override
    public com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord
    requeryChannelMatchAbnormality(String eventId,
            com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.RequeryCommand command) {
        return postAbnormalAction(eventId, "requery", command,
                new TypeReference<CommonResult<com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord>>() {
                });
    }

    /** 批量重新勾兑。 */
    @Override
    public com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult
    batchRequeryChannelMatchAbnormalities(
            com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryCommand command) {
        CommonResult<com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult> result =
                post(servicePaymentUrl(CHANNEL_MATCH_ABNORMAL_PATH + "/batch-requery"), command,
                        new TypeReference<CommonResult<com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult>>() {
                        });
        return unwrapData(result);
    }

    /** 使用交易真实分片时间主动重查并勾兑单笔交易。 */
    @Override
    public ChannelMatchRequeryResponse requeryChannelMatch(
            String transactionId,
            ChannelMatchRequeryRequest request) {
        CommonResult<ChannelMatchRequeryResponse> result = post(
                servicePaymentUrl(CHANNEL_MATCH_REQUERY_PATH + "/" + transactionId + "/requery"),
                request,
                new TypeReference<CommonResult<ChannelMatchRequeryResponse>>() {
                });
        return unwrapData(result);
    }

    /** 关闭或忽略勾兑异常案件。 */
    @Override
    public com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord
    resolveChannelMatchAbnormality(String eventId,
            com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.ResolveCommand command) {
        return postAbnormalAction(eventId, "resolve", command,
                new TypeReference<CommonResult<com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord>>() {
                });
    }

    private <T> T postAbnormalAction(String eventId,
                                     String action,
                                     Object command,
                                     TypeReference<CommonResult<T>> typeReference) {
        CommonResult<T> result = post(
                servicePaymentUrl(CHANNEL_MATCH_ABNORMAL_PATH + "/" + eventId + "/" + action),
                command, typeReference);
        return unwrapData(result);
    }

    private ApprovalResult decideRefund(String approvalId,
                                        String action,
                                        ApprovalClientRequest request) {
        CommonResult<ApprovalResult> result = post(
                servicePaymentUrl(REFUND_APPROVAL_PATH + "/" + approvalId + "/" + action),
                request,
                new TypeReference<CommonResult<ApprovalResult>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部退款命令
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

    /**
     * 将受控 service-payment 相对路径拼接为内部调用地址。
     *
     * @param path 以斜杠开头的内部接口路径
     * @return service-payment 完整内部 URL
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
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
            log.warn("service-payment post call failed, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment post call failed");
        }
    }

    /**
     * 构造带调用方、时间戳、随机 nonce 和内部签名的 JSON 请求实体。
     *
     * <p>签名覆盖 HTTP 方法、路径、时间戳、nonce 和 caller；内部密钥仅参与本地计算，
     * 不写入请求头、请求体或日志。请求体统一使用平台 JSON 工具序列化。</p>
     *
     * @param uri 内部请求 URI
     * @param method HTTP 方法
     * @param body 业务请求对象；GET 等无正文请求可为空
     * @return 包含内部鉴权头和 JSON 正文的请求实体
     */
    private HttpEntity<String> buildSignedEntity(URI uri, HttpMethod method, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String requestBody = body == null ? null : JsonUtils.toJsonString(body);
        String signature = InternalServiceSignature.sign(
                method.name(),
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
     * 根据目标主机选择直连或服务发现 RestTemplate。
     *
     * <p>localhost、IP 和带点的完整域名使用直连模板；无点服务名使用负载均衡模板，
     * 避免将固定地址错误交给服务发现解析。</p>
     *
     * @param uri 已解析的 service-payment 请求 URI
     * @return 与目标地址类型匹配的 RestTemplate
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
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param method HTTP 方法或内部调用方法名，用于构造请求、签名或异常摘要
     * @param uri 请求地址或路径，用于定位内部服务、渠道接口或商户回调目标
     * @param exception 下游调用、校验或持久化阶段捕获的异常对象
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ApiException translateHttpException(HttpMethod method, URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment {} call returned non-success status, targetPath: {}, status: {}, exceptionType: {}",
                method.name(),
                uri.getPath(),
                exception.getStatusCode().value(),
                exception.getClass().getSimpleName());
        if (exception.getStatusCode().value() == 401) {
            return new ApiException(ApiResultEnum.UNAUTHORIZED, "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ApiException(ApiResultEnum.FORBIDDEN, "service-payment call forbidden");
        }
        return new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment call failed");
    }
}

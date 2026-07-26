package com.scott.payment.channel.payment.mpgs;

import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsErrorCodeMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 错误码映射器骨架，位于 payment-channel-library 渠道实现层，后续负责渠道错误码、商户可见错误和内部错误原因分层映射。
 * @status : create
 */
public class MpgsErrorCodeMapper {

    /**
     * DEFAULT FAILED CODE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_FAILED_CODE = "MPGS_FAILED";

    /**
     * 提取渠道响应码。
     *
     * @param response MPGS 响应载荷
     * @return 渠道响应码
     */
    public String responseCode(MpgsResponsePayload response) {
        if (response == null) {
            return "MPGS_EMPTY_RESPONSE";
        }
        if (response.getError() != null && StringUtils.hasText(response.getError().getCause())) {
            return response.getError().getCause();
        }
        if (response.getResponse() != null && StringUtils.hasText(response.getResponse().getAcquirerCode())) {
            return response.getResponse().getAcquirerCode();
        }
        if (response.getResponse() != null && StringUtils.hasText(response.getResponse().getGatewayCode())) {
            return response.getResponse().getGatewayCode();
        }
        return StringUtils.hasText(response.getResult()) ? response.getResult() : DEFAULT_FAILED_CODE;
    }

    /**
     * 提取渠道响应摘要。
     *
     * @param response MPGS 响应载荷
     * @return 脱敏前业务摘要，调用方进入日志前仍需统一脱敏
     */
    public String responseMessage(MpgsResponsePayload response) {
        if (response == null) {
            return "MPGS response is empty";
        }
        if (response.getError() != null && StringUtils.hasText(response.getError().getExplanation())) {
            return response.getError().getExplanation();
        }
        if (response.getResponse() == null) {
            return response.getResult();
        }
        String acquirerMessage = response.getResponse().getAcquirerMessage();
        if (StringUtils.hasText(acquirerMessage)) {
            return acquirerMessage;
        }
        String gatewayRecommendation = response.getResponse().getGatewayRecommendation();
        if (StringUtils.hasText(gatewayRecommendation)) {
            return gatewayRecommendation;
        }
        return response.getResult();
    }
}

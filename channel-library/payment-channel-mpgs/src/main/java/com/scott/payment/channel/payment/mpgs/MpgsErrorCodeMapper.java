package com.scott.payment.channel.payment.mpgs;

import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsErrorCodeMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 错误码映射器骨架，位于 payment-channel-mpgs 渠道实现层，后续负责渠道错误码、商户可见错误和内部错误原因分层映射。
 * @status : create
 */
public class MpgsErrorCodeMapper {

    /**
     * 默认失败编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
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

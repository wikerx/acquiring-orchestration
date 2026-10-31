package com.scott.payment.channel.payment.mpgs.converter;

import com.scott.payment.channel.payment.mpgs.model.response.ResponsePayload;

import org.springframework.util.StringUtils;

/**
 * 按渠道错误、收单响应、网关响应的优先级提取 MPGS 响应码和描述。
 */
public class ResponseCodeMapper {

    /**
     * 渠道未返回任何可识别诊断码时使用的稳定失败编码。
     */
    private static final String DEFAULT_FAILED_CODE = "MPGS_FAILED";

    /**
     * 提取渠道响应码。
     *
     * @param response MPGS 响应载荷
     * @return 渠道响应码
     */
    public String responseCode(ResponsePayload response) {
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
    public String responseMessage(ResponsePayload response) {
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

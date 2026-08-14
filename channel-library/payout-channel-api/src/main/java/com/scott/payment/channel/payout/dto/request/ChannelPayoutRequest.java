package com.scott.payment.channel.payout.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPayoutRequest
 * @date : 2026-08-12 00:00
 * @description : 平台统一代付提交请求，仅包含跨 Provider 稳定字段和受控扩展参数。
 * @status : create
 */
@Data
public class ChannelPayoutRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由选中的代付渠道编码。 */
    private String channelCode;

    /** 平台代付生命周期标识。 */
    private String operationId;

    /** 平台代付订单号。 */
    private String payoutOrderNo;

    /** 平台商户号。 */
    private String merchantId;

    /** 商户代付订单号。 */
    private String merchantOrderNo;

    /** 代付金额，单位为币种主单位。 */
    private BigDecimal amount;

    /** ISO 4217 三位大写币种。 */
    private String currency;

    /** 渠道路由后的收款人令牌或受控引用，禁止承载明文账户凭据。 */
    private String beneficiaryReference;

    /** Provider 差异化参数；敏感值不得进入普通日志或 MQ。 */
    private Map<String, String> extension = new HashMap<>();
}

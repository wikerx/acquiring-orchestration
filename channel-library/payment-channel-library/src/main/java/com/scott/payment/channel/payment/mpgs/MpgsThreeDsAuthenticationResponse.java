package com.scott.payment.channel.payment.mpgs;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * MPGS 3DS Direct API 认证响应摘要。
 */
@Data
public class MpgsThreeDsAuthenticationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 返回结果所属渠道编码。
     */
    private String channelCode;

    /**
     * 平台支付操作单号。
     */
    private String operationId;

    /**
     * 平台交易号。
     */
    private String transactionId;

    /**
     * MPGS 订单号。
     */
    private String channelOrderNo;

    /**
     * MPGS authentication transaction id。
     */
    private String authenticationTransactionId;

    /**
     * MPGS 顶层结果，例如 SUCCESS、FAILURE 或 PENDING。
     */
    private String result;

    /**
     * MPGS gatewayCode 原始编码，用于内部状态映射和排障。
     */
    private String gatewayCode;

    /**
     * MPGS 网关建议，例如 PROCEED 或 DO_NOT_PROCEED。
     */
    private String gatewayRecommendation;

    /**
     * 3DS 认证状态。
     */
    private String authenticationStatus;

    /**
     * 持卡人交互要求，例如 REQUIRED 或 NOT_REQUIRED。
     */
    private String payerInteraction;

    /**
     * 实际使用的 3DS 协议版本。
     */
    private String threeDsVersion;

    /**
     * 通用 3DS 认证交易标识。
     */
    private String threeDsTransactionId;

    /**
     * 3DS Server 交易标识。
     */
    private String threeDsServerTransactionId;

    /**
     * ACS 交易标识。
     */
    private String acsTransactionId;

    /**
     * Directory Server 交易标识。
     */
    private String dsTransactionId;

    /**
     * 电子商务指示码 ECI。
     */
    private String eci;

    /**
     * 持卡人认证值 CAVV，属于敏感认证数据，日志和外部响应必须脱敏。
     */
    private String cavv;

    /**
     * 3DS challenge HTML，可能包含自动提交表单；只允许返回受控收银台，日志保存摘要。
     */
    private String redirectHtml;

    /**
     * 3DS challenge 跳转地址。
     */
    private String redirectUrl;

    /**
     * 平台映射后的渠道响应编码。
     */
    private String responseCode;

    /**
     * 内部渠道响应说明，不直接作为商户或付款人可见原因。
     */
    private String responseMessage;

    /**
     * 已完成敏感字段遮蔽的渠道响应摘要。
     */
    private String rawResponseMasked;

    /**
     * 受控扩展结果，不得包含未脱敏 PAN、CAVV、认证 token 或原始渠道报文。
     */
    private Map<String, String> extension = new HashMap<>();
}

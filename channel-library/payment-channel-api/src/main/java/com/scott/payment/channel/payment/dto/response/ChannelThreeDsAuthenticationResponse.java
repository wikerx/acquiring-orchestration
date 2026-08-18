package com.scott.payment.channel.payment.dto.response;

import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelThreeDsAuthenticationResponse
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一 3DS 认证响应，位于 payment-channel-api DTO 层，隔离渠道原始状态并向支付业务返回受控认证摘要。
 * @status : create
 */
@Data
public class ChannelThreeDsAuthenticationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次响应对应的 3DS 阶段，用于区分 Method HTML 与 ACS Challenge HTML。 */
    private ChannelThreeDsPhase phase;

    /** 平台归一化 3DS 状态；该状态不等同于支付交易状态。 */
    private ChannelThreeDsStatus status;

    /** 实际处理本次认证的渠道编码。 */
    private String channelCode;

    /** 平台支付操作单号，用于关联本次认证动作。 */
    private String operationId;

    /** 平台交易号，用于关联后续支付、查询和审计。 */
    private String transactionId;

    /** 渠道订单号，后续支付或授权应复用同一订单身份。 */
    private String channelOrderNo;

    /** 渠道侧认证交易号，用于渠道查询和结果关联。 */
    private String channelTransactionId;

    /** 渠道请求号；渠道未提供时允许为空。 */
    private String channelRequestId;

    /** 平台生成并发送给渠道的 3DS authentication transaction id。 */
    private String authenticationTransactionId;

    /** 渠道返回的原始 3DS 认证状态，仅供内部状态摘要和排障。 */
    private String threeDsStatus;

    /** 实际使用的 3DS 协议版本。 */
    private String threeDsVersion;

    /** 渠道或 3DS 协议返回的通用认证交易标识。 */
    private String threeDsTransactionId;

    /** 3DS Server 交易标识。 */
    private String threeDsServerTransactionId;

    /** ACS 交易标识。 */
    private String acsTransactionId;

    /** Directory Server 交易标识。 */
    private String dsTransactionId;

    /** 电子商务指示码 ECI，用于后续支付和责任转移判断。 */
    private String eci;

    /** 持卡人认证值 CAVV，属于敏感认证材料，禁止日志、持久化或对外回显。 */
    private String cavv;

    /** 渠道返回的受控 3DS Method 或 ACS Challenge HTML，只允许交给受控收银台执行，禁止普通日志记录。 */
    private String redirectHtml;

    /** 渠道返回的受控 3DS Method 或 ACS Challenge 跳转地址。 */
    private String redirectUrl;

    /** 渠道适配器映射后的认证失败码；结果未明确失败时允许为空。 */
    private String failureCode;

    /** 经脱敏的内部认证失败说明，不直接作为付款人可见提示。 */
    private String failureMessage;

    /** 已完成敏感字段遮蔽和长度控制的渠道响应摘要。 */
    private String rawResponseMasked;

    /** 受控扩展结果，不得包含 PAN、CVV、CAVV、令牌、密钥或完整原始报文。 */
    private Map<String, String> extension = new HashMap<>();
}

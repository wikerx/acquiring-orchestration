package com.scott.payment.channel.payment.dto.request;

import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelThreeDsAuthenticationRequest
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一 3DS 认证请求，位于 payment-channel-api DTO 层，承载平台交易身份、卡数据和浏览器上下文，不包含具体 PSP 协议字段。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelThreeDsAuthenticationRequest extends ChannelPaymentRequest {

    private static final long serialVersionUID = 1L;

    /** 当前只允许执行一个 3DS 阶段；默认从初始化开始。 */
    private ChannelThreeDsPhase phase = ChannelThreeDsPhase.INITIALIZE;

    /** 平台生成并在同一次 3DS 流程中稳定复用的认证交易号，不允许为空。 */
    private String authenticationTransactionId;

    /** 3DS 完成后返回平台受控收银台的地址，不允许来自未校验的外部输入。 */
    private String redirectResponseUrl;

    /** 渠道服务端通知地址，由平台可信配置构造，不允许来自商户输入。 */
    private String notificationUrl;

    /** 3DS 浏览器环境 JSON，可能包含设备信息，只允许在渠道调用链中按最小必要原则传递。 */
    private String browserInfoJson;

    /** 付款人真实 IP，仅在 3DS 瞬时调用链传递，不允许写入日志、缓存或数据库。 */
    private String payerIp;
}

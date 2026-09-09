package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutThreeDsResultDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 3DS 认证结果。
 * @status : create
 */
@Data
public class PaymentCheckoutThreeDsResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次响应对应的统一 3DS 阶段：INITIALIZE 或 AUTHENTICATE。 */
    private String phase;
    /** 平台归一化认证状态，独立于支付交易状态。 */
    private String status;
    /** 平台生成的 3DS 认证交易号。 */
    private String authenticationTransactionId;
    /** 渠道订单号。 */
    private String channelOrderNo;
    /** 渠道交易号。 */
    private String channelTransactionId;
    /** 渠道请求号，用于查询和回调关联。 */
    private String channelRequestId;
    /** 命中的渠道商户配置主键。 */
    private Long channelMidConfigId;
    /** 3DS 与后续资金动作必须复用的渠道编码。 */
    private String channelCode;
    /** 3DS 与后续资金动作必须复用的渠道主键。 */
    private Long channelId;
    /** 命中的 3DS 风控动作：FORCE_3DS、SKIP_3DS 或 NONE。 */
    private String threeDsPolicyAction;
    /** 命中的 3DS 风控规则主键；未命中时为空。 */
    private Long threeDsPolicyRuleId;
    /** 渠道返回的 3DS 认证状态。 */
    private String threeDsStatus;
    /** 3DS 协议版本。 */
    private String threeDsVersion;
    /** 平台或渠道侧统一 3DS 交易标识。 */
    private String threeDsTransactionId;
    /** 3DS Server 交易标识。 */
    private String threeDsServerTransactionId;
    /** ACS 交易标识。 */
    private String acsTransactionId;
    /** Directory Server 交易标识。 */
    private String dsTransactionId;
    /** 电子商务指示码。 */
    private String eci;
    /** 持卡人认证值，属于敏感认证材料，禁止写入日志或对外回显。 */
    private String cavv;
    /** 渠道返回的受控 3DS Method 或 ACS Challenge HTML，禁止写入普通日志。 */
    private String redirectHtml;
    /** 3DS Method 或付款人 ACS Challenge 跳转地址。 */
    private String redirectUrl;
    /** 归一化认证失败码。 */
    private String failureCode;
    /** 经脱敏的认证失败说明。 */
    private String failureMessage;
    /** 已脱敏和截断的渠道响应摘要，不得包含 CAVV 或令牌明文。 */
    private String rawResponseMasked;

    /**
     * 判断认证是否已通过。
     *
     * @return true 表示可以继续提交支付渠道
     */
    public boolean passed() {
        return "PASSED".equals(status);
    }

    /**
     * 判断是否需要付款人完成 3DS 挑战。
     *
     * @return true 表示应向前端返回受控 3DS 动作
     */
    public boolean challengeRequired() {
        return "CHALLENGE_REQUIRED".equals(status);
    }

    /** @return true 表示必须先在浏览器执行 3DS Method HTML。 */
    public boolean methodRequired() {
        return "METHOD_REQUIRED".equals(status);
    }

    /**
     * 判断认证是否明确失败。
     *
     * @return true 表示本次支付尝试应按失败处理
     */
    public boolean failed() {
        return "FAILED".equals(status);
    }

    /**
     * 判断认证结果是否仍未确定。
     *
     * @return true 表示后续需要回调或查询推进
     */
    public boolean processing() {
        return "PROCESSING".equals(status);
    }

    /**
     * 判断当前路由不需要 3DS，可继续执行普通支付或授权。
     *
     * @return true 表示未命中强制 3DS 策略
     */
    public boolean notRequired() {
        return "NOT_REQUIRED".equals(status);
    }
}

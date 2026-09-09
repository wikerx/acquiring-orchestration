package com.scott.payment.channel.payment.mpgs;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsRequestPayload
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 请求载荷模型，位于 payment-channel-mpgs 渠道实现层，仅用于序列化发送给 MPGS，不允许作为平台接口 DTO 暴露。
 * @status : create
 */
@Data
public class MpgsRequestPayload {

    /**
     * MPGS API 操作，例如 PAY、AUTHORIZE、CAPTURE、REFUND、VOID、UPDATE_AUTHORIZATION。
     */
    private String apiOperation;

    /**
     * MPGS 订单信息，首次支付或授权时承载订单金额、币种和平台商户订单号。
     */
    private Order order;

    /**
     * MPGS 交易动作信息，后续请款、退款、增量授权和撤销时承载动作金额、币种或目标交易号。
     */
    private Transaction transaction;

    /**
     * MPGS 资金来源信息，当前仅接入卡支付；包含 PAN 和 CVV，日志必须脱敏。
     */
    private SourceOfFunds sourceOfFunds;

    /**
     * MPGS 3DS 认证信息，CAVV / authenticationToken 属于认证敏感值，日志必须脱敏。
     */
    private Authentication authentication;

    /**
     * MPGS 付款人浏览器上下文，仅在 Authenticate Payer 阶段发送。
     */
    private Device device;

    /** MPGS 付款人账单地址，仅在 Authenticate Payer 阶段按已有资料发送。 */
    private Billing billing;

    /** MPGS 付款人资料，仅在 Authenticate Payer 阶段按已有资料发送。 */
    private Customer customer;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Order
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的订单节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Order {

        /**
         * 订单金额，主币种单位字符串，避免浮点精度损失。
         */
        private String amount;

        /**
         * 订单币种，ISO 4217 三位大写代码。
         */
        private String currency;

        /**
         * 平台商户订单号，用于 MPGS order.reference。
         */
        private String reference;

        /** Webhook notification URL, supported on INITIATE AUTHENTICATION. */
        private String notificationUrl;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Transaction
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的交易节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Transaction {

        /**
         * 当前交易动作金额，请款、退款、增量授权等后续动作使用。
         */
        private String amount;

        /**
         * 当前交易动作币种。
         */
        private String currency;

        /**
         * 当前平台交易动作单号，用于 MPGS transaction.reference。
         */
        private String reference;

        /**
         * 目标 MPGS transactionId，撤销或冲正时用于指向原授权/支付动作。
         */
        private String targetTransactionId;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SourceOfFunds
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的资金来源节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class SourceOfFunds {

        /**
         * 资金来源类型，当前固定为 CARD。
         */
        private String type;

        /**
         * 付款人提供的资金来源详情。
         */
        private Provided provided;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Provided
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的支付工具节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Provided {

        /**
         * 付款卡信息，包含敏感 PAN 和 CVV。
         */
        private Card card;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Card
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的卡节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Card {

        /**
         * PAN 卡号，日志、MQ 和落库前必须脱敏或禁止输出。
         */
        private String number;

        /**
         * 卡有效期。
         */
        private Expiry expiry;

        /**
         * CVV/CVC 安全码，只允许内存渠道调用，不允许日志、MQ 或落库。
         */
        private String securityCode;

        /** 卡面持卡人姓名，PAYER_BROWSER 认证要求提供。 */
        private String nameOnCard;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Expiry
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的有效期节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Expiry {

        /**
         * 两位有效期月份，例如 01。
         */
        private String month;

        /**
         * 两位有效期年份，例如 39。
         */
        private String year;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Authentication
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的认证节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Authentication {

        /** Initiate Authentication 接受的协议版本，当前平台只启用 EMV 3DS2。 */
        private String acceptVersions;

        /** Initiate Authentication 的付款人交互渠道。 */
        private String channel;

        /** 本次认证用途，支付或授权统一使用 PAYMENT_TRANSACTION。 */
        private String purpose;

        /**
         * MPGS 认证交易 ID，PAY/AUTHORIZE 时用于引用已完成的 3DS 认证。
         */
        private String transactionId;

        /**
         * 3DS 协议版本，由网关认证响应返回。
         */
        private String version;

        /**
         * 持卡人交互类型，例如 REQUIRED、NOT_REQUIRED。
         */
        private String payerInteraction;

        /** Authenticate Payer 完成后的平台受控回跳地址。 */
        private String redirectResponseUrl;

        /**
         * 认证重定向或 Method HTML。
         */
        private Redirect redirect;

        /**
         * 通用 3DS 认证数据。
         */
        private ThreeDs threeDs;

        /**
         * 3DS1 兼容字段。
         */
        private ThreeDs1 threeDs1;

        /**
         * 3DS2 兼容字段。
         */
        private ThreeDs2 threeDs2;
    }

    /** MPGS 付款人设备信息。 */
    @Data
    public static class Device {

        /** 浏览器 User-Agent，MPGS 在 PAYER_BROWSER 模式下要求提供。 */
        private String browser;

        /** 付款人真实 IPv4/IPv6 地址。 */
        private String ipAddress;

        /** EMV 3DS 浏览器能力详情。 */
        private BrowserDetails browserDetails;
    }

    /** MPGS 账单信息。 */
    @Data
    public static class Billing {
        private Address address;
    }

    /** MPGS 地址字段。 */
    @Data
    public static class Address {
        private String city;
        private String country;
        private String postcodeZip;
        private String stateProvince;
        private String street;
    }

    /** MPGS 付款人字段。 */
    @Data
    public static class Customer {
        private String email;
        private String firstName;
        private String lastName;
        private String mobilePhone;
    }

    /** MPGS Authenticate Payer 浏览器能力详情。 */
    @Data
    public static class BrowserDetails {

        /** ACS challenge 展示尺寸。 */
        @JSONField(name = "3DSecureChallengeWindowSize")
        private String challengeWindowSize;

        /** 浏览器 Accept 请求头。 */
        private String acceptHeaders;

        /** 屏幕颜色深度。 */
        private Integer colorDepth;

        /** 浏览器是否启用 Java。 */
        private Boolean javaEnabled;

        /** 浏览器是否启用 JavaScript。 */
        private Boolean javaScriptEnabled;

        /** 浏览器语言。 */
        private String language;

        /** 屏幕高度。 */
        private Integer screenHeight;

        /** 屏幕宽度。 */
        private Integer screenWidth;

        /** 本地时间与 UTC 的分钟偏移。 */
        private Integer timeZone;
    }

    /**
     * MPGS 3DS 重定向内容，供平台收银台渲染 challenge，不允许写入普通业务日志。
     */
    @Data
    public static class Redirect {

        /**
         * 需由浏览器渲染的 MPGS 3DS HTML，可能包含自动提交表单。
         */
        private String html;

        /**
         * 跳转 URL，部分 MPGS 响应可能返回该字段。
         */
        private String url;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的3DS节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class ThreeDs {

        /**
         * Directory Server 交易 ID。
         */
        private String transactionId;

        /**
         * ECI 值。
         */
        private String acsEci;

        /**
         * CAVV / AAV 认证 token，日志必须隐藏。
         */
        private String authenticationToken;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs1
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的3DS 1.x节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class ThreeDs1 {

        /**
         * 3DS1 PaRes 状态。
         */
        private String paResStatus;

        /**
         * 3DS1 VEReq Enrollment 状态。
         */
        private String veResEnrolled;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs2
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 请求报文的3DS 2.x节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class ThreeDs2 {

        /**
         * 3DS2 交易认证状态。
         */
        private String transactionStatus;
    }
}

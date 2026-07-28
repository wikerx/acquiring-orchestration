package com.scott.payment.channel.payment.mpgs;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsRequestPayload
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 请求载荷模型，位于 payment-channel-library 渠道实现层，仅用于序列化发送给 MPGS，不允许作为平台接口 DTO 暴露。
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
     * MPGS 浏览器返回配置，3DS 回跳 URL 由平台生成的一次性 token 保护。
     */
    private BrowserPayment browserPayment;

    /**
     * MPGS 3DS 认证信息，CAVV / authenticationToken 属于认证敏感值，日志必须脱敏。
     */
    private Authentication authentication;

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Order
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Order 协作组件，位于 渠道适配库，封装 订单 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Transaction
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Transaction 协作组件，位于 渠道适配库，封装 交易 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SourceOfFunds
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Source Of Funds 协作组件，位于 渠道适配库，封装 来源offunds 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Provided
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Provided 协作组件，位于 渠道适配库，封装 provided 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Provided {

        /**
         * 付款卡信息，包含敏感 PAN 和 CVV。
         */
        private Card card;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Card
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Card 协作组件，位于 渠道适配库，封装 card 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Expiry
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Expiry 协作组件，位于 渠道适配库，封装 expiry 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Authentication
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Authentication 协作组件，位于 渠道适配库，封装 authentication 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Authentication {

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

    @Data
    public static class BrowserPayment {

        /**
         * 3DS challenge 完成后 MPGS/ACS 回跳到平台收银台的地址。
         */
        private String returnUrl;
    }

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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Three Ds 协作组件，位于 渠道适配库，封装 threeds 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs1
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Three Ds 1 协作组件，位于 渠道适配库，封装 threeds1 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs2
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Three Ds 2 协作组件，位于 渠道适配库，封装 threeds2 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class ThreeDs2 {

        /**
         * 3DS2 交易认证状态。
         */
        private String transactionStatus;
    }
}

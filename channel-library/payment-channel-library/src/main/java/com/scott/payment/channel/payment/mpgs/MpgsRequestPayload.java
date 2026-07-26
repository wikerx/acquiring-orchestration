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
     * @description : Order Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : Transaction Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : SourceOfFunds Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : Provided Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : Card Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : Expiry Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : Authentication Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class Authentication {

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
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDs
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : ThreeDs Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : ThreeDs1 Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
     * @description : ThreeDs2 Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class ThreeDs2 {

        /**
         * 3DS2 交易认证状态。
         */
        private String transactionStatus;
    }
}

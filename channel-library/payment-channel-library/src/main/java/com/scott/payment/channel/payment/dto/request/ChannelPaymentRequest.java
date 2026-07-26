package com.scott.payment.channel.payment.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPaymentRequest
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一请求基类，位于 payment-channel-library DTO 层，用于承载渠道调用所需交易、金额、卡、账单和 3DS 上下文；敏感字段只允许内存使用。
 * @status : create
 */
@Data
public class ChannelPaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码，例如 MPGS。
     */
    private String channelCode;

    /**
     * 平台内部生命周期关联标识，同一原始交易生命周期共用，不直接上送渠道。
     */
    private String operationId;

    /**
     * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 原平台交易 ID，后续动作关联授权、支付或请款时使用。
     */
    private String sourceTransactionId;

    /**
     * 渠道订单号；MPGS 使用原始授权/支付的平台 transactionId。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；MPGS 使用平台生成的 channel_transaction_id，部分渠道可为空。
     */
    private String channelTransactionId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 交易类型，对齐 transaction_type。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 交易金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 交易币种，ISO 4217 三位大写代码。
     */
    private String currency;

    /**
     * 交易业务时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * PAN 卡号，只允许内存渠道调用，不允许明文日志、MQ 或落库。
     */
    private String cardNo;

    /**
     * 卡有效期月份。
     */
    private String expirationMonth;

    /**
     * 卡有效期年份。
     */
    private String expirationYear;

    /**
     * CVV/CVC 安全码，只允许内存渠道调用，不允许明文日志、MQ 或落库。
     */
    private String securityCode;

    /**
     * 卡品牌。
     */
    private String cardBrand;

    /**
     * 持卡人账单信息。
     */
    private BillingInfo billingInfo;

    /**
     * 3DS 认证信息。
     */
    private ThreeDsInfo threeDsInfo;

    /**
     * 渠道差异化扩展参数，不建议承载通用核心字段。
     */
    private Map<String, String> extension = new HashMap<>();

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingInfo
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : BillingInfo 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class BillingInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * first Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String firstName;

        /**
         * last Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String lastName;

        /**
         * phone 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String phone;

        /**
         * email 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String email;

        /**
         * country 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String country;

        /**
         * state 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String state;

        /**
         * city 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String city;

        /**
         * street 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String street;

        /**
         * postal 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String postal;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDsInfo
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : ThreeDsInfo 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class ThreeDsInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * eci 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        /**
         * ds Transaction Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String dsTransactionId;

        /**
         * three Ds Version 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String threeDsVersion;
    }
}

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
 * @description : 收单渠道统一请求基类，位于 payment-channel-api DTO 层，用于承载渠道调用所需交易、金额、卡、账单和 3DS 上下文；敏感字段只允许内存使用。
 * @status : create
 */
@Data
public class ChannelPaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台登记的渠道编码，由路由结果提供。
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
     * 渠道订单号；首次支付或授权与后续交易必须复用同一渠道订单身份。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；用于查询和关联后续动作，部分渠道在请求阶段可为空。
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
     * @description : Billing Info 协作组件，位于 渠道适配库，封装 billinginfo 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class BillingInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * first Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String firstName;

        /**
         * last Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String lastName;

        /**
         * phone，用于保存 Billing Info 中与 phone 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String phone;

        /**
         * email，用于保存 Billing Info 中与 email 相关的业务属性。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String email;

        /**
         * country，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String country;

        /**
         * state，用于保存 Billing Info 中与 state 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String state;

        /**
         * city，用于保存 Billing Info 中与 city 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String city;

        /**
         * street，用于保存 Billing Info 中与 street 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String street;

        /**
         * postal，用于保存 Billing Info 中与 postal 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * @description : Three Ds Info 协作组件，位于 渠道适配库，封装 threedsinfo 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class ThreeDsInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 渠道 3DS authentication transaction id，支付或授权时必须引用同一 provider 的已通过认证交易。
         */
        private String authenticationTransactionId;

        /**
         * eci，用于保存 Three Ds Info 中与 eci 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String dsTransactionId;

        /**
         * three Ds Version，用于保存 Three Ds Info 中与 threedsversion 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String threeDsVersion;
    }
}

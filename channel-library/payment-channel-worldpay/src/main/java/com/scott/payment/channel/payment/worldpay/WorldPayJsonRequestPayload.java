package com.scott.payment.channel.payment.worldpay;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonRequestPayload
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON 请求载荷模型，位于 payment-channel-worldpay 渠道协议层，按 Access Worldpay JSON Payments/Card Payments 报文结构承载交易引用、商户实体、金额最小单位、卡支付工具和后续动作引用，不作为平台对外 DTO 暴露。
 * @status : create
 */
@Data
public class WorldPayJsonRequestPayload {

    /**
     * 平台生成的渠道交易引用，Worldpay 用于幂等、查询和排查。
     * <p>
     * 单位：无；格式：平台渠道交易号或动作请求号；不允许为空；非敏感字段。
     * 数据来源：service-payment 预生成的 channelTransactionId/requestId；与 channelOrderNo、operationId 共同定位一次渠道调用。
     * </p>
     */
    private String transactionReference;

    /**
     * 商户在 Worldpay 的实体信息。
     * <p>
     * 单位：无；格式：对象；不允许为空；非敏感字段但日志只输出 MID 摘要。
     * 数据来源：后台渠道 MID 元数据中的 Worldpay gateway merchant code。
     * </p>
     */
    private Merchant merchant;

    /**
     * 支付或后续动作指令。
     * <p>
     * 单位：无；格式：对象；首笔卡交易不允许为空；包含金额最小单位、币种、支付工具和收银描述。
     * 数据来源：平台交易请求、币种精度和卡资料；PAN、CVC 只允许当前 HTTP 请求内短暂使用。
     * </p>
     */
    private Instruction instruction;

    /**
     * 交易发起通道。
     * <p>
     * 单位：无；格式：枚举字符串，例如 ecommerce/moto；允许为空，默认由 mapper 置为 ecommerce。
     * 数据来源：MID 元数据或平台渠道扩展字段；影响 Worldpay 风控和认证路径。
     * </p>
     */
    private String channel;

    /**
     * 商户订单引用，便于 Worldpay 后台按商户订单维度检索。
     * <p>
     * 单位：无；格式：1 至 64 位业务编号；允许为空；非敏感字段。
     * 数据来源：平台 channelOrderNo 或 merchantOrderNo；与 transactionReference 不要求相同。
     * </p>
     */
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    private String orderReference;

    /**
     * 后续动作金额。
     * <p>
     * 单位：币种最小单位；格式：整数 amount 加 ISO 4217 currency；请款、退款等金额类后续动作不允许为空；非敏感字段。
     * 数据来源：平台动作请求金额和币种精度；与 action link 共同定位并执行渠道后续动作。
     * </p>
     */
    private Value value;

    /**
     * 平台动作类型，作为非协议审计字段随可配置 endpoint 上送时便于渠道排查。
     * <p>
     * 单位：无；格式：PAYMENT、AUTHORIZE、CAPTURE、REFUND、VOID 或 QUERY；不允许为空；非敏感字段。
     * 数据来源：平台 transactionType 映射结果；不参与平台状态推进。
     * </p>
     */
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    private String operation;

    /**
     * 后续动作目标链接或查询链接。
     * <p>
     * 单位：无；格式：Worldpay 返回的 action href 或平台配置的 path；后续动作需要；非敏感字段但 URL query 会在日志中脱敏。
     * 数据来源：上次渠道响应 rawResponse 或 MID 元数据配置；与 sourceTransactionId 一起定位原渠道交易。
     * </p>
     */
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    private String actionLink;

    /**
     * 渠道请求扩展字段。
     * <p>
     * 单位：无；格式：扁平键值；允许为空；不得放入密码、Authorization、完整 PAN、CVV、JWT 或密钥。
     * 数据来源：受控 MID 元数据；用于补充 Worldpay 可选协议字段。
     * </p>
     */
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Merchant
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON merchant 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装商户在 Worldpay 的 entity 编码；输入来自后台 MID 配置，输出到 Access Worldpay JSON 请求体。
     * @status : create
     */
    @Data
    public static class Merchant {

        /**
         * Worldpay merchant entity / gateway merchant code。
         * <p>
         * 单位：无；格式：Worldpay 分配的商户实体编码；不允许为空；非敏感字段但日志只输出摘要。
         * 数据来源：后台 MID 元数据；与 Basic Auth 账号共同决定交易归属。
         * </p>
         */
        private String entity;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Instruction
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON instruction 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装卡交易方法、自动请款、认证结果、账单描述、金额和支付工具；不负责 HTTP 调用或平台状态推进。
     * @status : create
     */
    @Data
    public static class Instruction {

        /**
         * 付款方式。
         * <p>
         * 单位：无；格式：card 或渠道支持的受控字符串；不允许为空；非敏感字段。
         * 数据来源：paymentMethod 和 MID 元数据；与 paymentInstrument.type 共同表达卡交易。
         * </p>
         */
        private String method;

        /**
         * 自动请款指令。
         * <p>
         * 单位：无；格式：enabled 布尔值；支付类交易按配置决定，授权类交易通常为 false；非敏感字段。
         * 数据来源：平台交易类型和 MID 元数据；决定 Worldpay 授权后是否自动进入 settlement。
         * </p>
         */
        private RequestAutoSettlement requestAutoSettlement;

        /**
         * 认证结果。
         * <p>
         * 单位：无；格式：对象；仅在商户已经完成 3DS 等认证并需要随 Worldpay 请求提交时出现；CAVV 属于敏感认证值。
         * 数据来源：商户 OpenAPI 请求或收银台认证结果；与 paymentInstrument 一起影响责任转移和渠道风控。
         * </p>
         */
        private Authentication authentication;

        /**
         * 商户账单描述。
         * <p>
         * 单位：无；格式：字符串；允许为空；非敏感字段。
         * 数据来源：MID 元数据 statementNarrative/narrative；展示在持卡人账单或渠道后台时需保持可识别。
         * </p>
         */
        private Narrative narrative;

        /**
         * 交易金额。
         * <p>
         * 单位：币种最小单位；格式：整数 amount 加 ISO 4217 currency；金额类交易不允许为空；非敏感字段。
         * 数据来源：平台交易金额和数据库/ISO 字典解析出的 currencyExponent；与 currency 字段共同解释金额。
         * </p>
         */
        private Value value;

        /**
         * 支付工具。
         * <p>
         * 单位：无；格式：对象；首笔卡交易不允许为空；包含 PAN、有效期和 CVC。
         * 数据来源：OpenAPI 到 payment 的内存链路；高敏字段禁止落库和明文日志。
         * </p>
         */
        private PaymentInstrument paymentInstrument;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RequestAutoSettlement
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON requestAutoSettlement 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装是否请求渠道授权后自动请款；输入来自交易类型和 MID 元数据。
     * @status : create
     */
    @Data
    public static class RequestAutoSettlement {

        /**
         * 是否请求 Worldpay 自动请款。
         * <p>
         * 单位：无；格式：true 或 false；不允许为空；非敏感字段。
         * 数据来源：平台交易类型和 MID 元数据；PAYMENT 默认 true，授权和预授权默认 false。
         * </p>
         */
        private Boolean enabled;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Narrative
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON narrative 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装持卡人账单或渠道后台可见的商户描述；不允许承载卡号、密钥或个人敏感值。
     * @status : create
     */
    @Data
    public static class Narrative {

        /**
         * 出现在持卡人账单或渠道后台的商户描述。
         * <p>
         * 单位：无；格式：渠道允许的短文本；允许为空；非敏感字段。
         * 数据来源：MID 元数据；不应包含卡号、邮箱、电话或密钥。
         * </p>
         */
        private String line1;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Value
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON value 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装最小辅币单位金额和 ISO 币种；输入来自平台主币种金额按辅币位转换后的结果。
     * @status : create
     */
    @Data
    public static class Value {

        /**
         * 最小辅币单位金额。
         * <p>
         * 单位：由 currency 的辅币位决定，例如 USD 12.34 为 1234；不允许为空；非敏感字段。
         * 数据来源：平台交易金额按 currencyExponent 无舍入转换；与 currency 必须同时出现。
         * </p>
         */
        private Long amount;

        /**
         * 交易币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写代码；不允许为空；非敏感字段。
         * 数据来源：支付核心归一化后的 transactionCurrency；决定 amount 的辅币位解释。
         * </p>
         */
        private String currency;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PaymentInstrument
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON paymentInstrument 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装明文卡号、有效期、CVC、卡品牌、持卡人姓名和账单地址；PAN/CVC 只允许当前渠道请求内短暂使用。
     * @status : create
     */
    @Data
    public static class PaymentInstrument {

        /**
         * 支付工具类型。
         * <p>
         * 单位：无；格式：plain 或渠道支持的 token 类型；首笔明文卡交易为 plain；非敏感字段。
         * 数据来源：MID 元数据 paymentInstrumentType，未配置时为 plain。
         * </p>
         */
        private String type;

        /**
         * PAN 卡号。
         * <p>
         * 单位：无；格式：13 至 19 位数字；首笔卡交易不允许为空；高敏感字段。
         * 数据来源：OpenAPI 请求解密后的卡信息；仅允许当前渠道请求内使用，禁止日志、异常、MQ 和落库明文保存。
         * </p>
         */
        private String cardNumber;

        /**
         * 卡有效期。
         * <p>
         * 单位：无；格式：month 两位、year 四位；首笔卡交易不允许为空；非敏感认证辅助字段。
         * 数据来源：OpenAPI 请求卡信息；与 cardNumber 一起提交给渠道。
         * </p>
         */
        private ExpiryDate cardExpiryDate;

        /**
         * CVV/CVC 安全码。
         * <p>
         * 单位：无；格式：3 至 4 位数字；首笔卡交易不允许为空；高敏感认证数据。
         * 数据来源：OpenAPI 请求解密后的卡信息；禁止落库、日志、异常消息和 MQ。
         * </p>
         */
        private String cvc;

        /**
         * 卡品牌。
         * <p>
         * 单位：无；格式：VISA、MASTERCARD 等；允许为空；非敏感字段。
         * 数据来源：平台卡识别或上游请求；用于渠道可选增强字段和排查。
         * </p>
         */
        private String cardBrand;

        /**
         * 持卡人姓名。
         * <p>
         * 单位：无；格式：渠道允许的姓名文本；允许为空；个人信息字段，日志只允许脱敏或摘要输出。
         * 数据来源：账单持卡人信息或请求扩展 cardHolderName；与 billingAddress 一起用于 AVS 和渠道风控。
         * </p>
         */
        private String cardHolderName;

        /**
         * 账单地址。
         * <p>
         * 单位：无；格式：对象；允许为空；包含地址和邮编等个人信息，日志必须走统一 JSON 脱敏。
         * 数据来源：商户传入的 billingCardHolderInfo；与卡交易一并提交给 Worldpay。
         * </p>
         */
        private BillingAddress billingAddress;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ExpiryDate
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON expiryDate 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装卡有效期月份和年份；输入来自商户请求解密后的卡信息。
     * @status : create
     */
    @Data
    public static class ExpiryDate {

        /**
         * 有效期月份。
         * <p>
         * 单位：月；格式：两位数字 01 至 12；不允许为空；非敏感字段。
         * 数据来源：OpenAPI 卡信息；与 year 共同描述卡有效期。
         * </p>
         */
        private String month;

        /**
         * 有效期年份。
         * <p>
         * 单位：年；格式：四位数字；不允许为空；非敏感字段。
         * 数据来源：OpenAPI 卡信息；与 month 共同描述卡有效期。
         * </p>
         */
        private String year;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingAddress
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON billingAddress 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装持卡人账单街道、邮编、城市、州省和国家地区代码；字段属于个人信息，日志必须脱敏。
     * @status : create
     */
    @Data
    public static class BillingAddress {

        /**
         * 街道地址。
         * <p>
         * 单位：无；格式：字符串；允许为空；个人信息字段。
         * 数据来源：billingInfo.street；用于渠道 AVS 和风控。
         * </p>
         */
        private String address1;

        /**
         * 邮政编码。
         * <p>
         * 单位：无；格式：国家地区相关邮编格式；允许为空；个人信息字段。
         * 数据来源：billingInfo.postal；与 countryCode 共同解释地址。
         * </p>
         */
        private String postalCode;

        /**
         * 城市。
         * <p>
         * 单位：无；格式：城市名称；允许为空；个人信息字段。
         * 数据来源：billingInfo.city；用于渠道 AVS 和风控。
         * </p>
         */
        private String city;

        /**
         * 州、省或地区。
         * <p>
         * 单位：无；格式：地区缩写或名称；允许为空；非敏感但可能属于个人账单地址。
         * 数据来源：billingInfo.state；与 countryCode 共同描述地址区域。
         * </p>
         */
        private String state;

        /**
         * 国家或地区代码。
         * <p>
         * 单位：无；格式：ISO 3166 国家地区代码；允许为空；非敏感字段。
         * 数据来源：billingInfo.country；用于渠道 AVS、风控和合规检查。
         * </p>
         */
        private String countryCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Authentication
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON authentication 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装商户或收银台完成的 3DS 认证结果；用于提交给渠道判断责任转移和风险。
     * @status : create
     */
    @Data
    public static class Authentication {

        /**
         * 3DS 认证结果。
         * <p>
         * 单位：无；格式：对象；仅在商户侧或收银台已完成 3DS 后出现；CAVV 不允许明文日志输出。
         * 数据来源：threeDsInfo；影响 Worldpay 对交易责任转移和风险结果的判断。
         * </p>
         */
        private ThreeDS threeDS;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDS
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : Worldpay JSON threeDS 节点对象，位于 payment-channel-worldpay 渠道协议模型层，封装 3DS 版本、ECI、认证值和 DS 交易号；authenticationValue 属于敏感认证数据。
     * @status : create
     */
    @Data
    public static class ThreeDS {

        /**
         * 3DS 协议版本。
         * <p>
         * 单位：无；格式：例如 2.1.0、2.2.0；允许为空；非敏感字段。
         * 数据来源：threeDsInfo.threeDsVersion；与 ECI、认证值和 DS 交易号共同描述认证结果。
         * </p>
         */
        private String version;

        /**
         * 电子商务交易指示值。
         * <p>
         * 单位：无；格式：卡组织定义的 ECI 代码；允许为空；非敏感字段。
         * 数据来源：threeDsInfo.eci；与 authenticationValue 共同影响责任转移。
         * </p>
         */
        private String eci;

        /**
         * 认证值。
         * <p>
         * 高敏感认证数据；格式由 ACS/DS 返回；禁止日志、落库和异常消息明文输出。
         * 数据来源：threeDsInfo.cavv。
         * </p>
         */
        private String authenticationValue;

        /**
         * Directory Server 交易号。
         * <p>
         * 单位：无；格式：UUID 或 DS 返回标识；允许为空；非敏感但用于认证链路排查。
         * 数据来源：threeDsInfo.dsTransactionId；与 authenticationValue 关联同一次 3DS 认证。
         * </p>
         */
        private String dsTransactionId;
    }
}

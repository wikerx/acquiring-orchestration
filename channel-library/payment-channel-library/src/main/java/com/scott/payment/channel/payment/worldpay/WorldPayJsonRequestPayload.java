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
 * @description : WorldPay JSON 请求载荷模型，位于 payment-channel-library 渠道协议层，按 Access Worldpay JSON Payments/Card Payments 报文结构承载交易引用、商户实体、金额最小单位、卡支付工具和后续动作引用，不作为平台对外 DTO 暴露。
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
    private String orderReference;

    /**
     * 平台动作类型，作为非协议审计字段随可配置 endpoint 上送时便于渠道排查。
     * <p>
     * 单位：无；格式：PAYMENT、AUTHORIZE、CAPTURE、REFUND、VOID 或 QUERY；不允许为空；非敏感字段。
     * 数据来源：平台 transactionType 映射结果；不参与平台状态推进。
     * </p>
     */
    private String operation;

    /**
     * 后续动作目标链接或查询链接。
     * <p>
     * 单位：无；格式：Worldpay 返回的 action href 或平台配置的 path；后续动作需要；非敏感字段但 URL query 会在日志中脱敏。
     * 数据来源：上次渠道响应 rawResponse 或 MID 元数据配置；与 sourceTransactionId 一起定位原渠道交易。
     * </p>
     */
    private String actionLink;

    /**
     * 渠道请求扩展字段。
     * <p>
     * 单位：无；格式：扁平键值；允许为空；不得放入密码、Authorization、完整 PAN、CVV、JWT 或密钥。
     * 数据来源：受控 MID 元数据；用于补充 Worldpay 可选协议字段。
     * </p>
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Worldpay 商户实体节点。
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
     * Worldpay 交易指令节点。
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
     * Worldpay 自动请款节点。
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
     * Worldpay 账单描述节点。
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
     * Worldpay 金额节点。
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
     * Worldpay 卡支付工具节点。
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
        private ExpiryDate expiryDate;

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
    }

    /**
     * Worldpay 卡有效期节点。
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
}

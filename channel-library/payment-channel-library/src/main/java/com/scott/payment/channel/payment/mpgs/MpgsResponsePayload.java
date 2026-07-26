package com.scott.payment.channel.payment.mpgs;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponsePayload
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应载荷模型，位于 payment-channel-library 渠道实现层，仅承接渠道原始响应并映射为统一渠道响应。
 * @status : create
 */
@Data
public class MpgsResponsePayload {

    /**
     * MPGS 顶层 result，例如 SUCCESS、ERROR、PENDING、UNKNOWN。
     */
    private String result;

    /**
     * MPGS 网关入口标识，保留用于排查渠道侧链路。
     */
    private String gatewayEntryPoint;

    /**
     * MPGS 商户号。
     */
    private String merchant;

    /**
     * MPGS API 版本号。
     */
    private String version;

    /**
     * MPGS 网关响应摘要，包含网关码、收单响应和 CSC 校验结果。
     */
    private Response response;

    /**
     * MPGS 授权响应明细，包含收单响应码、STAN、交易识别号等对账排查字段。
     */
    private AuthorizationResponse authorizationResponse;

    /**
     * MPGS 当前交易动作信息。
     */
    private Transaction transaction;

    /**
     * MPGS 订单信息。
     */
    private Order order;

    /**
     * MPGS 错误信息，result=ERROR 时通常存在。
     */
    private ErrorPayload error;

    /**
     * MPGS 支付资金来源摘要，例如卡品牌、发卡国家、资金类型和脱敏卡号。
     */
    private SourceOfFunds sourceOfFunds;

    /**
     * MPGS 风险评估结果，当前只作为后台排查字段，不参与平台风控状态机。
     */
    private Risk risk;

    /**
     * MPGS 记录时间，ISO-8601 字符串。
     */
    private String timeOfRecord;

    /**
     * MPGS 最近更新时间，ISO-8601 字符串。
     */
    private String timeOfLastUpdate;

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Response
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Response 传输模型，位于 渠道适配库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class Response {

        /**
         * MPGS 网关响应码，例如 APPROVED、DECLINED。
         */
        private String gatewayCode;

        /**
         * MPGS 网关处理建议。
         */
        private String gatewayRecommendation;

        /**
         * 收单机构响应码。
         */
        private String acquirerCode;

        /**
         * 收单机构响应描述，可能含渠道原始失败原因。
         */
        private String acquirerMessage;

        /**
         * 卡安全码校验结果，不是原始 CVV，但仍只作为内部排查字段使用。
         */
        private CardSecurityCode cardSecurityCode;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardSecurityCode
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Card Security Code 协作组件，位于 渠道适配库，封装 cardsecuritycode 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class CardSecurityCode {

        /**
         * MPGS CSC 校验结果。
         */
        private String gatewayCode;

        /**
         * 收单机构 CSC 校验结果。
         */
        private String acquirerCode;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AuthorizationResponse
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Authorization Response 传输模型，位于 渠道适配库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AuthorizationResponse {

        /**
         * 商务卡信息。
         */
        private String commercialCard;

        /**
         * 商务卡标识。
         */
        private String commercialCardIndicator;

        /**
         * 金融网络编码。
         */
        private String financialNetworkCode;

        /**
         * POS 数据。
         */
        private String posData;

        /**
         * POS 录入模式。
         */
        private String posEntryMode;

        /**
         * 处理码。
         */
        private String processingCode;

        /**
         * 授权响应码。
         */
        private String responseCode;

        /**
         * 系统跟踪审计号。
         */
        private String stan;

        /**
         * 授权响应交易识别号。
         */
        private String transactionIdentifier;
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
         * MPGS 交易 ID。
         */
        private String id;

        /**
         * MPGS 交易类型。
         */
        private String type;

        /**
         * MPGS 交易金额，原始字符串。
         */
        private BigDecimal amount;

        /**
         * MPGS 交易币种。
         */
        private String currency;

        /**
         * 授权码，成功授权或支付时可能返回。
         */
        private String authorizationCode;

        /**
         * 渠道交易参考号。
         */
        private String reference;

        /**
         * 收单机构交易信息，部分 MPGS 响应中包含 batch、date、transactionId 等对账参考。
         */
        private Acquirer acquirer;

        /**
         * 渠道交易回单号。
         */
        private String receipt;

        /**
         * 交易来源。
         */
        private String source;

        /**
         * MPGS 认证状态。
         */
        private String authenticationStatus;

        /**
         * 系统跟踪审计号。
         */
        private String stan;

        /**
         * 终端号。
         */
        private String terminal;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Acquirer
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Acquirer 协作组件，位于 渠道适配库，封装 acquirer 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Acquirer {

        /**
         * 收单批次号。
         */
        private String batch;

        /**
         * 收单交易日期。
         */
        private String date;

        /**
         * 收单机构 ID。
         */
        private String id;

        /**
         * 渠道侧收单商户号。
         */
        private String merchantId;

        /**
         * 收单结算日期。
         */
        private String settlementDate;

        /**
         * 收单时区。
         */
        private String timeZone;

        /**
         * 收单机构交易参考号，渠道返回时可作为平台 ARN/收单参考号使用。
         */
        private String transactionId;
    }

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
         * MPGS orderId，当前使用平台原始授权/支付 transactionId。
         */
        private String id;

        /**
         * MPGS 订单金额，原始字符串。
         */
        private BigDecimal amount;

        /**
         * MPGS 订单币种。
         */
        private String currency;

        /**
         * MPGS 订单状态。
         */
        private String status;

        /**
         * MPGS 订单参考号。
         */
        private String reference;

        /**
         * MPGS 认证状态。
         */
        private String authenticationStatus;

        /**
         * 拒付摘要。
         */
        private Chargeback chargeback;

        /**
         * 订单创建时间，ISO-8601 字符串。
         */
        private String creationTime;

        /**
         * 订单最近更新时间，ISO-8601 字符串。
         */
        private String lastUpdatedTime;

        /**
         * 商户上送订单金额。
         */
        private BigDecimal merchantAmount;

        /**
         * 商户 MCC。
         */
        private String merchantCategoryCode;

        /**
         * 商户上送订单币种。
         */
        private String merchantCurrency;

        /**
         * 生命周期累计授权金额。
         */
        private BigDecimal totalAuthorizedAmount;

        /**
         * 生命周期累计请款金额。
         */
        private BigDecimal totalCapturedAmount;

        /**
         * 生命周期累计出款金额。
         */
        private BigDecimal totalDisbursedAmount;

        /**
         * 生命周期累计退款金额。
         */
        private BigDecimal totalRefundedAmount;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Chargeback
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Chargeback 协作组件，位于 渠道适配库，封装 chargeback 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Chargeback {

        /**
         * 拒付金额。
         */
        private BigDecimal amount;

        /**
         * 拒付币种。
         */
        private String currency;
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
         * 资金来源类型，例如 CARD。
         */
        private String type;

        /**
         * MPGS 返回的资金来源明细。
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
         * 卡信息摘要。
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
         * 卡品牌，例如 MASTERCARD。
         */
        private String brand;

        /**
         * 卡有效期。
         */
        private Expiry expiry;

        /**
         * 资金类型，例如 DEBIT。
         */
        private String fundingMethod;

        /**
         * 发卡国家或地区代码。
         */
        private String issuerCountryCode;

        /**
         * MPGS 返回的脱敏卡号。
         */
        private String number;

        /**
         * 卡组织。
         */
        private String scheme;

        /**
         * 是否已存储凭证。
         */
        private String storedOnFile;
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
         * 有效期月份。
         */
        private String month;

        /**
         * 有效期年份。
         */
        private String year;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Risk
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Risk 协作组件，位于 渠道适配库，封装 risk 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Risk {

        /**
         * MPGS 风险响应。
         */
        private RiskResponse response;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RiskResponse
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Risk Response 传输模型，位于 渠道适配库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RiskResponse {

        /**
         * 风险网关码。
         */
        private String gatewayCode;

        /**
         * 风险提供方。
         */
        private String provider;

        /**
         * 风险复核结果。
         */
        private Review review;

        /**
         * 命中或评估规则。
         */
        private List<RiskRule> rule;

        /**
         * 总风险分。
         */
        private Integer totalScore;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Review
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Review 协作组件，位于 渠道适配库，封装 review 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class Review {

        /**
         * 复核决策。
         */
        private String decision;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RiskRule
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Risk Rule 协作组件，位于 渠道适配库，封装 risk规则 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class RiskRule {

        /**
         * 规则数据。
         */
        private String data;

        /**
         * 规则名称。
         */
        private String name;

        /**
         * 规则建议。
         */
        private String recommendation;

        /**
         * 规则类型。
         */
        private String type;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ErrorPayload
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : Error Payload 协作组件，位于 渠道适配库，封装 errorpayload 相关的校验、转换、持久化访问或运行时协作入口。
     * @status : create
     */
    public static class ErrorPayload {

        /**
         * MPGS 错误原因编码。
         */
        private String cause;

        /**
         * MPGS 错误说明，日志可记录但不得直接作为商户可见原因。
         */
        private String explanation;

        /**
         * 发生校验错误的字段。
         */
        private String field;

        /**
         * MPGS 校验错误类型。
         */
        private String validationType;
    }
}

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
 * @description : MPGS 响应载荷模型，位于 payment-channel-mpgs 渠道实现层，仅承接渠道原始响应并映射为统一渠道响应。
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
     * MPGS 3DS 认证响应节点。
     */
    private Authentication authentication;

    /**
     * MPGS 记录时间，ISO-8601 字符串。
     */
    private String timeOfRecord;

    /**
     * MPGS 最近更新时间，ISO-8601 字符串。
     */
    private String timeOfLastUpdate;

    /**
     * MPGS 3DS browser return or callback order identifier.
     */
    private String orderId;

    /**
     * MPGS 3DS browser return or callback authentication transaction identifier.
     */
    private String transactionId;

    /**
     * EMV 3DS Server Transaction ID.
     */
    private String threeDSServerTransID;

    /**
     * MPGS encrypted 3DS session data. This field is sensitive and must only be stored masked.
     */
    private String threeDSSessionData;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Response
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的响应节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardSecurityCode
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的卡安全编码节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AuthorizationResponse
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的授权节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Transaction
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的交易节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Acquirer
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的收单机构节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Order
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的订单节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Chargeback
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的拒付节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SourceOfFunds
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的资金来源节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Provided
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的支付工具节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Provided {

        /**
         * 卡信息摘要。
         */
        private Card card;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Card
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的卡节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Expiry
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的有效期节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Risk
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的风控节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Risk {

        /**
         * MPGS 风险响应。
         */
        private RiskResponse response;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RiskResponse
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的风控节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Review
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的审核节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
    public static class Review {

        /**
         * 复核决策。
         */
        private String decision;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RiskRule
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的风控规则节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ErrorPayload
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : MPGS 响应报文的错误报文节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。
     * @status : create
     */
    @Data
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

    /**
     * MPGS 3DS 认证响应主体，封装认证状态、网关建议和不同协议版本的认证证据。
     */
    @Data
    public static class Authentication {

        /**
         * MPGS 3DS authentication transaction id。
         */
        private String transactionId;

        /**
         * 认证版本，例如 3DS2。
         */
        private String version;

        /**
         * 认证状态，例如 AUTHENTICATION_SUCCESSFUL。
         */
        private String status;

        /**
         * 网关建议，例如 PROCEED、DO_NOT_PROCEED。
         */
        private String gatewayRecommendation;

        /**
         * 持卡人交互状态，例如 REQUIRED、NOT_REQUIRED。
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

    /**
     * MPGS 返回的 3DS 浏览器重定向内容，只允许传递给受控收银台页面。
     */
    @Data
    public static class Redirect {

        /**
         * MPGS 返回给浏览器渲染的 HTML，日志和落库只能保存摘要。
         */
        private String html;

        /**
         * 部分响应可能返回重定向 URL。
         */
        private String url;
    }

    /**
     * MPGS 通用 3DS 认证证据。
     */
    @Data
    public static class ThreeDs {

        /**
         * ACS 返回的电子商务指示码。
         */
        private String acsEci;

        /**
         * 认证 token，属于敏感认证材料，禁止写入日志或外部响应。
         */
        private String authenticationToken;

        /**
         * 通用 3DS 认证交易标识。
         */
        private String transactionId;
    }

    /**
     * MPGS 3DS1 兼容认证证据。
     */
    @Data
    public static class ThreeDs1 {

        /**
         * Payer Authentication Response 状态。
         */
        private String paResStatus;

        /**
         * Visa Enrollment Response 注册状态。
         */
        private String veResEnrolled;
    }

    /**
     * MPGS 3DS2 认证链路标识与交易状态。
     */
    @Data
    public static class ThreeDs2 {

        /**
         * Access Control Server 交易标识。
         */
        private String acsTransactionId;

        /**
         * Directory Server 交易标识。
         */
        private String dsTransactionId;

        /**
         * 3DS Server 交易标识。
         */
        private String threeDSServerTransactionId;

        /**
         * EMV 3DS 交易状态码。
         */
        private String transactionStatus;
    }
}

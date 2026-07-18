package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClientProperties
 * @date : 2026-07-14 23:55
 * @email : scott_x@163.com
 * @description : service-payment 内部查询客户端配置，位于 service-admin 配置层，统一维护交易管理页面调用支付核心只读接口的地址和内部签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "admin.payment-client")
public class PaymentInternalClientProperties {

    /**
     * 交易主单分页查询接口地址。
     */
    private String orderSearchUrl = "http://service-payment/internal/payment/transactions/orders/search";

    /**
     * 交易动作单分页查询接口地址。
     */
    private String operationSearchUrl = "http://service-payment/internal/payment/transactions/operations/search";

    /**
     * 交易动作单分页及统计查询接口地址。
     */
    private String operationSearchWithSummaryUrl = "http://service-payment/internal/payment/transactions/operations/search-with-summary";

    /**
     * 退款动作接口地址。
     */
    private String refundUrl = "http://service-payment/internal/payment/refund";

    /**
     * 撤销动作接口地址。
     */
    private String voidUrl = "http://service-payment/internal/payment/void";

    /**
     * 交易聚合详情接口地址前缀，调用时追加 /{transactionId}。
     */
    private String detailBaseUrl = "http://service-payment/internal/payment/transactions";

    /**
     * 渠道交互日志分页查询接口地址。
     */
    private String channelLogSearchUrl = "http://service-payment/internal/payment/transactions/channel-logs/search";

    /**
     * 渠道回调业务记录分页查询接口地址。
     */
    private String channelCallbackSearchUrl = "http://service-payment/internal/payment/transactions/channel-callbacks/search";

    /**
     * 商户通知任务分页查询接口地址。
     */
    private String merchantNotificationSearchUrl = "http://service-payment/internal/payment/transactions/merchant-notifications/search";

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-admin";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}

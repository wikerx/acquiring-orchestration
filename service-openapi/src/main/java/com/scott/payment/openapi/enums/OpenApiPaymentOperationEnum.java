package com.scott.payment.openapi.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentOperationEnum
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单交易动作枚举，位于 service-openapi 枚举层，用于把独立 API 入口映射到 transaction_type 字典和内部支付服务路径。
 * @status : create
 */
@Getter
public enum OpenApiPaymentOperationEnum {

    /**
     * 一步支付交易，通常由渠道一次完成授权和请款。
     */
    PAYMENT("PAYMENT", "/payment"),

    /**
     * 授权交易，冻结或确认持卡人额度，后续可请款或撤销。
     */
    AUTHORIZATION("AUTHORIZATION", "/authorization"),

    /**
     * 预授权交易，先冻结额度，后续通过请款或预授权完成确认。
     */
    PRE_AUTHORIZATION("PRE_AUTHORIZATION", "/pre-authorization"),

    /**
     * 增量授权交易，用于同一生命周期内追加授权额度。
     */
    INCREMENTAL_AUTHORIZATION("INCREMENTAL_AUTHORIZATION", "/incremental-authorization"),

    /**
     * 请款交易，对授权或预授权成功的交易发起资金捕获。
     */
    CAPTURE("CAPTURE", "/capture"),

    /**
     * 退款交易，对成功支付或请款交易进行原路退回。
     */
    REFUND("REFUND", "/refund"),

    /**
     * 撤销交易，取消未完成清算的授权、预授权或支付动作。
     */
    VOID("VOID", "/void"),

    /**
     * 交易查询接口，用于查询交易当前状态；它是 API 操作，不直接生成新的资金交易动作。
     */
    QUERY("QUERY", "/query");

    /**
     * 对齐 transaction_type 字典的交易类型编码。
     */
    private final String transactionType;

    /**
     * service-payment 内部交易接口路径后缀。
     */
    private final String internalPath;

    OpenApiPaymentOperationEnum(String transactionType, String internalPath) {
        this.transactionType = transactionType;
        this.internalPath = internalPath;
    }
}

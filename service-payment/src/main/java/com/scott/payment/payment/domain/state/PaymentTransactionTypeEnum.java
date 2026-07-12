package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionTypeEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易类型枚举，位于 service-payment 领域状态层，对齐字典 transaction_type，用于标识同一原始交易生命周期中的不同交易动作。
 * @status : create
 */
@Getter
public enum PaymentTransactionTypeEnum {

    /**
     * 授权交易，冻结或确认持卡人额度。
     */
    AUTHORIZATION("AUTHORIZATION"),

    /**
     * 请款交易，对授权成功的交易发起资金捕获。
     */
    CAPTURE("CAPTURE"),

    /**
     * 支付交易，一步完成授权和请款。
     */
    PAYMENT("PAYMENT"),

    /**
     * 预授权交易，先冻结额度，后续通过预授权完成确认。
     */
    PRE_AUTHORIZATION("PRE_AUTHORIZATION"),

    /**
     * 预授权完成交易，对预授权发起完成确认。
     */
    PRE_AUTH_COMPLETION("PRE_AUTH_COMPLETION"),

    /**
     * 退款交易，对成功支付或请款交易进行原路退回。
     */
    REFUND("REFUND"),

    /**
     * 撤销交易，撤销未完成清算的授权或预授权。
     */
    VOID("VOID"),

    /**
     * 冲正交易，对异常或超时交易进行反向更正。
     */
    REVERSAL("REVERSAL"),

    /**
     * 拒付交易，由发卡行、卡组织或渠道侧发起争议。
     */
    CHARGEBACK("CHARGEBACK"),

    /**
     * 二次请款交易，对拒付争议发起资料抗辩和资金追回。
     */
    REPRESENTMENT("REPRESENTMENT"),

    /**
     * 调单交易，卡组织或渠道要求补充交易资料。
     */
    RETRIEVAL_REQUEST("RETRIEVAL_REQUEST"),

    /**
     * 增量授权交易，后续如启用需同步新增字典项并校验渠道能力。
     */
    INCREMENTAL_AUTHORIZATION("INCREMENTAL_AUTHORIZATION");

    private final String code;

    /**
     * 创建收单交易类型。
     *
     * @param code 字典 transaction_type 中的交易类型编码
     */
    PaymentTransactionTypeEnum(String code) {
        this.code = code;
    }
}

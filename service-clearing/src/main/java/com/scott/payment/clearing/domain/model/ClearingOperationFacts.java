package com.scott.payment.clearing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationFacts
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分应用层使用的动作事实快照；只承载数据库权威交易身份、终态、金额和分片时间，不携带持卡人数据。
 * @status : create
 * @param transactionId 动作级平台交易号
 * @param operationId 生命周期关联号
 * @param sourceTransactionId 退款、请款或冲正来源动作号
 * @param merchantId 平台商户号
 * @param merchantOrderNo 商户订单号
 * @param transactionType 动作类型
 * @param transactionStatus 数据库权威动作状态
 * @param labelCurrency 标签币种
 * @param labelAmount 标签金额
 * @param approvedCurrency 渠道最终批准币种
 * @param approvedAmount 渠道最终批准金额
 * @param transactionCurrency 系统交易币种
 * @param transactionAmount 系统交易金额
 * @param currencyExponent 标签币种 ISO exponent
 * @param transactionDateTime 当前动作季度分片时间
 * @param transactionUtcTime 当前动作 UTC 时间
 * @param transactionTimeZone 当前动作业务时区
 * @param operationVersion 动作投影更新使用的数据库 CAS 版本
 */
public record ClearingOperationFacts(String transactionId,
                                     String operationId,
                                     String sourceTransactionId,
                                     String merchantId,
                                     String merchantOrderNo,
                                     String transactionType,
                                     String transactionStatus,
                                     String labelCurrency,
                                     BigDecimal labelAmount,
                                     String approvedCurrency,
                                     BigDecimal approvedAmount,
                                     String transactionCurrency,
                                     BigDecimal transactionAmount,
                                     Integer currencyExponent,
                                     LocalDateTime transactionDateTime,
                                     LocalDateTime transactionUtcTime,
                                     String transactionTimeZone,
                                     Integer operationVersion) {
}

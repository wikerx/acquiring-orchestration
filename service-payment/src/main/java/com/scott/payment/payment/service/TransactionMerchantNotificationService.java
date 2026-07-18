package com.scott.payment.payment.service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationService
 * @date : 2026-07-14 21:35
 * @email : scott_x@163.com
 * @description : 商户交易结果通知服务，位于 service-payment 服务层，负责扫描到期通知任务、执行 HTTP 回调并记录每次重试日志。
 * @status : create
 */
public interface TransactionMerchantNotificationService {

    /**
     * 执行指定交易时间所在分表的到期商户通知任务。
     *
     * @param transactionDateTime 交易业务时间，用于定位 transaction_merchant_notification 物理分表
     * @param limit 最大处理数量
     * @return 本次成功通知数量
     */
    int notifyDue(LocalDateTime transactionDateTime, int limit);

    /**
     * 按平台交易 ID 触发同一交易时间分表中的通知任务。
     * <p>
     * MQ 事件消费优先使用该入口精准处理单笔交易；定时任务仍可调用 notifyDue 作为补偿扫描。
     *
     * @param transactionDateTime 交易业务时间，用于定位 transaction_merchant_notification 物理分表
     * @param transactionId 平台当前交易 ID
     * @return true 表示本次成功通知商户
     */
    boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId);
}

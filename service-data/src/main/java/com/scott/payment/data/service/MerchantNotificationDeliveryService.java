package com.scott.payment.data.service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationDeliveryService
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知投递契约，负责数据库任务抢占、HTTP 回调、重试状态和尝试日志，不修改支付订单事实
 * @status : create
 */
public interface MerchantNotificationDeliveryService {

    /**
     * 执行指定交易时间所在季度的到期通知任务。
     *
     * @param transactionDateTime 交易业务时间，用于定位通知逻辑表季度
     * @param limit 最大处理数量，必须大于零
     * @return 本次成功通知数量
     */
    int notifyDue(LocalDateTime transactionDateTime, int limit);

    /**
     * 按平台交易 ID 精确触发一条已经到期的通知任务。
     *
     * @param transactionDateTime 交易业务时间，用于精确定位通知逻辑表季度
     * @param transactionId 平台交易 ID
     * @return true 表示本次抢占并成功通知，false 表示任务不存在、未到期或抢占失败
     */
    boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId);

    /**
     * 按后台人工重发事件执行一次商户终态回调。
     *
     * @param transactionDateTime 交易业务时间，用于精确定位通知逻辑表季度
     * @param transactionId 平台交易 ID
     * @param callbackEventId MQ 消息唯一号，同时作为回调 JWT、Header 的稳定事件 ID
     * @return true 表示商户返回 HTTP 200 和 succeed，false 表示任务不存在、并发抢占失败或回调失败
     */
    boolean retryTransaction(LocalDateTime transactionDateTime,
                             String transactionId,
                             String callbackEventId);
}

package com.scott.payment.data.service;

import java.time.LocalDateTime;

/**
 * service-data 商户通知投递契约，负责数据库任务抢占、HTTP 回调、重试状态和尝试日志，不修改支付订单事实
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

    /**
     * 按通知任务号和商户号精确执行一次人工重发。
     *
     * <p>该入口供商户端重发命令使用，任务号和商户号必须同时匹配冻结的通知快照。独立人工事件号
     * 仍是商户可见的幂等键；执行结果只推进通知任务和尝试日志，不修改支付交易事实。</p>
     *
     * @param transactionDateTime 交易业务时间，用于精确定位季度分表
     * @param transactionId 平台交易号
     * @param notifyId 商户通知任务号
     * @param merchantId 通知任务所属商户号
     * @param callbackEventId MQ 消息唯一号，同时作为回调稳定事件 ID
     * @return true 表示商户返回 HTTP 200 和 succeed，false 表示回调失败
     */
    boolean retryNotification(LocalDateTime transactionDateTime,
                              String transactionId,
                              String notifyId,
                              String merchantId,
                              String callbackEventId);

    /**
     * 按自动重试事件携带的任务版本和 attempt 精确投递，重复或过期消息不执行 HTTP。
     *
     * @param transactionDateTime 交易业务时间
     * @param transactionId 平台交易号
     * @param notifyId 平台通知任务号
     * @param expectedVersion 失败状态提交后的任务版本
     * @param attemptNo 本消息计划触发的尝试序号
     * @return true 表示商户确认成功，false 表示消息已过期、抢占冲突或回调失败
     */
    boolean retryDue(LocalDateTime transactionDateTime,
                     String transactionId,
                     String notifyId,
                     int expectedVersion,
                     int attemptNo);
}

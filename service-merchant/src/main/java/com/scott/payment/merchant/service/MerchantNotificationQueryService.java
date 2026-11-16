package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRecord;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryTarget;

import java.time.LocalDateTime;

/**
 * 商户端交易通知只读查询服务。
 */
public interface MerchantNotificationQueryService {

    /**
     * 分页查询当前认证商户的交易通知任务。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 查询和分页条件
     * @return 当前商户的通知任务分页
     */
    PageResult<NotificationRecord> page(String merchantId, NotificationQuery query);

    /**
     * 查询当前认证商户的通知任务和投递尝试。
     *
     * @param merchantId 认证上下文中的商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 脱敏通知详情
     */
    NotificationDetailResponse detail(String merchantId,
                                      String notifyId,
                                      LocalDateTime transactionDateTime);

    /**
     * 分页查询当前认证商户的通知投递日志，最新尝试优先。
     *
     * @param merchantId 认证上下文中的商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页记录数，最大 100
     * @return 投递日志分页
     */
    PageResult<DeliveryLog> pageDeliveryLogs(String merchantId,
                                             String notifyId,
                                             LocalDateTime transactionDateTime,
                                             long pageNo,
                                             long pageSize);

    /**
     * 强一致读取当前认证商户可人工重发的通知任务。
     *
     * <p>查询必须同时校验认证商户、通知任务号、交易号终态和通知自动计划终态；跨商户或仍处于
     * 自动投递计划中的任务统一按不存在处理，避免泄露其他商户记录。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 可人工重发的精确任务定位
     */
    NotificationRetryTarget retryTarget(String merchantId,
                                        String notifyId,
                                        LocalDateTime transactionDateTime);
}

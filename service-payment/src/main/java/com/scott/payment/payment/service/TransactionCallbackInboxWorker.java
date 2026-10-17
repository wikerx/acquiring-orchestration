package com.scott.payment.payment.service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxWorker
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox Worker 契约，位于 service-payment 服务层，负责编排超时恢复、CAS 抢占和现有交易回调处理服务。
 * @status : create
 */
public interface TransactionCallbackInboxWorker {

    /**
     * 处理一批到期回调。
     *
     * @param now 本批次统一时间
     * @param limit 最大扫描条数
     * @return 已完成业务处理并成功标记 PROCESSED 的数量
     */
    int processDue(LocalDateTime now, int limit);
}

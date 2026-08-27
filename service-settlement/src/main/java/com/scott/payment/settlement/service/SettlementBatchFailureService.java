package com.scott.payment.settlement.service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchFailureService
 * @date : 2026-08-26 23:40
 * @email : scott_x@163.com
 * @description : 结算批次失败分类与数据库补偿边界，负责重试退避或不可逆转入人工复核。
 * @status : create
 */
public interface SettlementBatchFailureService {

    /**
     * 根据受控异常、数据库异常和重试次数记录失败状态。
     *
     * @param settlementBatchNo 批次号
     * @param owner 当前租约所有者
     * @param failure 处理异常
     * @param now 失败记录时间
     */
    void recordFailure(String settlementBatchNo,
                       String owner,
                       Throwable failure,
                       LocalDateTime now);
}

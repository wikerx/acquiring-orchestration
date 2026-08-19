package com.scott.payment.payment.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import com.scott.payment.payment.mapper.TransactionRefundApprovalMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalRecoveryService
 * @date : 2026-08-06 15:20
 * @email : scott_x@163.com
 * @description : 退款审批恢复编排服务，批量发现到期审批和已批准未执行任务，并逐笔委托事务工作流处理。
 * @status : create
 */
@Service
public class RefundApprovalRecoveryService {

    private final TransactionRefundApprovalMapper approvalMapper;
    private final RefundApprovalWorkflowService workflowService;

    /**
     * 创建退款审批恢复服务。
     *
     * @param approvalMapper 审批工作队列 Mapper
     * @param workflowService 单笔审批事务工作流
     */
    public RefundApprovalRecoveryService(TransactionRefundApprovalMapper approvalMapper,
                                         RefundApprovalWorkflowService workflowService) {
        this.approvalMapper = approvalMapper;
        this.workflowService = workflowService;
    }

    /**
     * 逐笔终结到期待审批任务，单笔事务失败不会提前标记其他任务。
     *
     * @param now 当前时间
     * @param limit 单轮最大任务数
     * @return 实际完成过期处理的任务数
     */
    @DS(DataSourceName.TRANSACTION)
    public int expireDue(LocalDateTime now, int limit) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        List<TransactionRefundApprovalDO> approvals = approvalMapper.selectExpired(actualNow, safeLimit(limit));
        int processed = 0;
        for (TransactionRefundApprovalDO approval : approvals) {
            if (workflowService.expire(approval.getApprovalId(), actualNow)) {
                processed++;
            }
        }
        return processed;
    }

    /**
     * 恢复静默超时且动作仍处于 WAITING_EXECUTION 的稳定执行事件。
     *
     * @param now 当前时间
     * @param staleSeconds 静默阈值秒数
     * @param limit 单轮最大任务数
     * @return 已确认或恢复执行事件的任务数
     */
    @DS(DataSourceName.TRANSACTION)
    public int recoverApproved(LocalDateTime now, long staleSeconds, int limit) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime staleBefore = actualNow.minusSeconds(Math.max(1L, staleSeconds));
        List<TransactionRefundApprovalDO> approvals = approvalMapper.selectApprovedForRecovery(
                staleBefore, safeLimit(limit));
        int recovered = 0;
        for (TransactionRefundApprovalDO approval : approvals) {
            if (workflowService.recoverApprovedExecution(approval.getApprovalId(), actualNow)) {
                recovered++;
            }
        }
        return recovered;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 1000));
    }
}

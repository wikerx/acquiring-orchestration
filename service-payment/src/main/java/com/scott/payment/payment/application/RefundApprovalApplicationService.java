package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.RefundApprovalDecisionRequestDTO;
import com.scott.payment.payment.api.internal.dto.RefundApprovalResultDTO;
import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import com.scott.payment.payment.service.RefundApprovalWorkflowService;
import com.scott.payment.payment.service.dto.RefundApprovalDecisionCommandDTO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalApplicationService
 * @date : 2026-08-06 15:30
 * @email : scott_x@163.com
 * @description : 退款审批应用服务，负责内部协议 DTO 转换并委托审批领域工作流完成事务决策。
 * @status : create
 */
@Service
public class RefundApprovalApplicationService {

    private final RefundApprovalWorkflowService workflowService;

    /** @param workflowService 退款审批事务工作流 */
    public RefundApprovalApplicationService(RefundApprovalWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 审批通过退款。
     *
     * @param approvalId 审批单号
     * @param request 内部审批请求
     * @return 审批结果
     */
    public RefundApprovalResultDTO approve(String approvalId, RefundApprovalDecisionRequestDTO request) {
        return toResult(workflowService.approve(toCommand(approvalId, request)));
    }

    /**
     * 拒绝退款审批。
     *
     * @param approvalId 审批单号
     * @param request 内部审批请求
     * @return 审批结果
     */
    public RefundApprovalResultDTO reject(String approvalId, RefundApprovalDecisionRequestDTO request) {
        return toResult(workflowService.reject(toCommand(approvalId, request)));
    }

    private RefundApprovalDecisionCommandDTO toCommand(String approvalId,
                                                       RefundApprovalDecisionRequestDTO request) {
        RefundApprovalDecisionCommandDTO command = new RefundApprovalDecisionCommandDTO();
        command.setApprovalId(approvalId);
        command.setDecisionRequestId(request.getDecisionRequestId());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setOperatorId(request.getOperatorId());
        command.setOperatorName(request.getOperatorName());
        command.setReason(request.getApprovalReason());
        return command;
    }

    private RefundApprovalResultDTO toResult(TransactionRefundApprovalDO approval) {
        RefundApprovalResultDTO result = new RefundApprovalResultDTO();
        result.setApprovalId(approval.getApprovalId());
        result.setRefundTransactionId(approval.getRefundTransactionId());
        result.setApprovalStatus(approval.getApprovalStatus());
        result.setApprovalOperatorId(approval.getApprovalOperatorId());
        result.setApprovalOperatorName(approval.getApprovalOperatorName());
        result.setApprovalTime(approval.getApprovalTime());
        result.setApprovalReason(approval.getApprovalReason());
        result.setExecutionEventId(approval.getExecutionEventId());
        result.setVersion(approval.getVersion());
        return result;
    }
}

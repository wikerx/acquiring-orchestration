package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import com.scott.payment.payment.mapper.TransactionRefundApprovalMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalRecoveryServiceTests
 * @date : 2026-08-06 15:25
 * @email : scott_x@163.com
 * @description : 退款审批恢复批处理测试，验证过期终结和已批准执行事件恢复均逐笔委托事务工作流。
 * @status : create
 */
class RefundApprovalRecoveryServiceTests {

    @Test
    void processesExpiredAndApprovedRecoveryQueuesWithBoundedBatches() {
        TransactionRefundApprovalMapper mapper = mock(TransactionRefundApprovalMapper.class);
        RefundApprovalWorkflowService workflow = mock(RefundApprovalWorkflowService.class);
        RefundApprovalRecoveryService service = new RefundApprovalRecoveryService(mapper, workflow);
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 0);
        TransactionRefundApprovalDO expired = approval("RA-EXPIRED");
        TransactionRefundApprovalDO approved = approval("RA-APPROVED");
        when(mapper.selectExpired(now, 100)).thenReturn(List.of(expired));
        when(mapper.selectApprovedForRecovery(now.minusMinutes(5), 50)).thenReturn(List.of(approved));
        when(workflow.expire("RA-EXPIRED", now)).thenReturn(true);
        when(workflow.recoverApprovedExecution("RA-APPROVED", now)).thenReturn(true);

        assertThat(service.expireDue(now, 100)).isEqualTo(1);
        assertThat(service.recoverApproved(now, 300, 50)).isEqualTo(1);

        verify(workflow).expire("RA-EXPIRED", now);
        verify(workflow).recoverApprovedExecution("RA-APPROVED", now);
    }

    private TransactionRefundApprovalDO approval(String approvalId) {
        TransactionRefundApprovalDO approval = new TransactionRefundApprovalDO();
        approval.setApprovalId(approvalId);
        return approval;
    }
}

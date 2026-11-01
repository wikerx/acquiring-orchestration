package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.api.internal.dto.RefundApprovalDecisionRequestDTO;
import com.scott.payment.payment.api.internal.dto.RefundApprovalResultDTO;
import com.scott.payment.payment.application.RefundApprovalApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * Payment 内部退款审批接口，只做参数校验和应用服务委托，继续受统一内部签名拦截器保护。
 */
@RestController
@RequestMapping("/internal/payment/refund-approvals")
public class RefundApprovalInternalController {

    private final RefundApprovalApplicationService applicationService;

    /**
     * 创建退款审批内部接口。
     *
     * @param applicationService 退款审批应用服务
     */
    public RefundApprovalInternalController(RefundApprovalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 审批通过退款并写入稳定执行 Outbox。
     */
    @PostMapping("/{approvalId}/approve")
    public CommonResult<RefundApprovalResultDTO> approve(
            @PathVariable("approvalId") String approvalId,
            @Valid @RequestBody RefundApprovalDecisionRequestDTO request) {
        return success(applicationService.approve(approvalId, request));
    }

    /**
     * 拒绝退款审批并终结待审批退款动作。
     */
    @PostMapping("/{approvalId}/reject")
    public CommonResult<RefundApprovalResultDTO> reject(
            @PathVariable("approvalId") String approvalId,
            @Valid @RequestBody RefundApprovalDecisionRequestDTO request) {
        return success(applicationService.reject(approvalId, request));
    }
}

package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminRefundApplicationService;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSearchResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRefundController
 * @date : 2026-08-06 16:00
 * @email : scott_x@163.com
 * @description : 管理端退款管理接口，提供退款查询、详情和独立审批操作，不包含人工修改交易终态能力。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions")
public class AdminRefundController {

    private final AdminRefundApplicationService applicationService;

    /** @param applicationService 管理端退款应用服务 */
    public AdminRefundController(AdminRefundApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询退款/撤销分页和统计。 */
    @PostMapping("/refunds/search")
    @RequiresPermission("transaction:refund:list")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.QUERY, operation = "查询退款列表")
    public CommonResult<RefundSearchResponse> search(@RequestBody(required = false) RefundQuery query) {
        return success(applicationService.search(query));
    }

    /** 查询退款详情。 */
    @GetMapping("/refunds/{transactionId}")
    @RequiresPermission("transaction:refund:detail")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.QUERY, operation = "查询退款详情")
    public CommonResult<RefundDetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(transactionId, transactionDateTime));
    }

    /** 导出退款和撤销记录。 */
    @PostMapping("/refunds/export")
    @RequiresPermission("transaction:refund:export")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.EXPORT, operation = "导出退款列表")
    public void export(@RequestBody(required = false) RefundQuery query, HttpServletResponse response) {
        applicationService.export(query, currentOperatorName(), response);
    }

    /** 审批通过退款。 */
    @PostMapping("/refund-approvals/{approvalId}/approve")
    @RequiresPermission("transaction:refund:approve")
    @OperationLog(moduleName = "退款审批", businessType = OperationTypeConstants.UPDATE, operation = "审批通过退款")
    public CommonResult<ApprovalResult> approve(
            @PathVariable("approvalId") String approvalId,
            @RequestBody ApprovalDecisionRequest request) {
        return success(applicationService.approve(approvalId, request));
    }

    /** 拒绝退款审批。 */
    @PostMapping("/refund-approvals/{approvalId}/reject")
    @RequiresPermission("transaction:refund:reject")
    @OperationLog(moduleName = "退款审批", businessType = OperationTypeConstants.UPDATE, operation = "拒绝退款审批")
    public CommonResult<ApprovalResult> reject(
            @PathVariable("approvalId") String approvalId,
            @RequestBody ApprovalDecisionRequest request) {
        return success(applicationService.reject(approvalId, request));
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "unknown";
        }
        return StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
    }
}

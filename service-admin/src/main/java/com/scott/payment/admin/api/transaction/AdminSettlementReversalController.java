package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementReversalApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReversalController
 * @date : 2026-09-01 22:50
 * @email : scott_x@163.com
 * @description : Admin 结算冲正查询、申请和 Maker-Checker 复核入口；可信操作人由应用层解析后通过内部鉴权调用 settlement 服务。
 * @status : update
 */
@RestController
@RequestMapping("/admin/settlement/reversal-orders")
public class AdminSettlementReversalController {

    private final AdminSettlementReversalApplicationService applicationService;

    public AdminSettlementReversalController(AdminSettlementReversalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询管理数据范围内的结算冲正单。
     *
     * @param request 批次、商户、状态、时间范围和分页条件
     * @return 冲正单分页
     */
    @PostMapping("/search")
    @RequiresPermission("settlement:reversal-order:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算冲正单")
    public CommonResult<PageResult<ReversalSummary>> search(@RequestBody ReversalSearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 查询管理数据范围内的结算冲正详情。
     *
     * @param reversalOrderNo 冲正单号
     * @return 冲正申请、复核和执行结果
     */
    @GetMapping("/{reversalOrderNo}")
    @RequiresPermission("settlement:reversal-order:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算冲正详情")
    public CommonResult<ReversalDetailResponse> detail(
            @PathVariable("reversalOrderNo") String reversalOrderNo) {
        return success(applicationService.detail(reversalOrderNo));
    }

    /**
     * 为已入账结算批次提交冲正申请，尚不直接冲减资金。
     *
     * @param request 批次号、原因和幂等请求键
     * @param servletRequest 可信登录上下文载体
     * @return 冲正申请结果
     */
    @PostMapping
    @RequiresPermission("settlement:reversal-order:create")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.CREATE,
            operation = "提交结算冲正申请")
    public CommonResult<ReversalCommandResponse> submit(
            @RequestBody ReversalSubmitRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.submit(request, servletRequest));
    }

    /**
     * 由不同操作人审批并执行结算冲正。
     *
     * @param reversalOrderNo 冲正单号
     * @param request 审批意见、期望版本和幂等请求键
     * @param servletRequest 可信登录上下文载体
     * @return 冲正执行结果
     */
    @PostMapping("/{reversalOrderNo}/approve")
    @RequiresPermission("settlement:reversal-order:approve")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "审批结算冲正")
    public CommonResult<ReversalCommandResponse> approve(
            @PathVariable("reversalOrderNo") String reversalOrderNo,
            @RequestBody ReversalDecisionRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.decide(reversalOrderNo, "APPROVE", request, servletRequest));
    }

    /**
     * 由不同操作人拒绝待复核冲正申请。
     *
     * @param reversalOrderNo 冲正单号
     * @param request 拒绝原因、期望版本和幂等请求键
     * @param servletRequest 可信登录上下文载体
     * @return 冲正拒绝结果
     */
    @PostMapping("/{reversalOrderNo}/reject")
    @RequiresPermission("settlement:reversal-order:reject")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "拒绝结算冲正")
    public CommonResult<ReversalCommandResponse> reject(
            @PathVariable("reversalOrderNo") String reversalOrderNo,
            @RequestBody ReversalDecisionRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.decide(reversalOrderNo, "REJECT", request, servletRequest));
    }
}

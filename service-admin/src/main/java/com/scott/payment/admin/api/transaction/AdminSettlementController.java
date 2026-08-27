package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
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
 * @classname : AdminSettlementController
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算批次查询、入账前取消和入账后冲正权限接口；Controller 不承载资金规则。
 * @status : create
 */
@RestController
@RequestMapping("/admin/settlement/batches")
public class AdminSettlementController {

    private final AdminSettlementApplicationService applicationService;

    public AdminSettlementController(AdminSettlementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("settlement:batch:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次")
    public CommonResult<PageResult<BatchSummary>> search(@RequestBody BatchSearchRequest request) {
        return success(applicationService.search(request));
    }

    @GetMapping("/{settlementBatchNo}")
    @RequiresPermission("settlement:batch:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次详情")
    public CommonResult<BatchDetailResponse> detail(
            @PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.detail(settlementBatchNo));
    }

    @PostMapping("/{settlementBatchNo}/cancel")
    @RequiresPermission("settlement:batch:cancel")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "取消未入账结算批次")
    public CommonResult<BatchCommandResponse> cancel(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request) {
        return success(applicationService.cancel(settlementBatchNo, request));
    }

    @PostMapping("/{settlementBatchNo}/reverse")
    @RequiresPermission("settlement:batch:reverse")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "冲正已入账结算批次")
    public CommonResult<BatchCommandResponse> reverse(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request) {
        return success(applicationService.reverse(settlementBatchNo, request));
    }
}

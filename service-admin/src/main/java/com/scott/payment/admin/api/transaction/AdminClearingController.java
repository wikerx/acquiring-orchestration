package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminClearingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/** Admin 清分记录查询和受控人工处置接口。 */
@RestController
@RequestMapping("/admin/clearing/records")
public class AdminClearingController {

    private final AdminClearingApplicationService applicationService;

    public AdminClearingController(AdminClearingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("clearing:record:list")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分记录")
    public CommonResult<PageResult<Summary>> search(@RequestBody SearchRequest request) {
        return success(applicationService.search(request));
    }

    @GetMapping("/{transactionId}")
    @RequiresPermission("clearing:record:detail")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分详情")
    public CommonResult<DetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(transactionId, transactionDateTime));
    }

    @GetMapping("/recalculation-options")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分重算费用版本")
    public CommonResult<RecalculationOptionsResponse> recalculationOptions(
            @RequestParam("merchantId") String merchantId,
            @RequestParam("feePlanId") Long feePlanId) {
        return success(applicationService.recalculationOptions(merchantId, feePlanId));
    }

    @PostMapping("/{transactionId}/retry")
    @RequiresPermission("clearing:record:retry")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "人工重试清分")
    public CommonResult<CommandResponse> retry(@PathVariable("transactionId") String transactionId,
                                               @RequestBody ActionRequest request) {
        return success(applicationService.retry(transactionId, request));
    }

    @PostMapping("/{transactionId}/review")
    @RequiresPermission("clearing:record:review")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "升级人工复核")
    public CommonResult<CommandResponse> review(@PathVariable("transactionId") String transactionId,
                                                @RequestBody ActionRequest request) {
        return success(applicationService.review(transactionId, request));
    }

    @PostMapping("/{transactionId}/recalculate")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "重算未结算清分")
    public CommonResult<CommandResponse> recalculate(@PathVariable("transactionId") String transactionId,
                                                     @RequestBody RecalculateRequest request) {
        return success(applicationService.recalculate(transactionId, request));
    }

    @PostMapping("/recalculate-batch")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "批量重算未结算清分")
    public CommonResult<RecalculateBatchResponse> recalculateBatch(
            @RequestBody RecalculateBatchRequest request) {
        return success(applicationService.batchRecalculate(request));
    }
}

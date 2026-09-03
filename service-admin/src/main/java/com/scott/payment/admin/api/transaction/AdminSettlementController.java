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
 * @classname : AdminSettlementController
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算批次查询和入账前取消权限接口；Controller 不承载资金规则。
 * @status : create
 */
@RestController
@RequestMapping("/admin/settlement/batches")
public class AdminSettlementController {

    private final AdminSettlementApplicationService applicationService;

    public AdminSettlementController(AdminSettlementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 在当前 Admin 商户数据范围内分页查询正式结算批次。
     *
     * @param request 业务日期窗口和批次过滤条件
     * @return 正式结算批次标准分页
     */
    @PostMapping("/search")
    @RequiresPermission("settlement:batch:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次")
    public CommonResult<PageResult<BatchSummary>> search(@RequestBody BatchSearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 在当前 Admin 商户数据范围内读取正式批次运营详情。
     *
     * @param settlementBatchNo 正式结算批次号
     * @return 批次、候选、汇率、结果、资金与投影详情
     */
    @GetMapping("/{settlementBatchNo}")
    @RequiresPermission("settlement:batch:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次详情")
    public CommonResult<BatchDetailResponse> detail(
            @PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.detail(settlementBatchNo));
    }

    /**
     * 注入可信操作人后取消尚未入账的正式批次。
     *
     * @param settlementBatchNo 待取消批次号
     * @param request 浏览器批次命令，不接受操作人字段
     * @param servletRequest 可信客户端环境来源
     * @return 取消状态和实际释放候选数
     */
    @PostMapping("/{settlementBatchNo}/cancel")
    @RequiresPermission("settlement:batch:cancel")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "取消未入账结算批次")
    public CommonResult<BatchCommandResponse> cancel(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request,
            HttpServletRequest servletRequest) {
        return success(applicationService.cancel(settlementBatchNo, request, servletRequest));
    }

}

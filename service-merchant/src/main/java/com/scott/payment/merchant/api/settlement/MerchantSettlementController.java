package com.scott.payment.merchant.api.settlement;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.settlement.MerchantSettlementApplicationService;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import jakarta.servlet.http.HttpServletResponse;
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
 * @classname : MerchantSettlementController
 * @date : 2026-09-01 22:35
 * @email : scott_x@163.com
 * @description : 商户结算账单、真实交易明细和保证金动作的查询及导出入口；只暴露当前商户数据，不提供任何结算命令。
 * @status : update
 */
@RestController
@RequestMapping("/merchant/settlements")
public class MerchantSettlementController {

    private final MerchantSettlementApplicationService applicationService;

    public MerchantSettlementController(MerchantSettlementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户已入账或已冲正的结算批次。
     *
     * @param query 可空批次过滤和分页条件
     * @return 强制绑定当前 merchantId 的结算批次分页
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:settlement:batch:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询结算账单")
    public CommonResult<PageResult<BatchSummary>> search(@RequestBody(required = false) BatchQuery query) {
        return success(applicationService.searchBatches(query));
    }

    /**
     * 查询当前认证商户指定批次的净额、汇率和聚合明细。
     *
     * @param settlementBatchNo 正式结算批次号
     * @return 仅当前 merchantId 可见的批次详情
     */
    @GetMapping("/{settlementBatchNo}")
    @RequiresPermission("merchant:settlement:batch:detail")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询结算账单详情")
    public CommonResult<BatchDetail> detail(@PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.getBatch(settlementBatchNo));
    }

    /**
     * 分页查询批次内可追溯到真实 transactionId 的结算财务行。
     *
     * @param query 可空交易明细过滤和分页条件
     * @return 强制绑定当前 merchantId 的交易结算明细分页
     */
    @PostMapping("/transaction-items/search")
    @RequiresPermission("merchant:settlement:transaction-item:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询交易结算明细")
    public CommonResult<PageResult<TransactionItem>> searchTransactionItems(
            @RequestBody(required = false) TransactionItemQuery query) {
        return success(applicationService.searchTransactionItems(query));
    }

    /**
     * 分页查询当前商户保证金结算动作及动作后的剩余责任。
     *
     * @param query 可空保证金明细过滤和分页条件
     * @return 强制绑定当前 merchantId 的保证金动作分页
     */
    @PostMapping("/reserve-items/search")
    @RequiresPermission("merchant:settlement:reserve-item:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询保证金结算明细")
    public CommonResult<PageResult<ReserveItem>> searchReserveItems(
            @RequestBody(required = false) ReserveItemQuery query) {
        return success(applicationService.searchReserveItems(query));
    }

    /**
     * 按当前查询条件导出商户可见的结算批次。
     *
     * @param query 可空批次过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/export")
    @RequiresPermission("merchant:settlement:batch:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出结算账单")
    public void exportBatches(@RequestBody(required = false) BatchQuery query, HttpServletResponse response) {
        applicationService.exportBatches(query, response);
    }

    /**
     * 按当前查询条件导出真实交易结算明细。
     *
     * @param query 可空交易明细过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/transaction-items/export")
    @RequiresPermission("merchant:settlement:transaction-item:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出交易结算明细")
    public void exportTransactionItems(@RequestBody(required = false) TransactionItemQuery query,
                                       HttpServletResponse response) {
        applicationService.exportTransactionItems(query, response);
    }

    /**
     * 按当前查询条件导出保证金动作明细。
     *
     * @param query 可空保证金明细过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/reserve-items/export")
    @RequiresPermission("merchant:settlement:reserve-item:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出保证金结算明细")
    public void exportReserveItems(@RequestBody(required = false) ReserveItemQuery query,
                                   HttpServletResponse response) {
        applicationService.exportReserveItems(query, response);
    }
}

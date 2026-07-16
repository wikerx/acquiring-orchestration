package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
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
 * @classname : AdminTransactionOperationController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 交易动作管理接口，位于 service-admin 接口层，以授权、请款、退款、撤销等每笔平台交易动作维度查询交易数据。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/operations")
public class AdminTransactionOperationController {

    private final AdminTransactionApplicationService transactionApplicationService;

    /**
     * 创建交易动作管理接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionOperationController(AdminTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("transaction:operation:list")
    @OperationLog(moduleName = "交易管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询交易动作")
    public CommonResult<PageResult<TransactionOperationResponse>> search(@RequestBody(required = false) TransactionPageQuery query) {
        return success(transactionApplicationService.pageOperations(query));
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    @PostMapping("/search-with-summary")
    @RequiresPermission("transaction:operation:list")
    @OperationLog(moduleName = "交易管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询交易动作及统计")
    public CommonResult<TransactionOperationSearchResponse> searchWithSummary(@RequestBody(required = false) TransactionPageQuery query) {
        return success(transactionApplicationService.searchOperations(query));
    }

    /**
     * 发起交易退款。
     *
     * @param transactionId 原平台交易 ID
     * @param request 退款动作请求
     * @return 退款动作结果
     */
    @PostMapping("/{transactionId}/refund")
    @RequiresPermission("transaction:operation:refund")
    @OperationLog(moduleName = "交易管理", businessType = OperationTypeConstants.UPDATE, operation = "发起交易退款")
    public CommonResult<TransactionActionResponse> refund(@PathVariable("transactionId") String transactionId,
                                                          @RequestBody TransactionActionRequest request) {
        return success(transactionApplicationService.refund(transactionId, request));
    }

    /**
     * 发起交易撤销。
     *
     * @param transactionId 原平台交易 ID
     * @param request 撤销动作请求
     * @return 撤销动作结果
     */
    @PostMapping("/{transactionId}/void")
    @RequiresPermission("transaction:operation:void")
    @OperationLog(moduleName = "交易管理", businessType = OperationTypeConstants.UPDATE, operation = "发起交易撤销")
    public CommonResult<TransactionActionResponse> voidPayment(@PathVariable("transactionId") String transactionId,
                                                               @RequestBody(required = false) TransactionActionRequest request) {
        return success(transactionApplicationService.voidPayment(transactionId, request));
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    @GetMapping("/{transactionId}")
    @RequiresPermission("transaction:operation:detail")
    @OperationLog(moduleName = "交易管理", businessType = OperationTypeConstants.QUERY, operation = "查询交易动作详情")
    public CommonResult<TransactionDetailResponse> detail(@PathVariable("transactionId") String transactionId) {
        return success(transactionApplicationService.detail(transactionId));
    }
}

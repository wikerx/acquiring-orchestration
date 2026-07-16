package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
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
 * @classname : AdminTransactionOrderController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 交易主单管理接口，位于 service-admin 接口层，以平台交易生命周期主单维度查询交易汇总和详情。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/orders")
public class AdminTransactionOrderController {

    private final AdminTransactionApplicationService transactionApplicationService;

    /**
     * 创建交易主单管理接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionOrderController(AdminTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /**
     * 分页查询交易生命周期主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("transaction:order:list")
    @OperationLog(moduleName = "交易主单管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询交易主单")
    public CommonResult<PageResult<TransactionOrderResponse>> search(@RequestBody(required = false) TransactionPageQuery query) {
        return success(transactionApplicationService.pageOrders(query));
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    @GetMapping("/{transactionId}")
    @RequiresPermission("transaction:order:detail")
    @OperationLog(moduleName = "交易主单管理", businessType = OperationTypeConstants.QUERY, operation = "查询交易主单详情")
    public CommonResult<TransactionDetailResponse> detail(@PathVariable("transactionId") String transactionId) {
        return success(transactionApplicationService.detail(transactionId));
    }
}

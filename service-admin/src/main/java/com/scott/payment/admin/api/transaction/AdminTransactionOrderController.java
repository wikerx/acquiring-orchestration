package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
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
 * @classname : AdminTransactionOrderController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 交易主单管理接口，位于 service-admin 接口层，以平台交易生命周期主单维度查询交易汇总和详情。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/orders")
public class AdminTransactionOrderController {

    /**
     * transaction Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * 按当前查询条件导出交易生命周期主单。
     *
     * @param query 查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("transaction:order:export")
    @OperationLog(moduleName = "交易主单管理", businessType = OperationTypeConstants.EXPORT, operation = "导出交易主单")
    public void export(@RequestBody(required = false) TransactionPageQuery query, HttpServletResponse response) {
        transactionApplicationService.exportOrders(query, currentOperatorName(), response);
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

    /**
     * 完成 current Operator Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}

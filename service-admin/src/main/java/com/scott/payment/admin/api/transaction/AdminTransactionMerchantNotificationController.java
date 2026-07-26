package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionMerchantNotificationController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 商户回调记录查询接口，位于 service-admin 接口层，查询交易结果通知商户的任务状态、重试计划和回调配置快照。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/merchant-notifications")
public class AdminTransactionMerchantNotificationController {

    /**
     * transaction Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminTransactionApplicationService transactionApplicationService;

    /**
     * 创建商户回调记录查询接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionMerchantNotificationController(AdminTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("transaction:merchant-notification:list")
    @OperationLog(moduleName = "商户回调记录", businessType = OperationTypeConstants.QUERY, operation = "分页查询商户回调记录")
    public CommonResult<PageResult<Map<String, Object>>> search(@RequestBody(required = false) MerchantNotificationQuery query) {
        return success(transactionApplicationService.pageMerchantNotifications(query));
    }

    /**
     * 按当前查询条件导出商户通知任务。
     *
     * @param query 查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("transaction:merchant-notification:export")
    @OperationLog(moduleName = "商户回调记录", businessType = OperationTypeConstants.EXPORT, operation = "导出商户回调记录")
    public void export(@RequestBody(required = false) MerchantNotificationQuery query, HttpServletResponse response) {
        transactionApplicationService.exportMerchantNotifications(query, currentOperatorName(), response);
    }

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

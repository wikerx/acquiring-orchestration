package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.application.transaction.AdminMerchantNotificationRetryApplicationService;
import com.scott.payment.admin.dto.transaction.MerchantNotificationRetryRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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

    private final AdminTransactionApplicationService transactionApplicationService;

    /** 商户终态回调人工重发应用服务，只负责可靠 MQ 入队。 */
    private final AdminMerchantNotificationRetryApplicationService retryApplicationService;

    /**
     * 创建商户回调记录查询接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionMerchantNotificationController(
            AdminTransactionApplicationService transactionApplicationService,
            AdminMerchantNotificationRetryApplicationService retryApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
        this.retryApplicationService = retryApplicationService;
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
     * 查询单个商户回调任务及每次投递尝试的脱敏日志。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 通知任务和投递日志详情
     */
    @GetMapping("/{notifyId}")
    @RequiresPermission("transaction:merchant-notification:detail")
    @OperationLog(moduleName = "商户回调记录", businessType = OperationTypeConstants.QUERY, operation = "查询商户回调详情")
    public CommonResult<Map<String, Object>> detail(
            @PathVariable("notifyId") String notifyId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS") LocalDateTime transactionDateTime) {
        return success(transactionApplicationService.merchantNotificationDetail(
                notifyId, transactionDateTime));
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

    /**
     * 将指定终态交易的商户回调人工重发请求写入可靠 MQ Outbox。
     *
     * @param request 平台交易号、页面查询得到的真实分片时间和请求号
     * @return 已受理的稳定 MQ 事件号
     */
    @PostMapping("/retry")
    @RequiresPermission("transaction:merchant-notification:retry")
    @OperationLog(moduleName = "商户回调记录", businessType = OperationTypeConstants.UPDATE, operation = "人工重发商户终态回调")
    public CommonResult<String> retry(@Valid @RequestBody MerchantNotificationRetryRequest request) {
        return success(retryApplicationService.retry(request, currentOperatorName()));
    }

    /**
     * 解析写入导出审计元信息的当前操作人名称。
     *
     * @return 优先返回真实姓名，其次登录账号；无认证上下文时返回 {@code admin}
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

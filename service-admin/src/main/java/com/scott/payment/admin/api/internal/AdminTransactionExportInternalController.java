package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminTransactionExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundPageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的交易域导出源内部入口，受现有内部 HMAC 与防重放链路保护。
 */
@RestController
@RequestMapping("/internal/admin/transactions")
public class AdminTransactionExportInternalController {
    private final AdminTransactionExportSourceApplicationService applicationService;

    /**
     * 创建仅供异步导出执行器使用的内部入口。
     */
    public AdminTransactionExportInternalController(
            AdminTransactionExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按冻结范围分页读取交易主单安全摘要。
     */
    @PostMapping("/orders/export-page")
    public CommonResult<OrderPageResponse> orderPage(@Valid @RequestBody OrderPageRequest request) {
        return success(applicationService.pageOrders(request));
    }

    /**
     * 按冻结范围分页读取交易操作流水安全摘要。
     */
    @PostMapping("/operations/export-page")
    public CommonResult<OperationPageResponse> operationPage(
            @Valid @RequestBody OperationPageRequest request) {
        return success(applicationService.pageOperations(request));
    }

    /**
     * 按冻结范围分页读取商户通知记录安全摘要。
     */
    @PostMapping("/merchant-notifications/export-page")
    public CommonResult<MerchantNotificationPageResponse> merchantNotificationPage(
            @Valid @RequestBody MerchantNotificationPageRequest request) {
        return success(applicationService.pageMerchantNotifications(request));
    }

    /**
     * 按冻结范围分页读取退款安全摘要。
     */
    @PostMapping("/refunds/export-page")
    public CommonResult<RefundPageResponse> refundPage(@Valid @RequestBody RefundPageRequest request) {
        return success(applicationService.pageRefunds(request));
    }
}

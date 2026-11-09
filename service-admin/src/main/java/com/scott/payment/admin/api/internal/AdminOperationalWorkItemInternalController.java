package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.operation.AdminOperationalWorkItemApplicationService;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncResponse;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.PageRequest;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.PageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-job 调用的统一待办同步内部入口，受现有内部 HMAC 与防重放链路保护。
 */
@RestController
@RequestMapping("/internal/admin/operations/work-items")
public class AdminOperationalWorkItemInternalController {

    private final AdminOperationalWorkItemApplicationService applicationService;

    /**
     * 创建仅供内部任务调用的同步入口。
     *
     * @param applicationService 统一待办应用服务
     */
    public AdminOperationalWorkItemInternalController(
            AdminOperationalWorkItemApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按任务 runId 派生的请求键幂等同步六类来源。
     *
     * @param request 带请求键和任务执行时间的同步命令
     * @return 本轮来源扫描和状态变更统计
     */
    @PostMapping("/sync")
    public CommonResult<SyncResponse> synchronize(@Valid @RequestBody SyncRequest request) {
        return success(applicationService.synchronize(request));
    }

    /**
     * 使用任务创建时冻结的数据范围分页读取待办摘要，供 Data 流式生成文件。
     *
     * @param request 冻结查询、冻结范围和页码
     * @return 权威待办摘要分页
     */
    @PostMapping("/export-page")
    public CommonResult<PageResponse> exportPage(@Valid @RequestBody PageRequest request) {
        return success(applicationService.pageForExport(request));
    }
}

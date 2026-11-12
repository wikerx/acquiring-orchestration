package com.scott.payment.data.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SearchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskSummary;
import com.scott.payment.data.application.export.DataExportTaskApplicationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.scott.payment.component.core.model.CommonResult.success;
import static com.scott.payment.data.service.export.DataExportTaskService.APP_ADMIN;

/**
 * service-data 异步导出内部接口，仅允许通过 HMAC 白名单的 Admin 和 Job 调用。
 */
@RestController
@RequestMapping("/internal/data/export-tasks")
public class DataExportTaskInternalController {
    private final DataExportTaskApplicationService applicationService;

    /**
     * 创建异步导出内部接口。
     */
    public DataExportTaskInternalController(DataExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建任务并返回同一账号、同一请求键对应的幂等结果。
     */
    @PostMapping("/submit")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody CreateRequest request) {
        applyAdminOwnership(request);
        return success(applicationService.submit(request));
    }

    /**
     * 查询当前创建账号可见的任务分页，不接受跨账号查询。
     */
    @PostMapping("/search")
    public CommonResult<PageResult<TaskSummary>> search(@Valid @RequestBody SearchRequest request) {
        applyAdminOwnership(request);
        return success(applicationService.search(request));
    }

    /**
     * 查询归属于创建账号的单任务安全摘要。
     */
    @PostMapping("/detail")
    public CommonResult<TaskSummary> detail(@Valid @RequestBody TaskRequest request) {
        applyAdminOwnership(request);
        return success(applicationService.detail(request));
    }

    /**
     * 按预期版本人工重试失败任务，重复或并发命令由 CAS 拒绝。
     */
    @PostMapping("/retry")
    public CommonResult<TaskSummary> retry(@Valid @RequestBody TaskRequest request) {
        applyAdminOwnership(request);
        return success(applicationService.retry(request));
    }

    /**
     * 由 Job 执行一轮有界任务领取和过期对象清理。
     */
    @PostMapping("/dispatch")
    public CommonResult<DispatchResult> dispatch(@Valid @RequestBody DispatchRequest request) {
        return success(applicationService.dispatch(request));
    }

    /**
     * 代理私有对象下载；正文只写入内部调用方响应，不返回对象键或预签名永久链接。
     */
    @PostMapping("/download")
    public void download(@Valid @RequestBody DownloadRequest request,
                         HttpServletResponse response) throws IOException {
        applyAdminOwnership(request);
        TaskSummary summary = applicationService.detail(request);
        String fileName = summary.getFileName() == null ? "export.xlsx" : summary.getFileName();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment;filename*=utf-8''"
                        + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        if (summary.getFileSizeBytes() != null) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(summary.getFileSizeBytes()));
        }
        if (summary.getFileSha256() != null) {
            response.setHeader("X-File-SHA256", summary.getFileSha256());
        }
        applicationService.download(request, response.getOutputStream());
    }

    /**
     * 将旧版 Admin 内部路径固定到管理应用命名空间，忽略调用正文中的归属声明。
     */
    private void applyAdminOwnership(CreateRequest request) {
        request.setCreatorAppCode(APP_ADMIN);
        request.setMerchantId(null);
    }

    /**
     * 将 Admin 任务列表请求固定到管理应用命名空间。
     */
    private void applyAdminOwnership(SearchRequest request) {
        request.setCreatorAppCode(APP_ADMIN);
        request.setMerchantId(null);
    }

    /**
     * 将 Admin 单任务请求固定到管理应用命名空间。
     */
    private void applyAdminOwnership(TaskRequest request) {
        request.setCreatorAppCode(APP_ADMIN);
        request.setMerchantId(null);
    }
}

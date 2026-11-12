package com.scott.payment.data.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
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
import static com.scott.payment.data.service.export.DataExportTaskService.APP_MERCHANT;

/**
 * service-merchant 专用异步导出内部入口，将所有任务固定到商户应用和认证商户命名空间。
 *
 * <p>该路径必须只允许 service-merchant 的独立 HMAC 身份访问，浏览器不能直连。</p>
 */
@RestController
@RequestMapping("/internal/data/merchant-export-tasks")
public class MerchantDataExportTaskInternalController {
    private final DataExportTaskApplicationService applicationService;

    /**
     * 创建商户异步导出内部接口。
     *
     * @param applicationService 通用异步导出应用服务
     */
    public MerchantDataExportTaskInternalController(DataExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建当前商户账号范围内的幂等导出任务。
     */
    @PostMapping("/submit")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody CreateRequest request) {
        applyMerchantOwnership(request);
        return success(applicationService.submit(request));
    }

    /**
     * 分页查询当前商户账号创建的任务。
     */
    @PostMapping("/search")
    public CommonResult<PageResult<TaskSummary>> search(@Valid @RequestBody SearchRequest request) {
        applyMerchantOwnership(request);
        return success(applicationService.search(request));
    }

    /**
     * 查询当前商户账号拥有的单任务摘要。
     */
    @PostMapping("/detail")
    public CommonResult<TaskSummary> detail(@Valid @RequestBody TaskRequest request) {
        applyMerchantOwnership(request);
        return success(applicationService.detail(request));
    }

    /**
     * 使用预期版本重新排队当前商户账号的失败任务。
     */
    @PostMapping("/retry")
    public CommonResult<TaskSummary> retry(@Valid @RequestBody TaskRequest request) {
        applyMerchantOwnership(request);
        return success(applicationService.retry(request));
    }

    /**
     * 代理当前商户账号拥有的私有导出文件并写入下载审计。
     */
    @PostMapping("/download")
    public void download(@Valid @RequestBody DownloadRequest request,
                         HttpServletResponse response) throws IOException {
        applyMerchantOwnership(request);
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
     * 覆盖正文应用归属，商户号仍由 service-merchant 的认证上下文提供。
     */
    private void applyMerchantOwnership(CreateRequest request) {
        request.setCreatorAppCode(APP_MERCHANT);
    }

    /**
     * 覆盖列表请求应用归属，防止通过正文切换到 Admin 命名空间。
     */
    private void applyMerchantOwnership(SearchRequest request) {
        request.setCreatorAppCode(APP_MERCHANT);
    }

    /**
     * 覆盖单任务请求应用归属，防止跨应用枚举和下载。
     */
    private void applyMerchantOwnership(TaskRequest request) {
        request.setCreatorAppCode(APP_MERCHANT);
    }
}

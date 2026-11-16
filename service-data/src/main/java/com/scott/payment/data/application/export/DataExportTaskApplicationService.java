package com.scott.payment.data.application.export;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SearchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskSummary;
import com.scott.payment.data.service.export.DataExportTaskService;
import org.springframework.stereotype.Service;

import java.io.OutputStream;

/**
 * 异步导出内部 API 应用服务，保持 Controller 仅负责协议适配。
 */
@Service
public class DataExportTaskApplicationService {
    private final DataExportTaskService exportTaskService;

    /**
     * 创建异步导出应用服务。
     */
    public DataExportTaskApplicationService(DataExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    /**
     * 将来源服务已冻结的查询条件和数据范围提交为幂等导出任务。
     */
    public TaskSummary submit(CreateRequest request) {
        return exportTaskService.submit(request);
    }

    /**
     * 按创建账号隔离并分页查询导出任务摘要。
     */
    public PageResult<TaskSummary> search(SearchRequest request) {
        return exportTaskService.search(request);
    }

    /**
     * 查询归属于指定创建账号的单个任务安全摘要。
     */
    public TaskSummary detail(TaskRequest request) {
        return exportTaskService.detail(request);
    }

    /**
     * 使用预期版本将当前账号的失败任务重新放入处理队列。
     */
    public TaskSummary retry(TaskRequest request) {
        return exportTaskService.retry(request);
    }

    /**
     * 执行一轮有界任务领取、文件生成和到期文件清理。
     */
    public DispatchResult dispatch(DispatchRequest request) {
        return exportTaskService.dispatch(request);
    }

    /**
     * 将已完成且未过期的私有文件写入经过内部鉴权的响应流。
     */
    public TaskSummary download(DownloadRequest request, OutputStream outputStream) {
        return exportTaskService.download(request, outputStream);
    }
}

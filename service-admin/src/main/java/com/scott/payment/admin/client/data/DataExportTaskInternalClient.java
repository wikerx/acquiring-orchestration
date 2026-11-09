package com.scott.payment.admin.client.data;

import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin 调用 Data 异步导出内部能力的边界，隐藏签名、服务发现和文件流协议。
 */
public interface DataExportTaskInternalClient {

    /**
     * 幂等创建一条导出任务。
     *
     * @param request 冻结查询和数据范围的创建命令
     * @return 已创建或既有幂等任务
     */
    TaskSummary submit(CreateRequest request);

    /**
     * 查询当前创建人的任务分页。
     *
     * @param request 当前账号和筛选条件
     * @return 任务分页
     */
    PageResult<TaskSummary> search(SearchRequestDTO request);

    /**
     * 查询归属于当前创建人的任务详情。
     *
     * @param request 任务号和当前账号
     * @return 任务详情
     */
    TaskSummary detail(TaskRequest request);

    /**
     * 使用版本条件人工重试失败任务。
     *
     * @param request 任务归属和预期版本
     * @return 重试后的任务
     */
    TaskSummary retry(TaskRequest request);

    /**
     * 将 Data 私有对象流代理到浏览器响应，不在 Admin 堆内存中聚合文件。
     *
     * @param request 下载归属与审计信息
     * @param response 浏览器响应
     */
    void download(DownloadRequest request, HttpServletResponse response);
}

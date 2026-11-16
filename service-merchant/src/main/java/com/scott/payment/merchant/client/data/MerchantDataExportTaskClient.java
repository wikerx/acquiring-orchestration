package com.scott.payment.merchant.client.data;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.SearchRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Merchant 调用 Data 异步导出能力的客户端边界，隐藏服务签名和文件流协议。
 */
public interface MerchantDataExportTaskClient {

    /**
     * 幂等提交一个商户导出任务。
     */
    TaskSummary submit(CreateRequest request);

    /**
     * 分页查询当前商户账号创建的任务。
     */
    PageResult<TaskSummary> search(SearchRequest request);

    /**
     * 查询当前商户账号拥有的任务详情。
     */
    TaskSummary detail(TaskRequest request);

    /**
     * 使用版本条件重试失败任务。
     */
    TaskSummary retry(TaskRequest request);

    /**
     * 将 Data 私有对象流代理到已认证的商户浏览器响应。
     */
    void download(DownloadRequest request, HttpServletResponse response);
}

package com.scott.payment.component.excel.service;

import jakarta.servlet.http.HttpServletResponse;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;

import java.io.OutputStream;

/**
 * Excel 导出服务接口
 */
public interface ExcelExportService {

    /**
     * 按统一样式导出 Excel 文件。
     *
     * @param request 导出请求
     * @param response HTTP 响应
     * @param <T> 行类型
     */
    <T> void export(ExcelExportRequest<T> request, HttpServletResponse response);

    /**
     * 分页加载并流式写出 Excel，导出总行数不受同步查询结果预算限制。
     *
     * @param request 分页导出请求
     * @param response HTTP 响应
     * @param <T> 行类型
     */
    <T> void exportPaged(ExcelPagedExportRequest<T> request, HttpServletResponse response);

    /**
     * 分页加载并流式写入调用方提供的输出流，供异步导出任务直接写入临时文件或对象存储上传流。
     *
     * <p>该方法不关闭输出流，流的生命周期由调用方负责；分页读取和工作簿内存边界与 HTTP 导出一致。</p>
     *
     * @param request 分页导出请求
     * @param outputStream 调用方管理的输出流
     * @param <T> 行类型
     */
    <T> void exportPaged(ExcelPagedExportRequest<T> request, OutputStream outputStream);

    /**
     * 按统一样式导出动态列 Excel 文件。
     *
     * @param request 动态列导出请求
     * @param response HTTP 响应
     */
    void exportDynamic(ExcelDynamicExportRequest request, HttpServletResponse response);
}

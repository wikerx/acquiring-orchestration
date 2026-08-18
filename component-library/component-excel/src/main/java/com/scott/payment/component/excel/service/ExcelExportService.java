package com.scott.payment.component.excel.service;

import jakarta.servlet.http.HttpServletResponse;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportService
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出服务接口
 * @status : create
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
     * 按统一样式导出动态列 Excel 文件。
     *
     * @param request 动态列导出请求
     * @param response HTTP 响应
     */
    void exportDynamic(ExcelDynamicExportRequest request, HttpServletResponse response);
}

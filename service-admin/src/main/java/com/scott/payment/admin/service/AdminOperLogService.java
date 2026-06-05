package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogService
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 管理后台操作日志服务
 * @status : create
 */
public interface AdminOperLogService {

    /**
     * 写入后台操作日志。
     *
     * @param request 操作日志写入请求
     */
    void recordOperLog(SysOperLogRecordRequest request);

    /**
     * 按条件查询后台操作日志列表。
     *
     * @param request 查询条件
     * @return 操作日志列表
     */
    PageResult<SysOperLogDTO> pageOperLogs(SysOperLogQueryRequest request);
}

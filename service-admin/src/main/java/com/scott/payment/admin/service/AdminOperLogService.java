package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogService
 * @date : 2026-06-19 21:53
 * @email : scott_x@163.com
 * @description : 管理后台操作日志领域服务
 * @status : create
 *
 * <p>负责后台操作日志写入与审计查询等领域能力，统一收敛日志落库规则与查询过滤规则。</p>
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

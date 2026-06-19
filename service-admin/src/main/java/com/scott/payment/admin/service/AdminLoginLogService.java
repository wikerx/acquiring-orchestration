package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * 后台登录日志领域服务。
 */
public interface AdminLoginLogService {

    /**
     * 按条件查询登录日志。
     *
     * @param request 查询条件
     * @return 登录日志分页结果
     */
    PageResult<SysLoginLogDTO> pageLoginLogs(SysLoginLogQueryRequest request);
}

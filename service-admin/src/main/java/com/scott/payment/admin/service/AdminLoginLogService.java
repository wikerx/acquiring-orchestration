package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogService
 * @date : 2026-06-19 21:53
 * @email : scott_x@163.com
 * @description : 管理后台登录日志领域服务
 * @status : create
 *
 * <p>负责后台登录日志查询相关领域能力，聚焦审计数据读取，不处理页面与接口协议细节。</p>
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

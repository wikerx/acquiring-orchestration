package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.admin.service.AdminLoginLogService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台登录日志应用服务
 * @status : create
 */
@Service
public class AdminLoginLogApplicationService {

    private final AdminLoginLogService adminLoginLogService;

    /**
     * 创建后台登录日志应用服务。
     *
     * @param adminLoginLogService 登录日志领域服务
     */
    public AdminLoginLogApplicationService(AdminLoginLogService adminLoginLogService) {
        this.adminLoginLogService = adminLoginLogService;
    }

    /**
     * 分页查询后台登录日志。
     *
     * @param request 查询条件
     * @return 登录日志分页结果
     */
    public PageResult<SysLoginLogDTO> pageLoginLogs(SysLoginLogQueryRequest request) {
        return adminLoginLogService.pageLoginLogs(request);
    }
}

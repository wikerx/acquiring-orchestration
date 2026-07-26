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

    /**
     * admin Login Log Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

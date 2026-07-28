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
     * admin Login Log Service 依赖，用于 Admin Login Log Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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

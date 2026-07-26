package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台操作日志应用服务
 * @status : create
 */
@Service
public class AdminOperLogApplicationService {

    /**
     * admin Oper Log Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminOperLogService adminOperLogService;

    /**
     * 创建后台操作日志应用服务。
     *
     * @param adminOperLogService 操作日志领域服务
     */
    public AdminOperLogApplicationService(AdminOperLogService adminOperLogService) {
        this.adminOperLogService = adminOperLogService;
    }

    /**
     * 记录一条操作日志。
     *
     * @param request 日志记录请求
     */
    public void recordOperLog(SysOperLogRecordRequest request) {
        adminOperLogService.recordOperLog(request);
    }

    /**
     * 分页查询操作日志。
     *
     * @param request 查询条件
     * @return 操作日志分页结果
     */
    public PageResult<SysOperLogDTO> pageOperLogs(SysOperLogQueryRequest request) {
        return adminOperLogService.pageOperLogs(request);
    }
}

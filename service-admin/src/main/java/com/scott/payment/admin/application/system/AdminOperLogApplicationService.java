package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 后台操作日志应用服务。
 */
@Service
public class AdminOperLogApplicationService {

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

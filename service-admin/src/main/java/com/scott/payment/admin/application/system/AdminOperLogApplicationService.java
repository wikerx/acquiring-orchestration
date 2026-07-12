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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Oper Log Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminOperLogApplicationService {

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
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
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 查询系统管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<SysOperLogDTO> pageOperLogs(SysOperLogQueryRequest request) {
        return adminOperLogService.pageOperLogs(request);
    }
}

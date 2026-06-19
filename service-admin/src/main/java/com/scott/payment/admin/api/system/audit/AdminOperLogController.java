package com.scott.payment.admin.api.system.audit;

import com.scott.payment.admin.application.system.AdminOperLogApplicationService;
import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogController
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台操作日志控制器
 * @status : create
 *
 * <p>提供后台操作日志落库和日志查询能力，Controller 本身不承载日志脱敏或审计规则。</p>
 */
@RestController
@RequestMapping("/admin/system/oper-logs")
public class AdminOperLogController {

    /**
     * 操作日志应用服务。
     */
    private final AdminOperLogApplicationService adminOperLogApplicationService;

    /**
     * 创建操作日志控制器。
     *
     * @param adminOperLogApplicationService 操作日志应用服务
     */
    public AdminOperLogController(AdminOperLogApplicationService adminOperLogApplicationService) {
        this.adminOperLogApplicationService = adminOperLogApplicationService;
    }

    /**
     * 写入后台操作日志。
     * <p>
     * 该接口只面向内部管理系统或后续 AOP 调用，入参必须已经完成脱敏，禁止记录密钥、JWT、卡号和 CVV 明文。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    @PostMapping
    @RequiresPermission("system:oper-log:list")
    public CommonResult<Void> recordOperLog(@RequestBody SysOperLogRecordRequest request) {
        adminOperLogApplicationService.recordOperLog(request);
        return success();
    }

    /**
     * 按条件查询后台操作日志列表。
     *
     * @param request 查询条件
     * @return 操作日志列表
     */
    @PostMapping("/search")
    @RequiresPermission("system:oper-log:list")
    @OperationLog(moduleName = "操作日志", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台操作日志列表")
    public CommonResult<PageResult<SysOperLogDTO>> listOperLogs(@RequestBody(required = false) SysOperLogQueryRequest request) {
        return success(adminOperLogApplicationService.pageOperLogs(request));
    }
}

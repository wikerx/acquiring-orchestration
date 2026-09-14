package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorOnlineApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorOnlineController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理端在线会话监控 HTTP 入口，负责权限、分页参数和统一响应，不直接查询或修改登录会话数据。
 * @status : create
 *
     * <p>在线会话查询与强制下线状态变更统一由应用服务层处理。</p>
 */
@RestController
@RequestMapping("/admin/monitor")
public class MonitorOnlineController {

    /**
     * 在线用户监控应用服务。
     */
    private final AdminMonitorOnlineApplicationService adminMonitorOnlineApplicationService;

    /**
     * 创建在线用户监控控制器。
     *
     * @param adminMonitorOnlineApplicationService 在线用户监控应用服务
     */
    public MonitorOnlineController(AdminMonitorOnlineApplicationService adminMonitorOnlineApplicationService) {
        this.adminMonitorOnlineApplicationService = adminMonitorOnlineApplicationService;
    }

    /**
     * 查询在线用户列表。
     *
     * <p>返回当前未退出的登录会话，包含用户名称、登录 IP、登录时间等信息。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param loginIp  登录 IP 过滤条件（可选）
     * @param userName 用户名称过滤条件（可选）
     * @return 分页的在线用户列表
     */
    @GetMapping("/online/list")
    @RequiresPermission("system:online:list")
    public CommonResult<Map<String, Object>> onlineList(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "loginIp", required = false) String loginIp,
            @RequestParam(value = "userName", required = false) String userName) {
        return success(adminMonitorOnlineApplicationService.pageOnlineUsers(pageNo, pageSize, loginIp, userName));
    }

    /**
     * 强制下线指定会话。
     *
     * @param sessionId 会话主键 ID
     * @return 空响应
     */
    @DeleteMapping("/online/{sessionId}")
    @RequiresPermission("system:online:forceLogout")
    @OperationLog(moduleName = "在线用户监控", businessType = OperationTypeConstants.DELETE, operation = "强制下线用户会话")
    public CommonResult<Void> forceLogout(@PathVariable("sessionId") Long sessionId) {
        adminMonitorOnlineApplicationService.forceLogout(sessionId);
        return success(null);
    }
}

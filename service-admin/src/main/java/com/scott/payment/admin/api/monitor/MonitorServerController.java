package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorServerApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorServerController
 * @date : 2026-06-12 17:30
 * @email : scott_x@163.com
 * @description : 服务监控控制器，提供 CPU、内存、JVM、磁盘等服务器运行信息
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor")
public class MonitorServerController {

    private final AdminMonitorServerApplicationService adminMonitorServerApplicationService;

    /**
     * 创建服务监控控制器。
     *
     * @param adminMonitorServerApplicationService 服务监控应用服务
     */
    public MonitorServerController(AdminMonitorServerApplicationService adminMonitorServerApplicationService) {
        this.adminMonitorServerApplicationService = adminMonitorServerApplicationService;
    }

    /**
     * 获取服务器运行信息。
     *
     * <p>返回 CPU 核心数与系统负载、JVM 内存使用、操作系统信息、运行时长和磁盘使用情况。
     *
     * @return 服务器运行信息 Map
     */
    @GetMapping("/server")
    @RequiresPermission("system:server:list")
    public CommonResult<Map<String, Object>> serverInfo() {
        return success(adminMonitorServerApplicationService.serverInfo());
    }
}

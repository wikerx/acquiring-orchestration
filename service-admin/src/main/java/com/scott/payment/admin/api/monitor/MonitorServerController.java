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
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台服务器监控控制器
 * @status : create
 *
 * <p>系统监控菜单下的服务器信息接口入口，负责服务监控相关 HTTP 映射与权限校验，
 * 具体节点运行指标采集由应用服务层完成。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorServerController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Monitor Server 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor")
public class MonitorServerController {

    /**
     * 服务器监控应用服务。
     */
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
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/server")
    @RequiresPermission("system:server:list")
    public CommonResult<Map<String, Object>> serverInfo() {
        return success(adminMonitorServerApplicationService.serverInfo());
    }
}

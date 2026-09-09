package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorCacheApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorCacheController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台 Redis 缓存监控控制器
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor/cache")
public class MonitorCacheController {
    private final AdminMonitorCacheApplicationService adminMonitorCacheApplicationService;

    /**
     * 创建 Redis 缓存监控控制器。
     *
     * @param adminMonitorCacheApplicationService Redis 缓存监控应用服务
     */
    public MonitorCacheController(AdminMonitorCacheApplicationService adminMonitorCacheApplicationService) {
        this.adminMonitorCacheApplicationService = adminMonitorCacheApplicationService;
    }

    /**
     * 查询 Redis 基础运行信息。
     *
     * @return Redis 基础信息、命令统计和内存信息
     */
    @GetMapping("/info")
    @RequiresPermission("system:cache:list")
    public CommonResult<Map<String, Object>> info() {
        return success(adminMonitorCacheApplicationService.info());
    }

    /**
     * 分页查询缓存 Key。
     *
     * @param keyPattern Key 模式，默认 *
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 缓存 Key 分页
     */
    @GetMapping("/keys")
    @RequiresPermission("system:cache:query")
    public CommonResult<Map<String, Object>> keys(@RequestParam(value = "keyPattern", required = false) String keyPattern,
                                                  @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return success(adminMonitorCacheApplicationService.keys(keyPattern, pageNo, pageSize));
    }

    /**
     * 查询指定缓存值。
     *
     * @param key Redis Key
     * @return 缓存详情
     */
    @GetMapping("/value")
    @RequiresPermission("system:cache:query")
    public CommonResult<Map<String, Object>> value(@RequestParam("key") String key) {
        return success(adminMonitorCacheApplicationService.value(key));
    }

    /**
     * 删除指定缓存 Key。
     *
     * @param key Redis Key
     * @return 删除结果
     */
    @DeleteMapping("/key")
    @RequiresPermission("system:cache:clear")
    @OperationLog(moduleName = "Redis缓存监控", businessType = OperationTypeConstants.DELETE,
            operation = "删除平台配置缓存Key", recordRequest = false, recordResponse = false)
    public CommonResult<Boolean> delete(@RequestParam("key") String key) {
        return success(adminMonitorCacheApplicationService.delete(key));
    }
}

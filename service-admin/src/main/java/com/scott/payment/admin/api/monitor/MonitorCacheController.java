package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorCacheApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorCacheController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Monitor Cache 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor/cache")
public class MonitorCacheController {
    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除监控治理数据，按业务规则处理引用校验和删除边界。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/key")
    @RequiresPermission("system:cache:clear")
    public CommonResult<Boolean> delete(@RequestParam("key") String key) {
        return success(adminMonitorCacheApplicationService.delete(key));
    }
}

package com.scott.payment.admin.api.monitor;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHealthController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理端轻量健康检查 HTTP 入口，仅用于确认服务进程可响应，不承载业务依赖深度探测。
 * @status : create
 */
@RestController
public class AdminHealthController {

    /**
     * 后台管理服务健康检查入口，用于网关、注册中心或部署平台探测服务存活状态。
     *
     * @return 当前服务名称
     */
    @GetMapping("/admin/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-admin");
    }
}

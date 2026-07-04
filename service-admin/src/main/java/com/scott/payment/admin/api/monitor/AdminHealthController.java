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
 * @description : 管理后台健康检查控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHealthController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Admin Health 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
public class AdminHealthController {

    /**
     * 后台管理服务健康检查入口，用于网关、注册中心或部署平台探测服务存活状态。
     *
     * @return 当前服务名称
     */
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/admin/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-admin");
    }
}

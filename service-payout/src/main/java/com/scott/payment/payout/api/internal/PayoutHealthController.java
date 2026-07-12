package com.scott.payment.payout.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 代付服务健康检查控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutHealthController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payout Health 管理接口，位于 service-payout 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
public class PayoutHealthController {

    /**
     * 代付核心服务健康检查入口。
     *
     * @return 当前服务名称
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/payout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payout");
    }
}

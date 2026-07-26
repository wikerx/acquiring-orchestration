package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;


@RestController
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentHealthController
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : PaymentHealthController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PaymentHealthController {

    /**
     * 收单支付核心服务健康检查入口。
     *
     * @return 当前服务名称
     */
    @GetMapping("/payment/health")
    public ApiResult<String> health() {
        return success("service-payment");
    }
}

package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户服务健康检查控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantHealthController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Merchant Health 管理接口，位于 service-merchant 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
public class MerchantHealthController {

    /**
     * 商户服务健康检查入口。
     *
     * @return 当前服务名称
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/merchant/health")
    public ApiResult<String> health() {
        return success("service-merchant");
    }
}

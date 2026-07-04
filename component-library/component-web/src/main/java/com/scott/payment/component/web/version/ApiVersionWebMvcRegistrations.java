package com.scott.payment.component.web.version;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiVersionWebMvcRegistrations
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : API 版本路由 MVC 注册器
 * @status : create
 */
public class ApiVersionWebMvcRegistrations implements WebMvcRegistrations {

    /**
     * 注册自定义 RequestMappingHandlerMapping，让包含 {version} 的控制器支持版本降级匹配。
     *
     * @return 自定义路由映射器
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiRequestHandlerMapping();
    }
}

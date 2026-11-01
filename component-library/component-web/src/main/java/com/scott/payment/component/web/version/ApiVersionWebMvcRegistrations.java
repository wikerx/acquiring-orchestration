package com.scott.payment.component.web.version;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API 版本路由 MVC 注册器
 */
public class ApiVersionWebMvcRegistrations implements WebMvcRegistrations {

    /**
     * 注册自定义 RequestMappingHandlerMapping，让包含 {version} 的控制器支持版本降级匹配。
     *
     * @return 自定义路由映射器
     */
    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiRequestHandlerMapping();
    }
}

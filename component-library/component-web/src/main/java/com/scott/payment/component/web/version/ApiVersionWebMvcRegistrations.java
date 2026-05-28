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

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiRequestHandlerMapping();
    }
}

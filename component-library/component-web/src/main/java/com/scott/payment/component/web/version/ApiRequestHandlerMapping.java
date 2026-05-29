package com.scott.payment.component.web.version;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiRequestHandlerMapping
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 支持 {version} 路径变量的 REST API 路由映射
 * @status : create
 */
public class ApiRequestHandlerMapping extends RequestMappingHandlerMapping {

    /**
     * REST 路由中的版本变量占位符，只有包含该占位符的控制器才启用版本匹配和降级逻辑。
     */
    public static final String VERSION_FLAG = "{version}";

    /**
     * 根据控制器上的 RequestMapping 和 ApiVersion 创建版本匹配条件。
     *
     * @param clazz 控制器类型
     * @return API 版本匹配条件
     */
    public static RequestCondition<ApiVersionCondition> createCondition(Class<?> clazz) {
        RequestMapping classRequestMapping = clazz.getAnnotation(RequestMapping.class);
        if (classRequestMapping == null) {
            return null;
        }
        String mappingUrl = resolveMappingUrl(classRequestMapping);
        if (!mappingUrl.contains(VERSION_FLAG)) {
            return null;
        }
        ApiVersion apiVersion = clazz.getAnnotation(ApiVersion.class);
        int version = apiVersion == null ? 1 : apiVersion.apiVersion();
        return new ApiVersionCondition(version);
    }

    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        return null;
    }

    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        return createCondition(handlerType);
    }

    private static String resolveMappingUrl(RequestMapping requestMapping) {
        if (requestMapping.value().length > 0) {
            return requestMapping.value()[0];
        }
        if (requestMapping.path().length > 0) {
            return requestMapping.path()[0];
        }
        return "";
    }
}

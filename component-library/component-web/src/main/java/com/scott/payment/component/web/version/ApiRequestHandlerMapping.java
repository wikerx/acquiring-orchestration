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
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param clazz 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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

    /**
     * 当前版本策略只支持控制器类级别声明，方法级别不单独覆盖版本。
     *
     * @param method 控制器方法
     * @return 方法级版本条件，当前固定返回 null
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param method 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        return null;
    }

    /**
     * 为控制器类型创建版本条件。
     *
     * @param handlerType 控制器类型
     * @return 自定义版本条件
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param handlerType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

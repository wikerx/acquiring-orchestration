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

    /**
     * 当前版本策略只支持控制器类级别声明，方法级别不单独覆盖版本。
     *
     * @param method 控制器方法
     * @return 方法级版本条件，当前固定返回 null
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
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        return createCondition(handlerType);
    }

    /**
     * 解析 resolve Mapping Url 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param requestMapping request Mapping 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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

package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestArgumentResolver
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口解密 DTO 参数解析器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestArgumentResolver
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Request Argument Resolver，位于 service-openapi 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class OpenApiRequestArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断当前参数是否为注解声明的解密 DTO。
     *
     * @param parameter 控制器方法参数
     * @return 是否支持解析
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param parameter 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.getMethod() == null) {
            return false;
        }
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                parameter.getMethod(),
                VerificationAndProcessing.class
        );
        return annotation != null
                && !Void.class.equals(annotation.dataReceiver())
                && annotation.dataReceiver().isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 从请求上下文中取出解密后的 DTO 并注入控制器参数。
     *
     * @param parameter     控制器方法参数
     * @param mavContainer  MVC 容器
     * @param webRequest    Web 请求
     * @param binderFactory 参数绑定工厂
     * @return 解密后的 DTO 对象
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param parameter 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param mavContainer 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param webRequest 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param binderFactory 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "http request can not be resolved");
        }
        Object data = request.getAttribute(OpenApiRequestAttributes.DECRYPTED_DATA);
        if (data == null) {
            throw new ServiceException(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode(), "open api request body is not decrypted");
        }
        return data;
    }
}

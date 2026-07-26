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


@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestArgumentResolver
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiRequestArgumentResolver 解析组件，用于根据输入条件确定配置、路由、字典或上下文结果，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiRequestArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断当前参数是否为注解声明的解密 DTO。
     *
     * @param parameter 控制器方法参数
     * @return 是否支持解析
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

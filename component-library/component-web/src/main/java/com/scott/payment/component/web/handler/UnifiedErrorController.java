package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UnifiedErrorController
 * @date : 2026-05-28 18:32
 * @email : scott_x@163.com
 * @description : 统一兜底错误响应控制器
 * @status : create
 */
@RestController
public class UnifiedErrorController implements ErrorController {

    /**
     * 开放 API 路径前缀，商户开放接口命名空间下的请求需要先完成 Authorization 校验。
     */
    private static final String OPEN_API_REST_PREFIX = "/api/rest/";

    /**
     * 标准授权请求头名称。
     */
    private static final String HEADER_AUTHORIZATION = "authorization";

    /**
     * 处理未命中路由、非法路径和容器层异常。
     *
     * @param request HTTP 请求
     * @return 统一 JSON 错误响应
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public CommonResult<Void> handleError(HttpServletRequest request) {
        int status = resolveStatus(request);
        if (isOpenApiRestRequest(request) && !hasAuthorization(request)) {
            return CommonResult.error(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        }
        return switch (status) {
            case 404 -> CommonResult.error(ApiResultEnum.NOT_FOUND);
            case 405 -> CommonResult.error(ApiResultEnum.METHOD_NOT_ALLOWED);
            default -> status >= HttpStatus.BAD_REQUEST.value() && status < HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ? CommonResult.error(ApiResultEnum.BAD_REQUEST)
                    : CommonResult.error(ApiResultEnum.INTERNAL_SERVER_ERROR);
        };
    }

    private int resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer statusCode) {
            return statusCode;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    /**
     * 判断原始请求是否属于商户开放 API REST 命名空间。
     *
     * @param request HTTP 请求
     * @return true 表示原始请求地址以 /api/rest/ 开头
     */
    private boolean isOpenApiRestRequest(HttpServletRequest request) {
        String requestUri = resolveOriginalRequestUri(request);
        return requestUri != null && requestUri.startsWith(OPEN_API_REST_PREFIX);
    }

    /**
     * 判断请求是否携带授权头。
     *
     * @param request HTTP 请求
     * @return true 表示存在 Authorization 请求头
     */
    private boolean hasAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }

    /**
     * 获取容器转发到 /error 前的原始请求地址。
     *
     * @param request HTTP 请求
     * @return 原始请求地址，缺失时回退到当前请求地址
     */
    private String resolveOriginalRequestUri(HttpServletRequest request) {
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestUri instanceof String originalRequestUri) {
            return originalRequestUri;
        }
        return request.getRequestURI();
    }
}

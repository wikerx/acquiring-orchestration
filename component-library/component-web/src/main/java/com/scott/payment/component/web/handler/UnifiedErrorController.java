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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UnifiedErrorController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Unified Error 管理接口，位于 component-library/component-web 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
public class UnifiedErrorController implements ErrorController {

    /**
     * 处理未命中路由、非法路径和容器层异常。
     *
     * @param request HTTP 请求
     * @return 统一 JSON 错误响应
     */
    /**
     * 处理收单支付业务流程，维护关键状态和异常边界。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public CommonResult<Void> handleError(HttpServletRequest request) {
        int status = resolveStatus(request);
        return switch (status) {
            case 404 -> OpenApiErrorResponseSupport.routeNotFound(
                    request,
                    OpenApiErrorResponseSupport.resolveOriginalRequestUri(request)
            );
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
}

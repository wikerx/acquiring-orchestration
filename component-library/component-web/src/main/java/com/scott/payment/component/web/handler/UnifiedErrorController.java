package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
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
     * 处理未命中路由、非法路径和容器层异常。
     *
     * @param request HTTP 请求
     * @return 统一 JSON 错误响应
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public CommonResult<Void> handleError(HttpServletRequest request) {
        int status = resolveStatus(request);
        if (HttpStatus.NOT_FOUND.value() == status) {
            return CommonResult.error(ApiCoResultEnum.CO_NOT_FOUND);
        }
        if (HttpStatus.METHOD_NOT_ALLOWED.value() == status) {
            return CommonResult.error(ApiCoResultEnum.CO_METHOD_NOT_ALLOWED);
        }
        if (status >= HttpStatus.BAD_REQUEST.value() && status < HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return CommonResult.error(ApiCoResultEnum.CO_BAD_REQUEST);
        }
        return CommonResult.error(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR);
    }

    private int resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer) {
            return (Integer) status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}

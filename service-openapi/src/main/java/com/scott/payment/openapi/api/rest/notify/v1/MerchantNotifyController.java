package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.model.ApiResult;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

@RestController
@RequestMapping("/openapi/v1/merchant-notifies")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotifyController
 * @date : 2026-05-28 10:23
 * @email : scott_x@163.com
 * @description : Merchant Notify Controller 控制器，位于 商户开放接口服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class MerchantNotifyController {

    /**
     * 回调类入口安全校验组件。
     */
    private final OpenApiCallbackSecuritySupport callbackSecuritySupport;

    /**
     * 创建商户通知控制器。
     *
     * @param callbackSecuritySupport 回调类入口安全校验组件
     */
    public MerchantNotifyController(OpenApiCallbackSecuritySupport callbackSecuritySupport) {
        this.callbackSecuritySupport = callbackSecuritySupport;
    }

    /**
     * 重试商户通知。
     *
     * @return 重试受理结果
     */
    @PostMapping("/retry")
    public ApiResult<String> retry(HttpServletRequest request) {
        callbackSecuritySupport.verifyNotifyRetryToken(request);
        return success("accepted");
    }
}

package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.model.ApiResult;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotifyController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIMerchant Notify 管理接口，位于 service-openapi 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/openapi/v1/merchant-notifies")
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/retry")
    public ApiResult<String> retry(HttpServletRequest request) {
        callbackSecuritySupport.verifyNotifyRetryToken(request);
        return success("accepted");
    }
}

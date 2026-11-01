package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.model.ApiResult;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * 受维护令牌保护的商户通知重试入口；不得作为公开商户 API 使用。
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
    @PostMapping("/retry")
    public ApiResult<String> retry(HttpServletRequest request) {
        callbackSecuritySupport.verifyNotifyRetryToken(request);
        return success("accepted");
    }
}

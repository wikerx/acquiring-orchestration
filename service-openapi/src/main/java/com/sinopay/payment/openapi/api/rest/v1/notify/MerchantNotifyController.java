package com.sinopay.payment.openapi.api.rest.v1.notify;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotifyController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户通知回调控制器
 * @status : create
 */
@RestController
@RequestMapping("/openapi/v1/merchant-notifies")
public class MerchantNotifyController {

    @PostMapping("/retry")
    public ApiResult<String> retry() {
        return ApiResult.success("accepted");
    }
}


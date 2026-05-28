package com.sinopay.payment.openapi.api.rest.v1.notify;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi/v1/merchant-notifies")
public class MerchantNotifyController {

    @PostMapping("/retry")
    public ApiResult<String> retry() {
        return ApiResult.success("accepted");
    }
}


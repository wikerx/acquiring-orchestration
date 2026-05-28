package com.sinopay.payment.channel.api.internal;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChannelHealthController {

    @GetMapping("/channel/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-channel");
    }
}


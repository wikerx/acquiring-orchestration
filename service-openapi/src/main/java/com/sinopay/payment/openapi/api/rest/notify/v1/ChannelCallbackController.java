package com.sinopay.payment.openapi.api.rest.notify.v1;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackController
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 渠道回调入口控制器
 * @status : create
 */
@RestController
@RequestMapping("/channel/v1/callbacks")
public class ChannelCallbackController {

    /**
     * 接收渠道侧回调通知。
     *
     * @param channelCode 渠道编码
     * @return 回调受理结果
     */
    @PostMapping("/{channelCode}")
    public ApiResult<String> receive(@PathVariable String channelCode) {
        return ApiResult.success(channelCode + " accepted");
    }
}

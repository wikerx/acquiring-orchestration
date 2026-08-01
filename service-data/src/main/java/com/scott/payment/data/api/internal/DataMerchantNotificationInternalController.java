package com.scott.payment.data.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.data.application.MerchantNotificationApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationInternalController
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知内部接口，只向通过 HMAC 服务间鉴权的定时补偿调用方开放
 * @status : create
 */
@RestController
@RequestMapping("/internal/data/merchant-notifications")
public class DataMerchantNotificationInternalController {

    /** 商户通知应用服务。 */
    private final MerchantNotificationApplicationService applicationService;

    /**
     * 创建商户通知内部接口。
     *
     * @param applicationService 商户通知应用服务
     */
    public DataMerchantNotificationInternalController(MerchantNotificationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 触发指定交易时间分表的到期商户通知补偿。
     *
     * @param commandDTO 补偿命令
     * @return 成功通知数量
     */
    @PostMapping("/notify-due")
    public CommonResult<Integer> notifyDue(@RequestBody MerchantNotificationNotifyDueCommandDTO commandDTO) {
        return success(applicationService.notifyDue(commandDTO));
    }
}

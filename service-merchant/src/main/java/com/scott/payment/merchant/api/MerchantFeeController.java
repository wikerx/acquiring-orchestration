package com.scott.payment.merchant.api;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.MerchantFinanceApplicationService;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeController
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前生效费用配置只读接口，不暴露模板库和模板来源元数据。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/fees")
public class MerchantFeeController {
    private final MerchantFinanceApplicationService applicationService;

    /** 构造当前费率接口。 */
    public MerchantFeeController(MerchantFinanceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询当前登录商户正在使用的生效费率。 */
    @GetMapping("/current")
    @RequiresPermission("merchant:fee:view")
    public CommonResult<CurrentFeeResponse> getCurrentFee() {
        return success(applicationService.getCurrentFee());
    }
}

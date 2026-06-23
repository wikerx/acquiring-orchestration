package com.scott.payment.checkout.controller;

import com.scott.payment.checkout.application.CheckoutCountryConfigApplicationService;
import com.scott.payment.checkout.dto.CheckoutCountryConfigResponse;
import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收银台公开配置控制器，提供付款人页面所需的只读展示配置。
 */
@RestController
public class CheckoutCountryConfigController {

    /**
     * 收银台国家地区配置应用服务。
     */
    private final CheckoutCountryConfigApplicationService countryConfigApplicationService;

    /**
     * 创建收银台国家地区配置控制器。
     *
     * @param countryConfigApplicationService 收银台国家地区配置应用服务
     */
    public CheckoutCountryConfigController(CheckoutCountryConfigApplicationService countryConfigApplicationService) {
        this.countryConfigApplicationService = countryConfigApplicationService;
    }

    /**
     * 查询收银台国家地区下拉配置。
     *
     * @return 国家地区配置列表
     */
    @GetMapping("/checkout/config/countries")
    public ApiResult<List<CheckoutCountryConfigResponse>> listCountries() {
        return ApiResult.success(countryConfigApplicationService.listCountries());
    }
}

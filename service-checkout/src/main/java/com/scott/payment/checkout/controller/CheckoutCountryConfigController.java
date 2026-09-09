package com.scott.payment.checkout.controller;

import com.scott.payment.checkout.application.CheckoutCountryConfigApplicationService;
import com.scott.payment.checkout.dto.CheckoutCountryConfigResponse;
import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : 收银台国家或地区配置 HTTP 控制器，位于 收银台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
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

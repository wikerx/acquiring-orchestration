package com.scott.payment.checkout.controller;

import com.scott.payment.checkout.application.CheckoutCountryConfigApplicationService;
import com.scott.payment.checkout.dto.CheckoutCountryConfigResponse;
import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Checkout Country Config Controller 控制器，位于 收银台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
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

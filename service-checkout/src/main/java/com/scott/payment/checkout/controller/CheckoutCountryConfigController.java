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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Checkout Country Config 管理接口，位于 service-checkout 的接口层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/checkout/config/countries")
    public ApiResult<List<CheckoutCountryConfigResponse>> listCountries() {
        return ApiResult.success(countryConfigApplicationService.listCountries());
    }
}

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
 * @description : CheckoutCountryConfigController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 收银台服务层，输入输出边界由所在包和公开方法契约限定。
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

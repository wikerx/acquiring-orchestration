package com.scott.payment.checkout.dto;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收银台国家地区下拉配置响应。 @param countryCode        ISO 3166-1 alpha-2 国家地区代码 @param countryName        收银台英文展示名称 @param countryNameLocal   收银台本地化展示名称 @param flagIconUrl        国家或地区 Logo 地址；为空时前端展示默认地球图标 @param defaultLanguage    收银台默认语言 @param supportedLanguages 当前收银台支持语言列表 @param sortNo             展示排序号
 * @status : create
 */
public record CheckoutCountryConfigResponse(String countryCode,
                                            String countryName,
                                            String countryNameLocal,
                                            String flagIconUrl,
                                            String defaultLanguage,
                                            List<String> supportedLanguages,
                                            Integer sortNo) {
}

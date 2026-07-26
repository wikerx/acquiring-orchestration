package com.scott.payment.checkout.dto;

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigResponse
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Checkout Country Config Response 不可变数据结构，位于 收银台服务，用于在当前调用链中传递固定字段集合，不承担状态写入职责。
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

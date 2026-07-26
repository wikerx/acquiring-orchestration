package com.scott.payment.checkout.dto;

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigResponse
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : CheckoutCountryConfigResponse 不可变数据载体，用于在模块内部传递结构化参数或结果，位于 收银台服务层，输入输出边界由所在包和公开方法契约限定。
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

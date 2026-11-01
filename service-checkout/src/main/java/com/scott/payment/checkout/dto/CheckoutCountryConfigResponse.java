package com.scott.payment.checkout.dto;

import java.util.List;


/**
 * 收银台国家地区展示配置的不可变响应模型。
 *
 * @param countryCode ISO 3166-1 alpha-2 大写国家地区代码
 * @param countryName 英文展示名称
 * @param countryNameLocal 本地化展示名称，当前优先使用中文名称
 * @param flagIconUrl 已编码的国旗图标 Data URL；空字符串表示由前端使用默认图标
 * @param defaultLanguage 收银台默认文案语言
 * @param supportedLanguages 收银台当前支持切换的文案语言
 * @param sortNo 展示顺序，数值越小越靠前
 */
public record CheckoutCountryConfigResponse(String countryCode,
                                            String countryName,
                                            String countryNameLocal,
                                            String flagIconUrl,
                                            String defaultLanguage,
                                            List<String> supportedLanguages,
                                            Integer sortNo) {
}

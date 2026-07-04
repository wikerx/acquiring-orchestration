package com.scott.payment.openapi.dto.body.iso;

import lombok.Data;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryQueryRequestDTO
 * @date : 2026-06-03 15:05
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 查询国家地区请求参数
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryQueryRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIIso Country Query Request 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class IsoCountryQueryRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求 DTO 在测试和日志序列化时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * ISO 3166-1 alpha-2 两位字母国家地区代码。
     * <p>
     * 示例：US、CN、HK。为空时不按两位字母代码过滤。
     */
    @Pattern(regexp = "^[A-Z]{2}$", message = "alpha2 must be ISO 3166-1 alpha-2 uppercase code")
    private String alpha2;

    /**
     * ISO 3166-1 alpha-3 三位字母国家地区代码。
     * <p>
     * 示例：USA、CHN、HKG。为空时不按三位字母代码过滤。
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "alpha3 must be ISO 3166-1 alpha-3 uppercase code")
    private String alpha3;

    /**
     * ISO 3166-1 numeric 三位数字国家地区代码。
     * <p>
     * 示例：840、156、344。为空时不按三位数字代码过滤。
     */
    @Pattern(regexp = "^\\d{3}$", message = "numeric must be ISO 3166-1 three-digit code")
    private String numeric;

    /**
     * 国家或地区英文全称。
     * <p>
     * 示例：United States of America。为空时不按英文全称过滤。
     */
    @Size(max = 128, message = "englishName length must be less than or equal to 128")
    private String englishName;

    /**
     * 国家或地区英文简称。
     * <p>
     * 示例：United States。为空时不按英文简称过滤。
     */
    @Size(max = 128, message = "shortEnglishName length must be less than or equal to 128")
    private String shortEnglishName;

    /**
     * 国家或地区中文名称。
     * <p>
     * 示例：美国、中国香港。为空时不按中文名称过滤。
     */
    @Size(max = 128, message = "chineseName length must be less than or equal to 128")
    private String chineseName;

    /**
     * 七大洲代码过滤条件。
     * <p>
     * 可选值：AS/EU/AF/NA/SA/OC/AN。为空时不按大洲过滤。
     */
    @Pattern(regexp = "^(AS|EU|AF|NA|SA|OC|AN)$", message = "continentCode must be one of AS, EU, AF, NA, SA, OC, AN")
    private String continentCode;

    /**
     * 主要语言代码过滤条件。
     * <p>
     * 示例：en、zh。为空时不按主要语言过滤。
     */
    @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$", message = "primaryLanguageCode must be a valid language code")
    private String primaryLanguageCode;

    /**
     * 默认币种过滤条件。
     * <p>
     * 例如 USD、EUR、CNY。为空时不按默认币种过滤。
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "currencyAlpha3Code must be ISO 4217 alphabetic code")
    private String currencyAlpha3Code;
}

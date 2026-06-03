package com.scott.payment.openapi.dto.body.iso;

import lombok.Data;

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
@Data
public class IsoCountryQueryRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求 DTO 在测试和日志序列化时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 国家地区查询关键字。
     * <p>
     * 支持 alpha-2、alpha-3、numeric、英文名、中文名、七大洲、主要语言和默认币种。为空时查询全部国家地区。
     */
    private String keyword;

    /**
     * 七大洲代码过滤条件。
     * <p>
     * 可选值：AS/EU/AF/NA/SA/OC/AN。为空时不按大洲过滤。
     */
    private String continentCode;

    /**
     * 默认币种过滤条件。
     * <p>
     * 例如 USD、EUR、CNY。为空时不按默认币种过滤。
     */
    private String currencyCode;
}

package com.scott.payment.openapi.dto.body.iso;

import lombok.Data;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyQueryRequestDTO
 * @date : 2026-06-03 15:06
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 查询币种请求参数
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyQueryRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIIso Currency Query Request 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class IsoCurrencyQueryRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求 DTO 在测试和日志序列化时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * ISO 4217 三位字母币种代码。
     * <p>
     * 示例：USD、CNY、JPY。为空时不按三位字母币种代码过滤。
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "alphabeticCode must be ISO 4217 alphabetic code")
    private String alphabeticCode;

    /**
     * ISO 4217 三位数字币种代码。
     * <p>
     * 示例：840、156、392。为空时不按三位数字币种代码过滤。
     */
    @Pattern(regexp = "^\\d{3}$", message = "numericCode must be ISO 4217 three-digit numeric code")
    private String numericCode;

    /**
     * 币种英文名称。
     * <p>
     * 示例：US Dollar、Yuan Renminbi。为空时不按英文名称过滤。
     */
    @Size(max = 128, message = "englishName length must be less than or equal to 128")
    private String englishName;

    /**
     * 币种中文名称。
     * <p>
     * 示例：美元、人民币。为空时不按中文名称过滤。
     */
    @Size(max = 128, message = "chineseName length must be less than or equal to 128")
    private String chineseName;

    /**
     * 币种符号或展示图标。
     * <p>
     * 示例：$、¥、€。为空时不按币种符号过滤。
     */
    @Size(max = 16, message = "currencySymbol length must be less than or equal to 16")
    private String currencySymbol;
}

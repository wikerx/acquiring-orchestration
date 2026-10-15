package com.scott.payment.openapi.dto.body.iso;

import lombok.Data;

import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryQueryRequestDTO
 * @date : 2026-06-03 15:05
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 国家地区查询参数，仅开放三种 ISO 3166-1 代码；解码入口拒绝未知字段，避免忽略条件后返回全量数据。
 * @status : update
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
     * 示例：US、CN、HK。未传或为 null 时不参与过滤，传入时必须精确匹配。
     */
    @Pattern(regexp = "^[A-Z]{2}$", message = "alpha2 must be ISO 3166-1 alpha-2 uppercase code")
    private String alpha2;

    /**
     * ISO 3166-1 alpha-3 三位字母国家地区代码。
     * <p>
     * 示例：USA、CHN、HKG。未传或为 null 时不参与过滤，多代码条件按 AND 组合。
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "alpha3 must be ISO 3166-1 alpha-3 uppercase code")
    private String alpha3;

    /**
     * ISO 3166-1 numeric 三位数字国家地区代码。
     * <p>
     * 示例：840、156、004。必须使用字符串保留前导零，未传或为 null 时不参与过滤。
     */
    @Pattern(regexp = "^\\d{3}$", message = "numeric must be ISO 3166-1 three-digit code")
    private String numeric;
}

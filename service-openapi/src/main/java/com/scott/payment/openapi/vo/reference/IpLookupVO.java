package com.scott.payment.openapi.vo.reference;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupVO
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 归属检索响应，仅暴露查询值、命中标识和最小必要地域信息
 * @status : create
 */
@Data
public class IpLookupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否命中归属区间，不允许为空。 */
    private Boolean matched;

    /** 规范化后的 IP，不允许为空；响应 data 必须加密，日志不得完整输出。 */
    private String ipAddress;

    /** IP 类型，格式为 IPV4 或 IPV6，不允许为空。 */
    private String ipType;

    /** 国家或地区 ISO Alpha-2 编码，未命中时为空。 */
    private String countryAlpha2;

    /** 国家或地区 ISO Alpha-3 编码，未命中时为空。 */
    private String countryAlpha3;

    /** 国家或地区 ISO Numeric 编码，未命中时为空。 */
    private String countryNumeric;

    /** 国家或地区英文名称，未命中时为空。 */
    private String countryName;

    /** 州或省名称，未命中或数据源未提供时为空。 */
    private String stateProvince;

    /** 城市名称，未命中或数据源未提供时为空。 */
    private String city;
}

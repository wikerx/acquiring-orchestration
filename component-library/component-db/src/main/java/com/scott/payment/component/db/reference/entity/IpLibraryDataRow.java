package com.scott.payment.component.db.reference.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDataRow
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 库区间查询投影，仅承载对外归属检索所需的最小字段集合
 * @status : create
 */
@Data
public class IpLibraryDataRow {

    /** 国家或地区 ISO Alpha-2 编码，允许为空，非敏感字段。 */
    private String countryAlpha2;

    /** 国家或地区 ISO Alpha-3 编码，允许为空，非敏感字段。 */
    private String countryAlpha3;

    /** 国家或地区 ISO Numeric 编码，允许为空，非敏感字段。 */
    private String countryNumeric;

    /** 国家或地区英文名称，允许为空，非敏感字段。 */
    private String countryName;

    /** 州或省名称，允许为空，非敏感字段。 */
    private String stateProvince;

    /** 城市名称，允许为空，非敏感字段。 */
    private String city;
}

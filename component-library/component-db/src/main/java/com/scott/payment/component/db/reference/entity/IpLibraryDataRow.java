package com.scott.payment.component.db.reference.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDataRow
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 库精确查询投影，承载对外归属字段及管理端区间审计字段
 * @status : create
 */
@Data
public class IpLibraryDataRow {

    /** 命中区间主键，不允许为空。 */
    private Long id;

    /** 区间起始无符号数值，不允许为空。 */
    private String ipNumberStart;

    /** 区间结束无符号数值，不允许为空。 */
    private String ipNumberEnd;

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

    /** 命中记录的数据版本，不允许为空。 */
    private String dataVersion;

    /** 命中记录创建时间，允许为空。 */
    private LocalDateTime createTime;

    /** 命中记录创建人，允许为空。 */
    private String createBy;
}

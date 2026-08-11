package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupResult
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : 公共 IP 归属检索结果，区分合法输入未命中与平台配置或数据访问异常
 * @status : create
 *
 * @param matched         是否命中归属区间，不允许为空
 * @param ipAddress       规范化后的精确 IP，可识别字段，日志中不得完整输出
 * @param ipType          IP 类型，格式为 IPV4 或 IPV6，不允许为空
 * @param countryAlpha2   国家或地区 ISO Alpha-2 编码，允许为空
 * @param countryAlpha3   国家或地区 ISO Alpha-3 编码，允许为空
 * @param countryNumeric  国家或地区 ISO Numeric 编码，允许为空
 * @param countryName     国家或地区英文名称，允许为空
 * @param stateProvince   州或省名称，允许为空
 * @param city            城市名称，允许为空
 */
public record IpLookupResult(Boolean matched,
                             String ipAddress,
                             String ipType,
                             String countryAlpha2,
                             String countryAlpha3,
                             String countryNumeric,
                             String countryName,
                             String stateProvince,
                             String city) {

    /**
     * 构造未命中的合法 IP 查询结果。
     *
     * @param ipAddress 规范化后的 IP
     * @param ipType    IP 类型
     * @return 未命中结果
     */
    public static IpLookupResult miss(String ipAddress, String ipType) {
        return new IpLookupResult(false, ipAddress, ipType, null, null, null, null, null, null);
    }
}

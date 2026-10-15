package com.scott.payment.component.db.reference.model;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupDetail
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : IP 精确查询公共详情，同时承载 OpenAPI 归属结果和管理端区间审计字段
 * @status : create
 *
 * @param result        对外可使用的 IP 归属结果，不允许为空
 * @param id            命中区间主键，未命中时为空
 * @param ipNumberStart 命中区间起始无符号数值，未命中时为空
 * @param ipNumberEnd   命中区间结束无符号数值，未命中时为空
 * @param dataVersion   命中记录的数据版本，未命中时为空
 * @param createTime    命中记录的创建时间，未命中时为空
 * @param createBy      命中记录的创建人，未命中时为空
 */
public record IpLookupDetail(IpLookupResult result,
                             Long id,
                             String ipNumberStart,
                             String ipNumberEnd,
                             String dataVersion,
                             LocalDateTime createTime,
                             String createBy) {

    /** 创建不包含区间审计字段的合法未命中详情。 */
    public static IpLookupDetail miss(IpLookupResult result) {
        return new IpLookupDetail(result, null, null, null, null, null, null);
    }
}

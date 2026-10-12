package com.scott.payment.component.db.reference.service;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataLookupService
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 与卡 BIN 基础数据的公共只读检索契约，统一隔离分片、十一位区间和数据源路由等数据库细节
 * @status : create
 */
public interface ReferenceDataLookupService {

    /**
     * 查询精确 IPv4 或 IPv6 的归属信息。
     *
     * @param ipAddress IP 字面量，不接受域名、CIDR 或范围
     * @return 命中或未命中的稳定查询结果
     * @throws IllegalArgumentException IP 格式不合法时抛出
     * @throws ServiceException IP 分片配置缺失、重复或与物理表不一致时抛出
     */
    IpLookupResult lookupIp(String ipAddress);

    /**
     * 查询精确 IP，并返回本次查询实际使用的路由版本。
     *
     * @param ipAddress IP 字面量，不接受域名、CIDR 或范围
     * @return 路由版本与稳定查询结果
     */
    IpLookupSnapshot lookupIpSnapshot(String ipAddress);

    /**
     * 查询 6 至 11 位纯数字卡 BIN 的启用归属信息。
     *
     * <p>输入不足 11 位时右补零；数据库按十一位数值区间匹配，多条记录覆盖同一数值时
     * 返回主键最大的增量记录。</p>
     *
     * @param cardBin 6 至 11 位纯数字 BIN
     * @return 命中或未命中的十一位查询结果
     * @throws IllegalArgumentException BIN 格式不合法时抛出
     * @throws ServiceException 数据库返回的记录违反区间数据约束时抛出
     */
    CardBinLookupResult lookupCardBin(String cardBin);

}

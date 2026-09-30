package com.scott.payment.component.db.reference.service;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.CardBinLookupSnapshot;
import com.scott.payment.component.db.reference.model.IpLookupResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataLookupService
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 与卡 BIN 基础数据的公共只读检索契约，统一隔离分片、区间和生效状态等数据库细节
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
     * 查询 6 至 11 位纯数字卡 BIN 的当前有效归属信息。
     *
     * @param cardBin 6 至 11 位纯数字 BIN，不接受完整卡号
     * @return 命中或未命中的稳定查询结果
     * @throws IllegalArgumentException BIN 格式不合法时抛出
     * @throws ServiceException 数据库返回的命中精度违反对外查询约束时抛出
     */
    CardBinLookupResult lookupCardBin(String cardBin);

    /**
     * 查询卡 BIN 并取得会影响缓存有效性的生效、失效时间边界。
     *
     * @param cardBin 6 至 11 位纯数字 BIN
     * @return 当前结果；仅正向命中提供影响缓存有效性的时间边界，未命中不缓存
     */
    CardBinLookupSnapshot lookupCardBinSnapshot(String cardBin);

}

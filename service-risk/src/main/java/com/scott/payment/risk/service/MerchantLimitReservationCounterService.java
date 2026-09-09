package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.RedisReservationMarkerState;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationCounterService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额 Redis 投影生命周期服务。
 * @status : create
 */
public interface MerchantLimitReservationCounterService {

    /**
     * 原子回滚 Redis 累计金额和预占 marker。
     *
     * @param reservation 持久化预占事实
     * @return true 表示回滚成功或 Redis 投影已不存在
     */
    boolean rollback(MerchantLimitReservationDO reservation);

    /**
     * 查询 Redis 预占 marker 三态；连接或脚本异常必须返回 UNKNOWN，不能伪装为 ABSENT。
     */
    RedisReservationMarkerState markerState(MerchantLimitReservationDO reservation);
}

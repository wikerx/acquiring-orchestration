package com.scott.payment.risk.domain;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisReservationMarkerState
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Redis 预占标记探测结果。
 * @status : create
 */
public enum RedisReservationMarkerState {
    /** Redis 中存在本笔累计限额预占标记。 */
    PRESENT,

    /** Redis 查询成功且确认预占标记不存在。 */
    ABSENT,

    UNKNOWN
}

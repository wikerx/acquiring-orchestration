package com.scott.payment.risk.domain;

/**
 * Redis 预占标记探测结果。
 */
public enum RedisReservationMarkerState {
    /** Redis 中存在本笔累计限额预占标记。 */
    PRESENT,

    /** Redis 查询成功且确认预占标记不存在。 */
    ABSENT,

    UNKNOWN
}

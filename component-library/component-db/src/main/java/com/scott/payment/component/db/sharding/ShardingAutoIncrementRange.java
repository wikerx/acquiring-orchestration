package com.scott.payment.component.db.sharding;

/**
 * 分表物理表 AUTO_INCREMENT 安全范围。
 *
 * @param prefix     yyyyQQ 前缀
 * @param startValue 起始自增值
 * @param maxValue   最大安全值
 */
public record ShardingAutoIncrementRange(long prefix, long startValue, long maxValue) {
}

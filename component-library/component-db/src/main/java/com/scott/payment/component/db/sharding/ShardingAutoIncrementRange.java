package com.scott.payment.component.db.sharding;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementRange
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表物理表 AUTO_INCREMENT 安全范围。 @param prefix     yyyyQQ 前缀 @param startValue 起始自增值 @param maxValue   最大安全值
 * @status : create
 */
public record ShardingAutoIncrementRange(long prefix, long startValue, long maxValue) {
}

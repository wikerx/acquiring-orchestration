package com.scott.payment.component.db.sharding;


/**
 * Sharding Auto Increment Range 不可变数据结构，位于 公共组件库，用于在当前调用链中传递固定字段集合，不承担状态写入职责。
 */
public record ShardingAutoIncrementRange(long prefix, long startValue, long maxValue) {
}

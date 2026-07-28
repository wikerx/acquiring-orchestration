package com.scott.payment.component.db.sharding;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementRange
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Auto Increment Range 不可变数据结构，位于 公共组件库，用于在当前调用链中传递固定字段集合，不承担状态写入职责。
 * @status : create
 */
public record ShardingAutoIncrementRange(long prefix, long startValue, long maxValue) {
}

package com.scott.payment.component.db.sharding;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementRange
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingAutoIncrementRange 不可变数据载体，用于在模块内部传递结构化参数或结果，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public record ShardingAutoIncrementRange(long prefix, long startValue, long maxValue) {
}

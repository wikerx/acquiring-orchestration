package com.scott.payment.component.db.sharding;

import java.util.Objects;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingQuarter
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingQuarter 不可变数据载体，用于在模块内部传递结构化参数或结果，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public record ShardingQuarter(int year, int quarter) implements Comparable<ShardingQuarter> {

    /**
     * 创建季度值对象并校验季度范围。
     *
     * @param year    年份
     * @param quarter 季度，取值 1 到 4
     */
    public ShardingQuarter {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("quarter must be between 1 and 4");
        }
    }

    /**
     * 返回下一个自然季度。
     *
     * @return 下一季度
     */
    public ShardingQuarter next() {
        if (quarter == 4) {
            return new ShardingQuarter(year + 1, 1);
        }
        return new ShardingQuarter(year, quarter + 1);
    }

    /**
     * 返回季度后缀。
     *
     * @return yyyyQQ 格式后缀
     */
    public String suffix() {
        return String.format("%d%02d", year, quarter);
    }

    /**
     * 返回便于页面展示的季度文本。
     *
     * @return yyyy-Qq 格式文本
     */
    public String displayName() {
        return String.format("%d-Q%d", year, quarter);
    }

    /**
     * 比较两个季度的自然时间先后。
     *
     * @param other 另一个季度
     * @return 比较结果
     */
    @Override
    public int compareTo(ShardingQuarter other) {
        Objects.requireNonNull(other, "other must not be null");
        int yearCompare = Integer.compare(year, other.year);
        if (yearCompare != 0) {
            return yearCompare;
        }
        return Integer.compare(quarter, other.quarter);
    }
}

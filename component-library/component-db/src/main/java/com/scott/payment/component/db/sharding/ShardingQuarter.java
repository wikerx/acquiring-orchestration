package com.scott.payment.component.db.sharding;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingQuarter
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表季度值对象。 <p>季度编号使用 1 到 4，季度后缀使用 yyyyQQ，其中 QQ 为 01、02、03、04。 该对象只表达路由维度，不代表自然月份。</p>
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String suffix() {
        return String.format("%d%02d", year, quarter);
    }

    /**
     * 返回便于页面展示的季度文本。
     *
     * @return yyyy-Qq 格式文本
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param other 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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

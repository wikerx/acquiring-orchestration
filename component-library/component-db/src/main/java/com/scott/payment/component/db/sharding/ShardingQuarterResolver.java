package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingQuarterResolver
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Quarter Resolver，位于 component-library/component-db 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class ShardingQuarterResolver {

    /**
     * 按配置时区计算当前季度。
     *
     * @param properties 分表配置
     * @return 当前季度
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ShardingQuarter currentQuarter(PaymentQuarterShardingProperties properties) {
        ZoneId zoneId = zoneId(properties);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return fromDateTime(now.toLocalDateTime());
    }

    /**
     * 按交易时间计算所属季度。
     *
     * @param transactionDateTime 交易时间
     * @return 所属季度
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param transactionDateTime 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ShardingQuarter fromDateTime(LocalDateTime transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        int quarter = (transactionDateTime.getMonthValue() - 1) / 3 + 1;
        return new ShardingQuarter(transactionDateTime.getYear(), quarter);
    }

    /**
     * 计算配置范围内从起始季度到结束季度的全部季度。
     *
     * @param rule 单表分表规则
     * @return 季度列表
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rule 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<ShardingQuarter> quartersInRange(PaymentQuarterShardingProperties.TableRule rule) {
        ShardingQuarter start = startQuarter(rule);
        ShardingQuarter end = endQuarter(rule);
        if (start.compareTo(end) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding start quarter must not be after end quarter");
        }
        List<ShardingQuarter> quarters = new ArrayList<>();
        ShardingQuarter cursor = start;
        while (cursor.compareTo(end) <= 0) {
            quarters.add(cursor);
            cursor = cursor.next();
        }
        return quarters;
    }

    /**
     * 判断目标季度是否在单表配置范围内。
     *
     * @param rule    单表分表规则
     * @param quarter 目标季度
     * @return true 表示在范围内
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rule 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param quarter 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public boolean inRange(PaymentQuarterShardingProperties.TableRule rule, ShardingQuarter quarter) {
        if (quarter == null) {
            return false;
        }
        return quarter.compareTo(startQuarter(rule)) >= 0 && quarter.compareTo(endQuarter(rule)) <= 0;
    }

    /**
     * 读取配置时区。
     *
     * @param properties 分表配置
     * @return 时区
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ZoneId zoneId(PaymentQuarterShardingProperties properties) {
        String timezone = properties == null ? null : properties.getDatabaseTimezone();
        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Shanghai";
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "invalid sharding database timezone");
        }
    }

    /**
     * 获取单表起始季度。
     *
     * @param rule 单表分表规则
     * @return 起始季度
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rule 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ShardingQuarter startQuarter(PaymentQuarterShardingProperties.TableRule rule) {
        validateRuleQuarter(rule == null ? null : rule.getStartYear(), rule == null ? null : rule.getStartQuarter(), "start");
        return new ShardingQuarter(rule.getStartYear(), rule.getStartQuarter());
    }

    /**
     * 获取单表结束季度。
     *
     * @param rule 单表分表规则
     * @return 结束季度
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rule 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ShardingQuarter endQuarter(PaymentQuarterShardingProperties.TableRule rule) {
        validateRuleQuarter(rule == null ? null : rule.getEndYear(), rule == null ? null : rule.getEndQuarter(), "end");
        return new ShardingQuarter(rule.getEndYear(), rule.getEndQuarter());
    }

    private void validateRuleQuarter(Integer year, Integer quarter, String label) {
        if (year == null || quarter == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), label + " sharding year and quarter are required");
        }
        if (quarter < 1 || quarter > 4) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), label + " sharding quarter must be between 1 and 4");
        }
    }
}

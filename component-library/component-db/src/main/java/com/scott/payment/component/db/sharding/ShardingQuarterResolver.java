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
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Quarter Resolver 解析组件，位于 公共组件库，根据请求路径、配置、分表条件或协议字段解析后续处理需要的标准结果。
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
    public ShardingQuarter currentQuarter(TransactionShardingGovernanceProperties properties) {
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
    public List<ShardingQuarter> quartersInRange(TransactionShardingGovernanceProperties.TableRule rule) {
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
    public boolean inRange(TransactionShardingGovernanceProperties.TableRule rule, ShardingQuarter quarter) {
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
    public ZoneId zoneId(TransactionShardingGovernanceProperties properties) {
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
    public ShardingQuarter startQuarter(TransactionShardingGovernanceProperties.TableRule rule) {
        validateRuleQuarter(rule == null ? null : rule.getStartYear(), rule == null ? null : rule.getStartQuarter(), "start");
        return new ShardingQuarter(rule.getStartYear(), rule.getStartQuarter());
    }

    /**
     * 获取单表结束季度。
     *
     * @param rule 单表分表规则
     * @return 结束季度
     */
    public ShardingQuarter endQuarter(TransactionShardingGovernanceProperties.TableRule rule) {
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

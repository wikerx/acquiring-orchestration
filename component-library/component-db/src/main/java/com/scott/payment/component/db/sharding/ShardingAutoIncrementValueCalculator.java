package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementValueCalculator
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表自动自增值calculator协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
 * @status : create
 */
@Component
public class ShardingAutoIncrementValueCalculator {

    /**
     * {@code BIGINT_MAX_VALUE}常量，统一 {@code ShardingAutoIncrementValueCalculator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final BigInteger BIGINT_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * 计算目标季度的 AUTO_INCREMENT 安全范围。
     *
     * @param properties 分表配置
     * @param quarter    目标季度
     * @return AUTO_INCREMENT 范围
     */
    public ShardingAutoIncrementRange calculate(TransactionShardingGovernanceProperties properties, ShardingQuarter quarter) {
        if (quarter == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding quarter is required");
        }
        TransactionShardingGovernanceProperties.IdGenerator idGenerator = properties == null
                ? new TransactionShardingGovernanceProperties.IdGenerator()
                : properties.getIdGenerator();
        if (idGenerator == null) {
            idGenerator = new TransactionShardingGovernanceProperties.IdGenerator();
        }
        int sequenceWidth = idGenerator.getSequenceWidth() == null ? 12 : idGenerator.getSequenceWidth();
        long startSequence = idGenerator.getStartSequence() == null ? 1L : idGenerator.getStartSequence();
        long maxSequence = idGenerator.getMaxSequence() == null ? 999_999_999_999L : idGenerator.getMaxSequence();
        validateSequence(sequenceWidth, startSequence, maxSequence);

        long prefix = Long.parseLong(quarter.suffix());
        BigInteger multiplier = BigInteger.TEN.pow(sequenceWidth);
        BigInteger startValue = BigInteger.valueOf(prefix).multiply(multiplier).add(BigInteger.valueOf(startSequence));
        BigInteger maxValue = BigInteger.valueOf(prefix).multiply(multiplier).add(BigInteger.valueOf(maxSequence));
        if (startValue.compareTo(BIGINT_MAX_VALUE) > 0 || maxValue.compareTo(BIGINT_MAX_VALUE) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding auto increment range exceeds BIGINT max value");
        }
        return new ShardingAutoIncrementRange(prefix, startValue.longValueExact(), maxValue.longValueExact());
    }

    private void validateSequence(int sequenceWidth, long startSequence, long maxSequence) {
        if (sequenceWidth < 1 || sequenceWidth > 12) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sequence width must be between 1 and 12");
        }
        if (startSequence < 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "start sequence must be positive");
        }
        if (maxSequence < startSequence) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "max sequence must not be less than start sequence");
        }
        BigInteger maxAllowed = BigInteger.TEN.pow(sequenceWidth).subtract(BigInteger.ONE);
        if (BigInteger.valueOf(maxSequence).compareTo(maxAllowed) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "max sequence exceeds configured sequence width");
        }
    }
}

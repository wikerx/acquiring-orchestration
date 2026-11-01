package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * 分表自动自增值calculator协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
 */
@Component
public class ShardingAutoIncrementValueCalculator {


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

    /**
     * 校验季度编号序列宽度和起止值可在该宽度内安全表示。
     *
     * @param sequenceWidth 序列十进制位数
     * @param startSequence 起始序列值
     * @param maxSequence 最大序列值
     */
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

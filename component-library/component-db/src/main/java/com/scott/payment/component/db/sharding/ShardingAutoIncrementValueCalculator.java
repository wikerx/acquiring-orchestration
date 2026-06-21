package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * 分表物理表 AUTO_INCREMENT 起始值计算器。
 *
 * <p>ID 格式为 yyyyQQ + 固定宽度自增序号，例如 202602000000000001。
 * 不同逻辑表之间允许 ID 重复，因此该类只计算单张物理表的安全范围。</p>
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
    public ShardingAutoIncrementRange calculate(PaymentQuarterShardingProperties properties, ShardingQuarter quarter) {
        if (quarter == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding quarter is required");
        }
        PaymentQuarterShardingProperties.IdGenerator idGenerator = properties == null
                ? new PaymentQuarterShardingProperties.IdGenerator()
                : properties.getIdGenerator();
        if (idGenerator == null) {
            idGenerator = new PaymentQuarterShardingProperties.IdGenerator();
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

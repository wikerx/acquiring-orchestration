package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementValueCalculator
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingAutoIncrementValueCalculator Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingAutoIncrementValueCalculator {

    /**
     * BIGINT MAX VALUE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    /**
     * 校验 validate Sequence 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingAutoIncrementValueCalculator 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sequenceWidth sequence Width 输入值，含义由调用方法名称和所属业务对象限定
     * @param startSequence start Sequence 输入值，含义由调用方法名称和所属业务对象限定
     * @param maxSequence max Sequence 输入值，含义由调用方法名称和所属业务对象限定
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

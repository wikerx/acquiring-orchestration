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
 * @description : Sharding Auto Increment Value Calculator 协作组件，位于 公共组件库，封装 shardingautoincrementvaluecalculator 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class ShardingAutoIncrementValueCalculator {

    /**
     * BIGINT MAX VALUE，用于保存 Sharding Auto Increment Value Calculator 中与 bigintmaxvalue 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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

    /**
     * 校验sequence输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param sequenceWidth sequence Width 输入值，参与 sequencewidth 的查询、校验、转换、写入或日志摘要
     * @param startSequence start Sequence 输入值，参与 startsequence 的查询、校验、转换、写入或日志摘要
     * @param maxSequence max Sequence 输入值，参与 最大序列值 的查询、校验、转换、写入或日志摘要
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

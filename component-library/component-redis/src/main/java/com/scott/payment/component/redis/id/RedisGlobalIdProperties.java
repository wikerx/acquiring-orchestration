package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.global-id")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdProperties
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : RedisGlobalIdProperties 配置属性模型，用于绑定 application 配置项并提供默认值，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class RedisGlobalIdProperties {

    /**
     * 是否启用统一编号生成器自动装配。
     */
    private boolean enabled = true;

    /**
     * 生成模式：redis 或 local；未配置时默认 redis。
     */
    private String mode = "redis";

    /**
     * 编号时间格式化时区。
     */
    private String timezone = GlobalIdConstants.DEFAULT_ZONE_ID.getId();

    /**
     * 毫秒内序列长度。
     */
    private int sequenceLength = GlobalIdConstants.SEQUENCE_LENGTH;

    /**
     * 毫秒内最大序列。
     */
    private long maxSequence = GlobalIdConstants.DEFAULT_MAX_SEQUENCE;

    /**
     * 毫秒序列 Redis Key 前缀。
     */
    private String seqKeyPrefix = "biz:{global_id}:seq:";

    /**
     * Redis 防时间回拨 Key。
     */
    private String lastMillisKey = "biz:{global_id}:last_millis";

    /**
     * 毫秒序列 Key 过期秒数。
     */
    private long seqKeyExpireSeconds = 172800L;

    /**
     * 序列溢出后最大重试次数。
     */
    private int maxRetryTimes = 3;

    /**
     * 序列溢出后重试等待毫秒数。
     */
    private long retrySleepMillis = 1L;

    /**
     * 判断 is Enabled 条件是否成立，用于控制后续业务分支。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 完成 set Enabled 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param enabled enabled 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 完成 get Mode 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public String getMode() {
        return mode;
    }

    /**
     * 完成 set Mode 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param mode mode 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 完成 get Timezone 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * 完成 set Timezone 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param timezone 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * 完成 get Sequence Length 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public int getSequenceLength() {
        return sequenceLength;
    }

    /**
     * 完成 set Sequence Length 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sequenceLength sequence Length 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setSequenceLength(int sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    /**
     * 完成 get Max Sequence 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public long getMaxSequence() {
        return maxSequence;
    }

    /**
     * 完成 set Max Sequence 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param maxSequence max Sequence 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setMaxSequence(long maxSequence) {
        this.maxSequence = maxSequence;
    }

    /**
     * 完成 get Seq Key Prefix 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public String getSeqKeyPrefix() {
        return seqKeyPrefix;
    }

    /**
     * 完成 set Seq Key Prefix 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param seqKeyPrefix seq Key Prefix 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setSeqKeyPrefix(String seqKeyPrefix) {
        this.seqKeyPrefix = seqKeyPrefix;
    }

    /**
     * 完成 get Last Millis Key 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public String getLastMillisKey() {
        return lastMillisKey;
    }

    /**
     * 完成 set Last Millis Key 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param lastMillisKey last Millis Key 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setLastMillisKey(String lastMillisKey) {
        this.lastMillisKey = lastMillisKey;
    }

    /**
     * 完成 get Seq Key Expire Seconds 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public long getSeqKeyExpireSeconds() {
        return seqKeyExpireSeconds;
    }

    /**
     * 完成 set Seq Key Expire Seconds 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param seqKeyExpireSeconds seq Key Expire Seconds 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setSeqKeyExpireSeconds(long seqKeyExpireSeconds) {
        this.seqKeyExpireSeconds = seqKeyExpireSeconds;
    }

    /**
     * 完成 get Max Retry Times 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    /**
     * 完成 set Max Retry Times 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param maxRetryTimes 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    /**
     * 完成 get Retry Sleep Millis 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public long getRetrySleepMillis() {
        return retrySleepMillis;
    }

    /**
     * 完成 set Retry Sleep Millis 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 RedisGlobalIdProperties 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param retrySleepMillis retry Sleep Millis 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void setRetrySleepMillis(long retrySleepMillis) {
        this.retrySleepMillis = retrySleepMillis;
    }
}

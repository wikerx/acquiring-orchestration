package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdProperties
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : 绑定全局 ID 生成模式、Redis 状态 Key、序列容量和受控恢复下限
 * @status : create
 */
@ConfigurationProperties(prefix = "payment.global-id")
public class RedisGlobalIdProperties {

    /**
     * 是否启用统一编号生成器自动装配；生产和 UAT 关闭后必须由其他受审计实现提供 GlobalIdGenerator。
     */
    private boolean enabled = true;

    /**
     * 编号生成模式，允许 redis 或 local；local 只适用于单 JVM 开发和测试。
     */
    private String mode = "redis";

    /**
     * 22 位编号中时间片使用的 IANA 时区，默认 Asia/Shanghai；不包含敏感信息。
     */
    private String timezone = GlobalIdConstants.DEFAULT_ZONE_ID.getId();

    /**
     * 毫秒内序列位数，单位为位；当前协议固定为 6，不允许按环境变化。
     */
    private int sequenceLength = GlobalIdConstants.SEQUENCE_LENGTH;

    /**
     * 单毫秒最大序列值，单位为个；当前 6 位协议最大为 999999。
     */
    private long maxSequence = GlobalIdConstants.DEFAULT_MAX_SEQUENCE;

    /**
     * 保存 last_millis 和 sequence 的持久 Hash Key；必须匹配 acquiring:{environment}:global-id:state。
     */
    private String stateKey = "acquiring:local:global-id:state";

    /**
     * 单毫秒序列溢出后的最大重试次数，单位为次；失败后不允许降级到本地发号。
     */
    private int maxRetryTimes = 3;

    /**
     * 序列溢出后的等待时间，单位毫秒；只控制当前调用线程，不修改 Redis TTL。
     */
    private long retrySleepMillis = 1L;

    /**
     * 是否确认本次启动使用了受审核的全局 ID 状态恢复方案；正常启动必须保持 false。
     */
    private boolean restoreAcknowledged;

    /**
     * 恢复后允许发号的最小 epochMillis；必须高于所有历史已签发编号的时间片，正常启动固定为 0。
     */
    private long restoreFloorEpochMillis;

    /**
     * 判断是否启用全局 ID 自动装配。
     *
     * @return 启用时为 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用全局 ID 自动装配。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取编号生成模式。
     *
     * @return redis 或 local
     */
    public String getMode() {
        return mode;
    }

    /**
     * 设置编号生成模式。
     *
     * @param mode redis 或 local
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 获取编号时间片时区。
     *
     * @return IANA 时区标识
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * 设置编号时间片时区。
     *
     * @param timezone IANA 时区标识
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * 获取毫秒内序列位数。
     *
     * @return 序列位数
     */
    public int getSequenceLength() {
        return sequenceLength;
    }

    /**
     * 设置毫秒内序列位数。
     *
     * @param sequenceLength 序列位数
     */
    public void setSequenceLength(int sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    /**
     * 获取单毫秒最大序列值。
     *
     * @return 最大序列值
     */
    public long getMaxSequence() {
        return maxSequence;
    }

    /**
     * 设置单毫秒最大序列值。
     *
     * @param maxSequence 最大序列值
     */
    public void setMaxSequence(long maxSequence) {
        this.maxSequence = maxSequence;
    }

    /**
     * 获取全局 ID 持久状态 Key。
     *
     * @return acquiring:{environment}:global-id:state 格式的 Key
     */
    public String getStateKey() {
        return stateKey;
    }

    /**
     * 设置全局 ID 持久状态 Key。
     *
     * @param stateKey acquiring:{environment}:global-id:state 格式的 Key
     */
    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    /**
     * 获取序列溢出后的最大重试次数。
     *
     * @return 最大重试次数，单位为次
     */
    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    /**
     * 设置序列溢出后的最大重试次数。
     *
     * @param maxRetryTimes 最大重试次数，单位为次
     */
    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    /**
     * 获取序列溢出后的等待时间。
     *
     * @return 等待时间，单位毫秒
     */
    public long getRetrySleepMillis() {
        return retrySleepMillis;
    }

    /**
     * 设置序列溢出后的等待时间。
     *
     * @param retrySleepMillis 等待时间，单位毫秒
     */
    public void setRetrySleepMillis(long retrySleepMillis) {
        this.retrySleepMillis = retrySleepMillis;
    }

    /**
     * 判断是否已确认使用受审核的状态恢复方案。
     *
     * @return 已确认恢复时为 true
     */
    public boolean isRestoreAcknowledged() {
        return restoreAcknowledged;
    }

    /**
     * 设置状态恢复确认标识。
     *
     * @param restoreAcknowledged 是否已完成备份校验、历史最大编号核对和双人审批
     */
    public void setRestoreAcknowledged(boolean restoreAcknowledged) {
        this.restoreAcknowledged = restoreAcknowledged;
    }

    /**
     * 获取状态恢复后的最小发号毫秒。
     *
     * @return epochMillis；正常启动为 0
     */
    public long getRestoreFloorEpochMillis() {
        return restoreFloorEpochMillis;
    }

    /**
     * 设置状态恢复后的最小发号毫秒。
     *
     * @param restoreFloorEpochMillis 必须高于所有历史已签发编号时间片的 epochMillis
     */
    public void setRestoreFloorEpochMillis(long restoreFloorEpochMillis) {
        this.restoreFloorEpochMillis = restoreFloorEpochMillis;
    }
}

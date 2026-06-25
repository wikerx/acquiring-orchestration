package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 全局唯一标识生成配置。
 */
@ConfigurationProperties(prefix = "payment.global-id")
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getSequenceLength() {
        return sequenceLength;
    }

    public void setSequenceLength(int sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    public long getMaxSequence() {
        return maxSequence;
    }

    public void setMaxSequence(long maxSequence) {
        this.maxSequence = maxSequence;
    }

    public String getSeqKeyPrefix() {
        return seqKeyPrefix;
    }

    public void setSeqKeyPrefix(String seqKeyPrefix) {
        this.seqKeyPrefix = seqKeyPrefix;
    }

    public String getLastMillisKey() {
        return lastMillisKey;
    }

    public void setLastMillisKey(String lastMillisKey) {
        this.lastMillisKey = lastMillisKey;
    }

    public long getSeqKeyExpireSeconds() {
        return seqKeyExpireSeconds;
    }

    public void setSeqKeyExpireSeconds(long seqKeyExpireSeconds) {
        this.seqKeyExpireSeconds = seqKeyExpireSeconds;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    public long getRetrySleepMillis() {
        return retrySleepMillis;
    }

    public void setRetrySleepMillis(long retrySleepMillis) {
        this.retrySleepMillis = retrySleepMillis;
    }
}

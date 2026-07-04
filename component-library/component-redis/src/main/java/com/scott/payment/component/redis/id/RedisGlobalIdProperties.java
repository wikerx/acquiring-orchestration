package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Global Id 配置属性，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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

    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @return 处理后的业务结果或页面展示数据。
     */

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param enabled 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public String getMode() {
        return mode;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param mode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public String getTimezone() {
        return timezone;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param timezone 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public int getSequenceLength() {
        return sequenceLength;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param sequenceLength 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setSequenceLength(int sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public long getMaxSequence() {
        return maxSequence;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param maxSequence 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setMaxSequence(long maxSequence) {
        this.maxSequence = maxSequence;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public String getSeqKeyPrefix() {
        return seqKeyPrefix;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param seqKeyPrefix 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setSeqKeyPrefix(String seqKeyPrefix) {
        this.seqKeyPrefix = seqKeyPrefix;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public String getLastMillisKey() {
        return lastMillisKey;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param lastMillisKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setLastMillisKey(String lastMillisKey) {
        this.lastMillisKey = lastMillisKey;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public long getSeqKeyExpireSeconds() {
        return seqKeyExpireSeconds;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param seqKeyExpireSeconds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setSeqKeyExpireSeconds(long seqKeyExpireSeconds) {
        this.seqKeyExpireSeconds = seqKeyExpireSeconds;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param maxRetryTimes 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */

    public long getRetrySleepMillis() {
        return retrySleepMillis;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param retrySleepMillis 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void setRetrySleepMillis(long retrySleepMillis) {
        this.retrySleepMillis = retrySleepMillis;
    }
}

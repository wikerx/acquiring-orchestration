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
 * @description : Redis Global ID Properties 配置属性模型，位于 公共组件库，绑定 application 配置项并提供运行时默认值。
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
     * 判断 is enabled 条件是否成立，用于控制 Redis Global ID Properties 的后续分支。
     * <p>
     * 前置条件：调用方已准备 公共组件库 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @return 条件满足时返回 true，否则返回 false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 写入setenabled，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param enabled enabled 输入值，参与 enabled 的查询、校验、转换、写入或日志摘要
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 查询运行模式，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public String getMode() {
        return mode;
    }

    /**
     * 写入setmode，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param mode mode 输入值，参与 运行模式 的查询、校验、转换、写入或日志摘要
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 查询时区配置，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * 写入settimezone，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param timezone 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * 查询序列长度，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public int getSequenceLength() {
        return sequenceLength;
    }

    /**
     * 写入setsequencelength，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sequenceLength sequence Length 输入值，参与 序列长度 的查询、校验、转换、写入或日志摘要
     */
    public void setSequenceLength(int sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    /**
     * 查询最大序列值，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public long getMaxSequence() {
        return maxSequence;
    }

    /**
     * 写入setmaxsequence，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param maxSequence max Sequence 输入值，参与 最大序列值 的查询、校验、转换、写入或日志摘要
     */
    public void setMaxSequence(long maxSequence) {
        this.maxSequence = maxSequence;
    }

    /**
     * 查询序列 Redis Key 前缀，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public String getSeqKeyPrefix() {
        return seqKeyPrefix;
    }

    /**
     * 写入setseq密钥prefix，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param seqKeyPrefix 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    public void setSeqKeyPrefix(String seqKeyPrefix) {
        this.seqKeyPrefix = seqKeyPrefix;
    }

    /**
     * 查询上一毫秒 Redis Key，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public String getLastMillisKey() {
        return lastMillisKey;
    }

    /**
     * 写入setlast毫秒数密钥，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param lastMillisKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    public void setLastMillisKey(String lastMillisKey) {
        this.lastMillisKey = lastMillisKey;
    }

    /**
     * 查询序列 Key 过期秒数，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public long getSeqKeyExpireSeconds() {
        return seqKeyExpireSeconds;
    }

    /**
     * 写入setseq密钥失效seconds，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param seqKeyExpireSeconds 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    public void setSeqKeyExpireSeconds(long seqKeyExpireSeconds) {
        this.seqKeyExpireSeconds = seqKeyExpireSeconds;
    }

    /**
     * 查询最大重试次数，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    /**
     * 写入setmaxretrytimes，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param maxRetryTimes 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    /**
     * 查询重试休眠毫秒数，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public long getRetrySleepMillis() {
        return retrySleepMillis;
    }

    /**
     * 写入setretrysleep毫秒数，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param retrySleepMillis retry Sleep Millis 输入值，参与 重试休眠毫秒数 的查询、校验、转换、写入或日志摘要
     */
    public void setRetrySleepMillis(long retrySleepMillis) {
        this.retrySleepMillis = retrySleepMillis;
    }
}

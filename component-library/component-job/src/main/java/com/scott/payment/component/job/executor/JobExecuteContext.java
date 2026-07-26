package com.scott.payment.component.job.executor;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import com.scott.payment.component.job.enums.JobTriggerTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteContext
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务执行上下文对象
 * @status : create
 */
@Data
public class JobExecuteContext {

    /**
     * 任务主键。
     */
    private Long jobId;

    /**
     * 任务编码。
     */
    private String jobCode;

    /**
     * 任务名称。
     */
    private String jobName;

    /**
     * 处理器编码。
     */
    private String handlerCode;

    /**
     * 执行批次号。
     */
    private String runId;

    /**
     * 触发类型。
     */
    private JobTriggerTypeEnum triggerType;

    /**
     * 调度模式。
     */
    private JobSchedulerModeEnum schedulerMode;

    /**
     * 执行模式。
     */
    private JobExecuteModeEnum executeMode;

    /**
     * JSON 参数文本。
     */
    private String paramsJson;

    /**
     * 计划触发时间。
     */
    private LocalDateTime scheduledTime;

    /**
     * 实际触发时间。
     */
    private LocalDateTime actualTriggerTime;

    /**
     * 当前重试序号。
     */
    private Integer retryIndex;

    /**
     * 最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 当前执行分片序号。
     * <p>
     * 单位：片；格式：从 0 开始的整数；允许为空，调度中心默认补齐为 0；非敏感字段。
     * 与 shardTotal 配合标识同一次任务执行中的分片位置。
     * </p>
     */
    private Integer shardIndex;

    /**
     * 当前执行分片总数。
     * <p>
     * 单位：片；格式：大于 0 的整数；允许为空，调度中心默认补齐为 1；非敏感字段。
     * 与 shardIndex 配合用于日志定位同一次任务执行的分片范围。
     * </p>
     */
    private Integer shardTotal;

    /**
     * 手动触发的操作人 ID。
     */
    private String operatorId;

    /**
     * 手动触发的操作人名称。
     */
    private String operatorName;

    /**
     * 执行节点标识。
     */
    private String executorNode;

    /**
     * 链路追踪标识。
     */
    private String traceId;

    /**
     * 将 JSON 参数解析为指定对象。
     *
     * @param clazz 参数对象类型
     * @param <T>   参数泛型
     * @return 参数对象
     */
    public <T> T parseParams(Class<T> clazz) {
        return JsonUtils.parseObject(paramsJson, clazz);
    }
}

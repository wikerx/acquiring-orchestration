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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteContext
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Execute Context，位于 component-library/component-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param clazz 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public <T> T parseParams(Class<T> clazz) {
        return JsonUtils.parseObject(paramsJson, clazz);
    }
}

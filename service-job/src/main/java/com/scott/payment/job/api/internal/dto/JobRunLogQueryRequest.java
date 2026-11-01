package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务运行日志Query请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRunLogQueryRequest extends PageRequest {


    private Long jobId;


    private String jobCode;


    private String runStatus;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
    private String triggerType;

    /**
     * 请求中的执行器节点，用于限定本次操作的输入和校验范围。
     */
    private String executorNode;
}

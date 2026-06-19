package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogQueryRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志Query请求对象
 * @status : create
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class JobRunLogQueryRequest extends PageRequest {

    private Long jobId;

    private String jobCode;

    private String runStatus;

    private String triggerType;

    private String executorNode;
}

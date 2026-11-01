package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 调度任务分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobTaskQueryRequest extends PageRequest {


    private String jobCode;


    private String jobName;

    /**
     * 请求中的任务分组，用于限定本次操作的输入和校验范围。
     */
    private String jobGroup;


    private String handlerCode;


    private String status;
}

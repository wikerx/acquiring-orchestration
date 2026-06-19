package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskQueryRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务任务Query请求对象
 * @status : create
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class JobTaskQueryRequest extends PageRequest {

    private String jobCode;

    private String jobName;

    private String jobGroup;

    private String handlerCode;

    private String status;
}

package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerOptionResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务处理器Option响应对象
 * @status : create
 */

@Data
public class JobHandlerOptionResponse {

    private String handlerCode;

    private String handlerName;

    private String jobGroup;

    private JobExecuteModeEnum executeMode;

    private String description;

    private Boolean allowManualTrigger;

    private Boolean allowConcurrent;
}

package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import lombok.Data;

/**
 * 任务处理器Option响应对象
 */
@Data
public class JobHandlerOptionResponse {


    private String handlerCode;


    private String handlerName;

    /**
     * 响应中的任务分组，用于管理端或商户端展示当前处理结果。
     */
    private String jobGroup;

    /**
     * 响应中的执行模式，用于管理端或商户端展示当前处理结果。
     */
    private JobExecuteModeEnum executeMode;


    private String description;


    private Boolean allowManualTrigger;


    private Boolean allowConcurrent;
}

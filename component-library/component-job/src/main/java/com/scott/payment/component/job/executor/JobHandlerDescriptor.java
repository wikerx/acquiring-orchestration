package com.scott.payment.component.job.executor;

import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerDescriptor
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务处理器注册描述对象
 * @status : create
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobHandlerDescriptor {

    /**
     * 处理器唯一编码。
     */
    private String handlerCode;

    /**
     * 处理器展示名称。
     */
    private String handlerName;

    /**
     * 任务所属业务分组。
     */
    private String jobGroup;

    /**
     * 执行模式。
     */
    private JobExecuteModeEnum executeMode;

    /**
     * 处理器说明。
     */
    private String description;

    /**
     * 是否允许手动执行。
     */
    private Boolean allowManualTrigger;

    /**
     * 是否允许同一任务并发执行。
     */
    private Boolean allowConcurrent;

    /**
     * 构建同步任务处理器描述。
     *
     * @param handlerCode 处理器编码
     * @param handlerName 处理器名称
     * @param jobGroup    业务分组
     * @param description 说明
     * @return 处理器描述
     */
    public static JobHandlerDescriptor sync(String handlerCode, String handlerName, String jobGroup, String description) {
        return JobHandlerDescriptor.builder()
                .handlerCode(handlerCode)
                .handlerName(handlerName)
                .jobGroup(jobGroup)
                .executeMode(JobExecuteModeEnum.SYNC)
                .description(description)
                .allowManualTrigger(Boolean.TRUE)
                .allowConcurrent(Boolean.FALSE)
                .build();
    }

    /**
     * 构建异步任务处理器描述。
     *
     * @param handlerCode 处理器编码
     * @param handlerName 处理器名称
     * @param jobGroup    业务分组
     * @param description 说明
     * @return 处理器描述
     */
    public static JobHandlerDescriptor async(String handlerCode, String handlerName, String jobGroup, String description) {
        return JobHandlerDescriptor.builder()
                .handlerCode(handlerCode)
                .handlerName(handlerName)
                .jobGroup(jobGroup)
                .executeMode(JobExecuteModeEnum.ASYNC)
                .description(description)
                .allowManualTrigger(Boolean.TRUE)
                .allowConcurrent(Boolean.FALSE)
                .build();
    }
}

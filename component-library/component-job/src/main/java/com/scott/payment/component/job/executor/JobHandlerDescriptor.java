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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerDescriptor
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Handler Descriptor，位于 component-library/component-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param handlerCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param handlerName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param jobGroup 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param description 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param handlerCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param handlerName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param jobGroup 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param description 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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

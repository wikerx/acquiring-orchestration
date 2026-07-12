package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerOptionResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务处理器选项响应 DTO
 * @status : create
 *
 * <p>用于任务配置页展示可选处理器清单及其执行模式、并发能力和手动触发能力。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerOptionResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Job Handler Option 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobHandlerOptionResponse {

    /**
     * 处理器编码。
     */
    private String handlerCode;

    /**
     * 处理器名称。
     */
    private String handlerName;

    /**
     * 任务分组。
     */
    private String jobGroup;

    /**
     * 执行模式枚举。
     */
    private JobExecuteModeEnum executeMode;

    /**
     * 处理器说明，可为空。
     */
    private String description;

    /**
     * 是否允许手动触发。
     */
    private Boolean allowManualTrigger;

    /**
     * 是否允许并发执行。
     */
    private Boolean allowConcurrent;
}

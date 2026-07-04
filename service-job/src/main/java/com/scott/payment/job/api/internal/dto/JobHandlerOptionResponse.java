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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerOptionResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Handler Option 响应对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobHandlerOptionResponse {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String handlerCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String handlerName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String jobGroup;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private JobExecuteModeEnum executeMode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String description;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean allowManualTrigger;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean allowConcurrent;
}

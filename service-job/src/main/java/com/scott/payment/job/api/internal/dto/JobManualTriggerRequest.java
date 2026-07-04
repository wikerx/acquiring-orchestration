package com.scott.payment.job.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobManualTriggerRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务Manual触发请求对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobManualTriggerRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Manual Trigger 请求对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobManualTriggerRequest {

    /**
     * 任务参数 JSON，允许为空，为空时使用任务默认参数。
     */
    private String paramsJson;

    /**
     * 操作人 ID。
     */
    @NotBlank(message = "operatorId must not be blank")
    private String operatorId;

    /**
     * 操作人名称。
     */
    @NotBlank(message = "operatorName must not be blank")
    private String operatorName;
}

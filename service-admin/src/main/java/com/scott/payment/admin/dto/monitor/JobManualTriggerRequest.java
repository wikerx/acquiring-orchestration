package com.scott.payment.admin.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobManualTriggerRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务手动触发请求 DTO
 * @status : create
 *
 * <p>用于后台人工触发一次任务执行，承载本次运行参数和操作人审计信息。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobManualTriggerRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Job Manual Trigger 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobManualTriggerRequest {

    /**
     * 任务参数 JSON。
     */
    private String paramsJson;

    /**
     * 当前操作人 ID。
     */
    @NotBlank(message = "operatorId must not be blank")
    private String operatorId;

    /**
     * 当前操作人名称。
     */
    @NotBlank(message = "operatorName must not be blank")
    private String operatorName;
}

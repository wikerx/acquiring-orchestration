package com.scott.payment.job.api.internal.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogQueryRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志Query请求对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Run Log Query 请求对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRunLogQueryRequest extends PageRequest {

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long jobId;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String jobCode;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String runStatus;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String triggerType;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String executorNode;
}

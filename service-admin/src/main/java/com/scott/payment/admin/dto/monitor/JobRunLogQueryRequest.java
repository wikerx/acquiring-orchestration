package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogQueryRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务运行日志查询请求 DTO
 * @status : create
 *
 * <p>用于任务运行日志分页检索，支持按任务、状态、触发方式和执行节点过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRunLogQueryRequest extends PageRequest {

    /**
     * 任务主键过滤条件，可为空。
     */
    private Long jobId;

    /**
     * 任务编码过滤条件，可为空。
     */
    private String jobCode;

    /**
     * 运行状态过滤条件，可为空。
     */
    private String runStatus;

    /**
     * 触发类型过滤条件，可为空。
     */
    private String triggerType;

    /**
     * 执行节点过滤条件，可为空。
     */
    private String executorNode;
}

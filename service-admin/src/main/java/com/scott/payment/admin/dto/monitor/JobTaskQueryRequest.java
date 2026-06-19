package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskQueryRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务定义查询请求 DTO
 * @status : create
 *
 * <p>用于任务定义分页检索，支持按任务编码、名称、分组、处理器和状态过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobTaskQueryRequest extends PageRequest {

    private String jobCode;

    private String jobName;

    private String jobGroup;

    private String handlerCode;

    private String status;
}

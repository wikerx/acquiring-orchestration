package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台任务定义查询请求 DTO
 *
 * <p>用于任务定义分页检索，支持按任务编码、名称、分组、处理器和状态过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobTaskQueryRequest extends PageRequest {

    private String jobCode;

    private String jobName;

    /**
     * 请求中的任务分组，用于限定本次操作的输入和校验范围。
     */
    private String jobGroup;

    private String handlerCode;

    private String status;
}

package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogQueryRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统后台操作日志查询请求
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOperLogQueryRequest extends PageRequest {

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 请求ID。
     */
    private String requestId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 操作人ID。
     */
    private String operatorId;

    /**
     * 模块名称。
     */
    private String moduleName;

    /**
     * 业务类型。
     */
    private Integer businessType;

    /**
     * 操作状态。
     */
    private Integer status;

    /**
     * 操作开始时间。
     */
    private LocalDateTime operatedStartAt;

    /**
     * 操作结束时间。
     */
    private LocalDateTime operatedEndAt;
}

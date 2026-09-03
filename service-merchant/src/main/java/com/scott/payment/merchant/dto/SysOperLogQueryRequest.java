package com.scott.payment.merchant.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogQueryRequest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : sysoper日志查询条件模型，位于 商户后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOperLogQueryRequest extends PageRequest {

    /**
     * 后端补齐的商户号。
     */
    private String merchantId;

    /**
     * 操作模块名称。
     */
    private String moduleName;

    /**
     * 操作状态：0失败，1成功。
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

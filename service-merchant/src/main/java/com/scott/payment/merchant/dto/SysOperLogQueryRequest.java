package com.scott.payment.merchant.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Sys Oper Log Query 请求对象，位于 service-merchant 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
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

package com.scott.payment.merchant.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogQueryRequest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Sys Oper Log Query Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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

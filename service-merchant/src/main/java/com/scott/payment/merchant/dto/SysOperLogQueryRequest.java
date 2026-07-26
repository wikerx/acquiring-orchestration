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
 * @description : SysOperLogQueryRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
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

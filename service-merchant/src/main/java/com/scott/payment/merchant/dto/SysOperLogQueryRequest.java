package com.scott.payment.merchant.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商户系统操作日志查询请求，商户号由登录上下文补齐，不能信任前端传入。
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

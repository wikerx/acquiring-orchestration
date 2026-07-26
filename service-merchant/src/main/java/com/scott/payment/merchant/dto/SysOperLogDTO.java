package com.scott.payment.merchant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDTO
 * @date : 2026-06-06 00:09
 * @email : scott_x@163.com
 * @description : SysOperLogDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class SysOperLogDTO {

    /**
     * 日志主键。
     */
    private Long id;

    /**
     * 请求链路追踪 ID。
     */
    private String traceId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 操作模块名称。
     */
    private String moduleName;

    /**
     * 操作名称。
     */
    private String operationName;

    /**
     * 业务类型。
     */
    private Integer businessType;

    /**
     * 请求方式。
     */
    private String requestMethod;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 操作 URL。
     */
    private String operUrl;

    /**
     * 操作 IP。
     */
    private String operIp;

    /**
     * 执行耗时，单位毫秒。
     */
    private Long costTime;

    /**
     * 操作状态：0失败，1成功。
     */
    private Integer status;

    /**
     * 错误码。
     */
    private String errorCode;

    /**
     * 脱敏后的错误信息。
     */
    private String errorMsg;

    /**
     * 操作时间。
     */
    private LocalDateTime operatedAt;
}

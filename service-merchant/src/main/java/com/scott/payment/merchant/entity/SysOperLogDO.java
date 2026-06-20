package com.scott.payment.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDO
 * @date : 2026-06-20 10:29
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志数据库实体，只保存商户管理侧审计日志
 * @status : create
 */
@Data
@TableName("sys_oper_log")
public class SysOperLogDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 请求ID。
     */
    private String requestId;

    /**
     * MQ 消息唯一标识。
     */
    private String messageId;

    /**
     * 消费幂等键。
     */
    private String idempotentKey;

    /**
     * 系统编码，区分 ADMIN / MERCHANT。
     */
    private String systemCode;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 模块名称。
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
     * 后端方法名称。
     */
    private String methodName;

    /**
     * 请求方式。
     */
    private String requestMethod;

    /**
     * 操作人类别。
     */
    private Integer operatorType;

    /**
     * 操作人ID。
     */
    private String operatorId;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 请求URL。
     */
    private String operUrl;

    /**
     * 操作IP。
     */
    private String operIp;

    /**
     * 操作地点。
     */
    private String operLocation;

    /**
     * 店铺号。
     */
    private String storeId;

    /**
     * 浏览器 User-Agent。
     */
    private String userAgent;

    /**
     * 脱敏后的请求参数。
     */
    private String requestParam;

    /**
     * 脱敏后的响应结果。
     */
    private String responseResult;

    /**
     * 执行时长，单位毫秒。
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
     * 错误信息。
     */
    private String errorMsg;

    /**
     * 操作时间。
     */
    private LocalDateTime operatedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}

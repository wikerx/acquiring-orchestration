package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统后台操作日志数据库实体，只记录管理后台审计信息，不承载交易明文
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
     * 商户号，后台操作涉及商户时记录。
     */
    private String merchantId;

    /**
     * 模块名称，如商户管理、费率管理、系统配置。
     */
    private String moduleName;

    /**
     * 操作名称。
     */
    private String operationName;

    /**
     * 业务类型：1新增，2修改，3删除，4查询，5导出，6审核，7冻结，8解冻。
     */
    private Integer businessType;

    /**
     * 后端方法名称。
     */
    private String methodName;

    /**
     * 请求方式：GET、POST、PUT、DELETE。
     */
    private String requestMethod;

    /**
     * 操作人类别：1后台用户，2商户用户，3系统任务。
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
     * 操作IP，支持 IPv4 和 IPv6。
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
     * 脱敏后的请求参数，禁止记录密钥、卡号、CVV、JWT 明文。
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
     * 错误信息，禁止写入堆栈明文。
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

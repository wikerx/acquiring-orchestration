package com.scott.payment.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataOperationLogDO
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : service-data 写入 sys_oper_log 的审计实体，仅保存生产端已脱敏和截断的管理操作信息
 * @status : create
 */
@Data
@TableName("sys_oper_log")
public class DataOperationLogDO {

    /** 数据库自增主键，不允许由 MQ 消息指定。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 跨服务链路追踪号，允许为空。 */
    private String traceId;

    /** 原始 HTTP 请求号，允许为空。 */
    private String requestId;

    /** RocketMQ 消息唯一标识，允许为空但建议生产端提供。 */
    private String messageId;

    /** 消费幂等键，不允许为空，由数据库唯一索引提供最终幂等。 */
    private String idempotentKey;

    /** 来源系统编码，例如 ADMIN 或 MERCHANT。 */
    private String systemCode;

    /** 相关商户号，后台公共操作允许为空。 */
    private String merchantId;

    /** 操作所属业务模块。 */
    private String moduleName;

    /** 用户可识别的操作名称。 */
    private String operationName;

    /** 业务类型编码：新增、修改、删除、查询、导出等。 */
    private Integer businessType;

    /** 被调用的后端方法名称。 */
    private String methodName;

    /** HTTP 请求方法。 */
    private String requestMethod;

    /** 操作人类型：1 后台用户，2 商户用户，3 系统任务。 */
    private Integer operatorType;

    /** 操作人业务标识。 */
    private String operatorId;

    /** 操作人展示名称。 */
    private String operatorName;

    /** 请求 URI，不包含域名和敏感查询参数。 */
    private String operUrl;

    /** 客户端 IPv4 或 IPv6 地址。 */
    private String operIp;

    /** IP 解析位置；生产消息未提供时允许为空。 */
    private String operLocation;

    /** 商户店铺号，非店铺操作允许为空。 */
    private String storeId;

    /** 浏览器 User-Agent，必须由生产端限制长度。 */
    private String userAgent;

    /** 已脱敏请求摘要，禁止包含密钥、卡号、CVV 或 Token。 */
    private String requestParam;

    /** 已脱敏响应摘要，禁止包含密钥、卡号、CVV 或 Token。 */
    private String responseResult;

    /** 原请求处理耗时，单位毫秒。 */
    private Long costTime;

    /** 操作状态：0 失败，1 成功。 */
    private Integer status;

    /** 对外或内部错误码，成功时允许为空。 */
    private String errorCode;

    /** 已截断的错误摘要，禁止保存完整异常栈。 */
    private String errorMsg;

    /** 实际业务操作时间，精度为毫秒。 */
    private LocalDateTime operatedAt;

    /** 消费成功写库时间，精度为毫秒。 */
    private LocalDateTime createdAt;
}

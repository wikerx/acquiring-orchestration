package com.scott.payment.component.db.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventDO
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件实体，位于 component-db 共享数据层，只保存异常请求的脱敏排查元数据，不保存完整请求体、JWT 或密钥材料。
 * @status : create
 */
@Data
@TableName("security_intercept_event")
public class SecurityInterceptEventDO {

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 安全事件号，用于后台排查和日志关联。
     */
    private String eventNo;

    /**
     * 事件发生时间，保留毫秒精度。
     */
    private LocalDateTime eventTime;

    /**
     * 来源层级，例如 OPENAPI、CHANNEL。
     */
    private String sourceLayer;

    /**
     * 事件类型，例如 OPENAPI_IP_DENIED、CHANNEL_SIGNATURE_INVALID。
     */
    private String eventType;

    /**
     * 风险等级：LOW、MEDIUM、HIGH、CRITICAL。
     */
    private String riskLevel;

    /**
     * 处置动作：BLOCK、REVIEW、LOG。
     */
    private String action;

    /**
     * 商户号；JWT 未解析或非商户事件时为空。
     */
    private String merchantId;

    /**
     * 客户端 IP，优先取网关写入的可信客户端 IP。
     */
    private String clientIp;

    /**
     * 请求方法。
     */
    private String requestMethod;

    /**
     * 请求路径，不包含查询串中的敏感参数。
     */
    private String requestPath;

    /**
     * traceId，用于链路日志检索。
     */
    private String traceId;

    /**
     * requestId，用于一次请求内关联。
     */
    private String requestId;

    /**
     * 脱敏或截断后的 User-Agent。
     */
    private String userAgent;

    /**
     * 拦截原因码。
     */
    private String reasonCode;

    /**
     * 脱敏后的拦截原因说明。
     */
    private String reasonMessage;

    /**
     * 记录事件的服务名。
     */
    private String serviceName;

    /**
     * 命中的安全规则编码。
     */
    private String hitRuleCode;

    /**
     * 脱敏后的请求头摘要，禁止保存 Authorization、Cookie、密钥或完整密文。
     */
    private String headerSummary;

    /**
     * 处理状态：0 未处理，1 已处理，2 忽略。
     */
    private Integer processStatus;

    /**
     * 处理备注。
     */
    private String processRemark;

    /**
     * 处理人。
     */
    private String processedBy;

    /**
     * 处理时间，保留毫秒精度。
     */
    private LocalDateTime processedTime;

    /**
     * 创建时间，保留毫秒精度。
     */
    private LocalDateTime gmtCreate;

    /**
     * 更新时间，保留毫秒精度。
     */
    private LocalDateTime gmtModified;
}

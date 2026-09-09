package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackLogDO
 * @date : 2026-07-14 22:18
 * @email : scott_x@163.com
 * @description : 渠道回调原始日志实体，位于 service-payment 持久化层，保存渠道回调脱敏原文、验签结果、IP 校验结果和平台响应摘要。
 * @status : create
 */
@Data
@TableName("transaction_channel_callback_log")
public class TransactionChannelCallbackLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code TransactionChannelCallbackLogDO} 数据库主键，用于唯一标识当前记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 回调日志ID，用于定位 {@code TransactionChannelCallbackLogDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private String callbackLogId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
     * </p>
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 transactionId、transactionType 共同定位一次交易动作。
     * </p>
     */
    private String operationId;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String channelCode;

    /**
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private String callbackType;

    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String channelOrderNo;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private String channelTransactionId;

    /**
     * 持久化的{@code requestUri}，用于还原当前记录的业务事实。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度和协议由调用方校验；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String requestUri;

    /**
     * HTTP方式，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String httpMethod;

    /**
     * 持久化的{@code sourceIp}，用于还原当前记录的业务事实。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String sourceIp;

    /**
     * {@code requestHeaderJsonMasked}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String requestHeaderJsonMasked;

    /**
     * {@code requestBodyJsonMasked}，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String requestBodyJsonMasked;

    /**
     * {@code signatureValid}，用于定位 {@code TransactionChannelCallbackLogDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer signatureValid;

    /**
     * 持久化的{@code ipAllowed}，用于还原当前记录的业务事实。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer ipAllowed;

    /**
     * 平台响应编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String platformResponseCode;

    /**
     * 平台响应报文体，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String platformResponseBody;

    /**
     * 持久化的{@code callbackReceivedTime}，用于还原当前记录的业务事实。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private LocalDateTime callbackReceivedTime;

    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String transactionTimeZone;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
     * </p>
     */
    private LocalDateTime createTime;
}

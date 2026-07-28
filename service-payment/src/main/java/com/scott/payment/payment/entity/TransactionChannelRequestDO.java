package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelRequestDO
 * @date : 2026-07-14 19:30
 * @email : scott_x@163.com
 * @description : 交易渠道请求实体，位于 service-payment 持久化层，保存一次渠道请求的核心字段、同步响应摘要和平台成功判断。
 * @status : create
 */
@Data
@TableName("transaction_channel_request")
public class TransactionChannelRequestDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    /**
     * Transaction Channel Request DO 数据库主键，用于唯一标识当前记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long id;

    /**
     * 平台渠道请求 ID，一次渠道调用唯一，对应交互日志 request_id。
     */
    private String requestId;

    /**
     * 平台当前交易唯一标识，授权、请款、退款、撤销每个动作各自唯一。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识，用于把同一原始交易下的授权、请款、退款等动作串联起来。
     */
    private String operationId;

    /**
     * 渠道信息表 ID，可用于后台关联渠道基础配置。
     */
    private Long channelId;

    /**
     * 渠道编码，例如 MPGS。
     */
    private String channelCode;

    /**
     * 渠道 MID 配置 ID，表示本次交易实际使用的渠道商户号配置。
     */
    private Long channelMidConfigId;

    /**
     * 平台交易类型，对齐系统字典 transaction_type。
     */
    private String transactionType;

    /**
     * 渠道请求场景，例如 AUTHORIZE、CAPTURE、REFUND、VOID 或 RETRIEVE。
     */
    private String requestScene;

    /**
     * 是否为渠道结果勾兑/查询确认请求，0 否，1 是。
     */
    private Integer channelMatchFlag;

    /**
     * 渠道请求状态，例如 SUCCESS、FAILED、TIMEOUT。
     */
    private String requestStatus;

    /**
     * 渠道 HTTP 方法。
     */
    private String httpMethod;

    /**
     * 脱敏后的渠道请求 URL，不允许包含用户名、密码、API Key 等敏感信息。
     */
    private String requestUrlMasked;

    /**
     * 上送渠道的交易币种，EDC 场景下可能不同于商户标签币种。
     */
    private String requestCurrency;

    /**
     * 上送渠道的交易金额，主币种单位，精度为 DECIMAL(20,6)。
     */
    private BigDecimal requestAmount;

    /**
     * 渠道订单号；MPGS 场景使用原始授权或一步支付的平台 transactionId。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；MPGS 场景使用平台生成的 channel_transaction_id，部分渠道可为空。
     */
    private String channelTransactionId;

    /**
     * 渠道网关外层结果，例如 MPGS result；该字段不等同于资金成功。
     */
    private String gatewayResult;

    /**
     * 渠道网关响应码，例如 MPGS response.gatewayCode。
     */
    private String gatewayCode;

    /**
     * 收单响应码，例如 MPGS response.acquirerCode；授权/支付成功需要重点判断 00。
     */
    private String acquirerCode;

    /**
     * 收单响应描述，例如 Approved 或渠道拒绝原因。
     */
    private String acquirerMessage;

    /**
     * 渠道原始交易状态，例如 MPGS order.status 或 result。
     */
    private String channelStatus;

    /**
     * 平台按渠道规则判断是否成功，1 成功，0 失败或未成功。
     */
    private Integer platformSuccess;

    /**
     * 平台统一交易状态结果，对齐 transaction_status 字典。
     */
    private String platformResultCode;

    /**
     * 后台可见失败原因，允许保存渠道真实失败摘要。
     */
    private String platformFailReason;

    /**
     * 渠道请求发起时间，DATETIME(3)。
     */
    private LocalDateTime requestStartTime;

    /**
     * 渠道响应时间，未收到响应时可为空。
     */
    private LocalDateTime responseTime;

    /**
     * 渠道请求耗时，单位毫秒。
     */
    private Integer durationMillis;

    /**
     * 交易业务时间，所有 transaction_* 分表统一使用该字段路由。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间，用于跨时区展示和审计。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区，例如 Asia/Shanghai。
     */
    private String transactionTimeZone;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Integer deleted;

    /**
     * 创建时间，DATETIME(3)。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间，DATETIME(3)。
     */
    private LocalDateTime updateTime;
}

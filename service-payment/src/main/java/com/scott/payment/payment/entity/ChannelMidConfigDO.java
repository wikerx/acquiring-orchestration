package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 渠道 MID 配置只读实体，位于 service-payment 数据访问层，用于交易路由读取真实渠道 MID、能力范围和渠道元数据。
 */
@Data
@TableName("channel_mid_config")
public class ChannelMidConfigDO {

    /**
     * {@code ChannelMidConfigDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道ID，用于定位 {@code ChannelMidConfigDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private Long channelId;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;

    /**
     * {@code channelMid}，用于定位 {@code ChannelMidConfigDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private String channelMid;

    /**
     * {@code midName}，用于定位渠道商户号配置或渠道侧 MID。
     */
    private String midName;

    /**
     * {@code terminalId}，用于定位 {@code ChannelMidConfigDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String terminalId;

    /**
     * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
     */
    private String businessType;

    /**
     * 支付方式范围，表示支付方式、通知方式或调用方式。
     */
    private String paymentMethodScope;

    /**
     * 银行卡品牌范围：ALL、NONE 或逗号分隔的品牌编码。
     */
    private String cardBrandScope;

    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    private String transactionTypeScope;

    /**
     * 币种范围，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String currencyScope;

    /**
     * 允许标识国家或地区范围，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     */
    private String allowedCountryScope;

    /**
     * 默认结算币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String defaultSettlementCurrency;

    /**
     * 持久化的{@code settlementCycle}，用于还原当前记录的业务事实。
     */
    private String settlementCycle;

    /**
     * 持久化的{@code settlementCutoffTime}，用于还原当前记录的业务事实。
     */
    private LocalTime settlementCutoffTime;

    /**
     * 结算时间时区，使用 IANA 时区标识解释关联的本地日期时间。
     */
    private String settlementTimeZone;

    /**
     * 持久化的{@code mcc}，用于还原当前记录的业务事实。
     */
    private String mcc;

    /**
     * 持久化的{@code statementDescriptor}，用于还原当前记录的业务事实。
     */
    private String statementDescriptor;

    /**
     * 持久化的{@code metadataValueJson}，用于还原当前记录的业务事实。
     */
    private String metadataValueJson;

    /**
     * {@code midStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private Integer midStatus;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    private LocalDateTime effectiveTime;

    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    private LocalDateTime expireTime;

    /**
     * MID 配置最后修改时间，用于隔离进程内短时敏感元数据缓存版本。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}

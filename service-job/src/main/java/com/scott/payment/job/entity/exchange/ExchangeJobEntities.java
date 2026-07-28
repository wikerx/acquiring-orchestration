package com.scott.payment.job.entity.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeJobEntities
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Job Entities 持久化模型，位于 调度任务服务，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。
 * @status : create
 */
public final class ExchangeJobEntities {

    private ExchangeJobEntities() {
    }

    /**
     * 汇率源配置数据库实体。
     */
    @Data
    @TableName("exchange_rate_source")
    public static class ExchangeRateSourceDO {

        @TableId(type = IdType.AUTO)
        /**
         * Exchange Rate Source DO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;

        /**
         * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceCode;

        /**
         * source Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceName;

        /**
         * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceType;

        /**
         * request URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String requestUrl;

        /**
         * default Source，用于保存 Exchange Rate Source DO 中与 default来源 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer defaultSource;

        /**
         * priority，用于保存 Exchange Rate Source DO 中与 priority 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer priority;

        /**
         * timeout Seconds，用于保存 Exchange Rate Source DO 中与 timeoutseconds 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer timeoutSeconds;

        /**
         * source Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer sourceStatus;

        /**
         * last Fetch Time，用于保存 Exchange Rate Source DO 中与 lastfetchtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime lastFetchTime;

        /**
         * last Fetch Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String lastFetchStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;

        /**
         * create By，用于保存 Exchange Rate Source DO 中与 createby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createBy;

        /**
         * create Time，用于保存 Exchange Rate Source DO 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;

        /**
         * update By，用于保存 Exchange Rate Source DO 中与 updateby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String updateBy;

        /**
         * update Time，用于保存 Exchange Rate Source DO 中与 updatetime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */

        private Long deleted;
    }

    /**
     * 原始汇率数据库实体。
     */
    @Data
    @TableName("exchange_raw_rate")
    public static class ExchangeRawRateDO {

        @TableId(type = IdType.AUTO)
        /**
         * Exchange Raw Rate DO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;

        /**
         * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceCode;

        /**
         * base Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String baseCurrency;

        /**
         * quote Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String quoteCurrency;

        /** 现钞买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */

        private BigDecimal cashBuyRate;

        /** 现钞卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */

        private BigDecimal cashSellRate;

        /** 现汇买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */

        private BigDecimal spotBuyRate;

        /** 现汇卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */

        private BigDecimal spotSellRate;

        /** 中间折算价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */

        private BigDecimal middleRate;

        /** 外部汇率源发布时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime publishTime;

        /** 系统拉取时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime fetchTime;

        /** 原始汇率生效时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime effectiveTime;

        /**
         * create Method，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createMethod;

        /**
         * batch No，用于保存 Exchange Raw Rate DO 中与 batchno 相关的业务属性。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String batchNo;

        /**
         * rate Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String rateStatus;

        /**
         * void Reason，用于保存 Exchange Raw Rate DO 中与 voidreason 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String voidReason;

        /**
         * create By，用于保存 Exchange Raw Rate DO 中与 createby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createBy;

        /**
         * create Time，用于保存 Exchange Raw Rate DO 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;

        /**
         * update By，用于保存 Exchange Raw Rate DO 中与 updateby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String updateBy;

        /**
         * update Time，用于保存 Exchange Raw Rate DO 中与 updatetime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */

        private Long deleted;
    }

    /**
     * 汇率规则数据库实体。
     *
     * <p>任务服务只读取启用规则，用于把新入库的原始汇率自动生成交易或结算业务汇率。</p>
     */
    @Data
    @TableName("exchange_rate_rule")
    public static class ExchangeRateRuleDO {

        @TableId(type = IdType.AUTO)
        /**
         * Exchange Rate Rule DO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;

        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String rateType;

        /**
         * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceCode;

        /**
         * base Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String baseCurrency;

        /**
         * quote Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String quoteCurrency;

        /**
         * rate Field，用于保存 Exchange Rate Rule DO 中与 汇率field 相关的业务属性。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String rateField;

        /**
         * adjust Direction，用于保存 Exchange Rate Rule DO 中与 adjustdirection 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String adjustDirection;

        /**
         * adjust Method，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String adjustMethod;

        /** 调整值：BP 按基点解释，PERCENT 按百分比解释，必须使用 BigDecimal。 */

        private BigDecimal adjustValue;

        /** 最终业务汇率保留的小数位。 */

        private Integer decimalScale;

        /**
         * rounding Mode，用于保存 Exchange Rate Rule DO 中与 roundingmode 相关的业务属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roundingMode;

        /**
         * priority，用于保存 Exchange Rate Rule DO 中与 priority 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer priority;

        /** 规则生效开始时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime effectiveStartTime;

        /** 规则生效结束时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime effectiveEndTime;

        /**
         * rule Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer ruleStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;

        /**
         * create By，用于保存 Exchange Rate Rule DO 中与 createby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createBy;

        /**
         * create Time，用于保存 Exchange Rate Rule DO 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;

        /**
         * update By，用于保存 Exchange Rate Rule DO 中与 updateby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String updateBy;

        /**
         * update Time，用于保存 Exchange Rate Rule DO 中与 updatetime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */

        private Long deleted;
    }

    /**
     * 业务汇率数据库实体。
     *
     * <p>任务服务自动生成的记录是交易和结算链路可直接读取的最终汇率。</p>
     */
    @Data
    @TableName("exchange_business_rate")
    public static class ExchangeBusinessRateDO {

        @TableId(type = IdType.AUTO)
        /**
         * Exchange Business Rate DO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;

        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String rateType;

        /**
         * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceCode;

        /**
         * base Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String baseCurrency;

        /**
         * quote Currency，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String quoteCurrency;

        /**
         * raw Rate ID，用于定位 Exchange Business Rate DO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long rawRateId;

        /**
         * rule ID，用于定位 Exchange Business Rate DO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long ruleId;

        /** 规则选取的原始报价字段值，必须使用 BigDecimal。 */

        private BigDecimal originalRate;

        /** 最终业务汇率，已应用调整和舍入规则，必须使用 BigDecimal。 */

        private BigDecimal finalRate;

        /**
         * adjust Description，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String adjustDescription;

        /** 业务汇率生效时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime effectiveTime;

        /** 业务汇率失效时间，数据库保留 DATETIME(3)。 */

        private LocalDateTime expireTime;

        /**
         * generate Method，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String generateMethod;

        /**
         * rate Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String rateStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;

        /**
         * create By，用于保存 Exchange Business Rate DO 中与 createby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createBy;

        /**
         * create Time，用于保存 Exchange Business Rate DO 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;

        /**
         * update By，用于保存 Exchange Business Rate DO 中与 updateby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String updateBy;

        /**
         * update Time，用于保存 Exchange Business Rate DO 中与 updatetime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */

        private Long deleted;
    }

    /**
     * 汇率拉取日志数据库实体。
     */
    @Data
    @TableName("exchange_rate_fetch_log")
    public static class ExchangeRateFetchLogDO {

        @TableId(type = IdType.AUTO)
        /**
         * Exchange Rate Fetch Log DO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;

        /**
         * batch No，用于保存 Exchange Rate Fetch Log DO 中与 batchno 相关的业务属性。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String batchNo;

        /**
         * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String sourceCode;

        /**
         * fetch Start Time，用于保存 Exchange Rate Fetch Log DO 中与 fetchstarttime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime fetchStartTime;

        /**
         * fetch End Time，用于保存 Exchange Rate Fetch Log DO 中与 fetchendtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime fetchEndTime;

        /**
         * fetch Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String fetchStatus;

        /**
         * request URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String requestUrl;

        /**
         * total Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer totalCount;

        /**
         * success Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer successCount;

        /**
         * duplicate Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer duplicateCount;

        /**
         * skip Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer skipCount;

        /**
         * error Message，用于保存 Exchange Rate Fetch Log DO 中与 errormessage 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String errorMessage;

        /**
         * create Time，用于保存 Exchange Rate Fetch Log DO 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;
    }
}

package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelDTOs
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : Channel DTOs 聚合类型，位于 运营后台服务，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 * @status : create
 */
public final class ChannelDTOs {

    private ChannelDTOs() {
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelInfoQuery
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : Channel Info Query 查询条件模型，位于 运营后台服务，承载筛选字段、时间范围、分页参数和列表查询边界。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelInfoQuery extends PageRequest {
        /**
         * 渠道管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 渠道状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer channelStatus;
        /**
         * {@code supportAcquiring}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer supportAcquiring;
        /**
         * 支持代付，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer supportPayout;
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer support3ds;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelInfoSaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道信息写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class ChannelInfoSaveRequest {
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "channelCode is required")
        private String channelCode;
        /**
         * {@code channelCnName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "channelCnName is required")
        private String channelCnName;
        /**
         * {@code channelEnName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "channelEnName is required")
        private String channelEnName;
        /**
         * 渠道状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        @NotNull(message = "channelStatus is required")
        private Integer channelStatus;
        /**
         * {@code supportAcquiring}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotNull(message = "supportAcquiring is required")
        private Integer supportAcquiring;
        /**
         * 支持代付，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotNull(message = "supportPayout is required")
        private Integer supportPayout;
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotNull(message = "support3ds is required")
        private Integer support3ds;
        /**
         * 默认请求URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String defaultRequestUrl;
        /**
         * 请求中的{@code defaultInteractionMode}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String defaultInteractionMode;
        /**
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
         */
        private Integer readTimeoutSeconds;
        /**
         * {@code sortOrder}，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道 MID 参数模板，定义该渠道后续 MID 维护时必须填写的字段集合。
         */
        @Valid
        private List<ChannelMetadataSchemaItem> metadataSchemas = new ArrayList<>();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelInfoResponse
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道信息响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class ChannelInfoResponse {
        /**
         * 渠道信息响应 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;
        /**
         * {@code channelCnName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCnName;
        /**
         * {@code channelEnName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelEnName;
        /**
         * 渠道状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer channelStatus;
        /**
         * {@code supportAcquiring}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer supportAcquiring;
        /**
         * 支持代付，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer supportPayout;
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer support3ds;
        /**
         * 默认请求URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String defaultRequestUrl;
        /**
         * 响应中的{@code defaultInteractionMode}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String defaultInteractionMode;
        /**
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
         */
        private Integer readTimeoutSeconds;
        /**
         * {@code sortOrder}，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * {@code acquiringPaymentMethods}，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> acquiringPaymentMethods = new ArrayList<>();
        /**
         * {@code payoutPaymentMethods}，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> payoutPaymentMethods = new ArrayList<>();
        /**
         * {@code metadataSchemas}集合，承载 渠道信息响应 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<ChannelMetadataSchemaItem> metadataSchemas = new ArrayList<>();
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelMetadataSchemaItem
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道元数据结构字段定义，描述配置项键名、类型、必填性、敏感性和校验规则。
     * @status : create
     */
    @Data
    public static class ChannelMetadataSchemaItem {
        /**
         * 元数据模板主键，新增模板字段时为空。
         */
        private Long id;
        /**
         * MID 参数 key，例如 merchantId、username、privateKey。
         */
        private String fieldKey;
        /**
         * 页面展示名称，例如商户号、访问账户、商户私钥。
         */
        private String fieldLabel;
        /**
         * 字段类型，决定后续 MID 表单控件和基础校验。
         */
        private String fieldType;
        /**
         * 是否必填：0否，1是。
         */
        private Integer requiredFlag;
        /**
         * 是否敏感：0否，1是；敏感字段后续 MID 值必须加密存储并脱敏展示。
         */
        private Integer sensitiveFlag;
        /**
         * 可选正则表达式，用于后续 MID 值的格式校验。
         */
        private String validationRegex;
        /**
         * 页面输入占位说明。
         */
        private String placeholder;
        /**
         * 非敏感字段默认值；敏感字段不建议配置默认值。
         */
        private String defaultValue;
        /**
         * 字段排序，数字越小越靠前。
         */
        private Integer sortOrder;
        /**
         * 模板字段状态：0停用，1启用。
         */
        private Integer fieldStatus;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelMidConfigQuery
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道mid配置查询查询条件模型，位于 运营后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelMidConfigQuery extends PageRequest {
        /**
         * 渠道ID。
         */
        private Long channelId;
        /**
         * 渠道编码。
         */
        private String channelCode;
        /**
         * 平台商户输入的真实渠道 MID。
         */
        private String channelMid;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        private String businessType;
        /**
         * MID 状态：0停用，1启用。
         */
        private Integer midStatus;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelMidConfigSaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道mid配置写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class ChannelMidConfigSaveRequest {
        /**
         * 渠道ID，关联 channel_info.id。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道侧真实 MID 或商户号；优先由 MID 元数据中的商户号/MID 字段派生，兼容历史调用可显式传入。
         */
        private String channelMid;
        /**
         * MID 后台展示名称，可为空；为空时后端使用渠道 MID 作为落库兜底名称。
         */
        private String midName;
        /**
         * 渠道终端号，可为空。
         */
        private String terminalId;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支持支付方式，ALL 或逗号分隔。
         */
        @NotBlank(message = "paymentMethodScope is required")
        private String paymentMethodScope;
        /**
         * 银行卡品牌范围，非银行卡支付方式为 NONE，银行卡为 ALL 或 card_brand 字典值逗号分隔。
         */
        private String cardBrandScope;
        /**
         * 支持交易类型，由渠道能力按支付方式派生，前端不再要求人工维护。
         */
        private String transactionTypeScope;
        /**
         * 支持交易币种，ALL 或 ISO 4217 三位币种逗号分隔。
         */
        @NotBlank(message = "currencyScope is required")
        private String currencyScope;
        /**
         * 允许交易国家，ALL 或 ISO 国家码逗号分隔。
         */
        @NotBlank(message = "allowedCountryScope is required")
        private String allowedCountryScope;
        /**
         * 默认结算币种。
         */
        @NotBlank(message = "defaultSettlementCurrency is required")
        private String defaultSettlementCurrency;
        /**
         * 结算周期：T0/T1/T2。
         */
        @NotBlank(message = "settlementCycle is required")
        private String settlementCycle;
        /**
         * 结算日切时间，可为空。
         */
        private LocalTime settlementCutoffTime;
        /**
         * 结算时区。
         */
        @NotBlank(message = "settlementTimeZone is required")
        private String settlementTimeZone;
        /**
         * 渠道 MID 对应的 MCC，用于渠道商户号能力和路由约束。
         */
        private String mcc;
        /**
         * 账单描述。
         */
        private String statementDescriptor;
        /**
         * 根据渠道元数据模板录入的 MID 元数据 JSON。
         */
        private String metadataValueJson;
        /**
         * MID 状态：0停用，1启用。
         */
        @NotNull(message = "midStatus is required")
        private Integer midStatus;
        /**
         * 生效时间。
         */
        private LocalDateTime effectiveTime;
        /**
         * 失效时间，空表示永不过期。
         */
        private LocalDateTime expireTime;
        /**
         * 备注。
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelMidConfigResponse
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道mid配置响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class ChannelMidConfigResponse {
        /**
         * {@code ChannelMidConfigResponse} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 渠道ID，用于定位 {@code ChannelMidConfigResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;
        /**
         * 渠道名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelName;
        /**
         * {@code channelMid}，用于定位 {@code ChannelMidConfigResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private String channelMid;
        /**
         * {@code midName}，用于定位渠道商户号配置或渠道侧 MID。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String midName;
        /**
         * {@code terminalId}，用于定位 {@code ChannelMidConfigResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String terminalId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String businessType;
        /**
         * 支付方式范围，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethodScope;
        /**
         * 响应中的卡品牌范围，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBrandScope;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionTypeScope;
        /**
         * 币种范围，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String currencyScope;
        /**
         * 允许标识国家或地区范围，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String allowedCountryScope;
        /**
         * 默认结算币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String defaultSettlementCurrency;
        /**
         * 响应中的{@code settlementCycle}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String settlementCycle;
        /**
         * 响应中的{@code settlementCutoffTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalTime settlementCutoffTime;
        /**
         * 结算时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String settlementTimeZone;
        /**
         * 响应中的{@code mcc}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String mcc;
        /**
         * 响应中的{@code statementDescriptor}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String statementDescriptor;
        /**
         * 响应中的{@code metadataValueJson}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：JSON 字符串或结构化对象；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：内容必须先脱敏再进入日志；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String metadataValueJson;
        /**
         * {@code midStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer midStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime expireTime;
        /**
         * 备注，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantChannelMidBindingQuery
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : Merchant Channel MID Binding Query 查询条件模型，位于 运营后台服务，承载筛选字段、时间范围、分页参数和列表查询边界。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantChannelMidBindingQuery extends PageRequest {
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 渠道ID，用于定位 {@code MerchantChannelMidBindingQuery} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String channelCode;
        /**
         * {@code midConfigId}，用于定位 {@code MerchantChannelMidBindingQuery} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Long midConfigId;
        /**
         * {@code bindingStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer bindingStatus;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantChannelMidBindingSaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 商户渠道midbinding写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class MerchantChannelMidBindingSaveRequest {
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        @NotBlank(message = "merchantId is required")
        private String merchantId;
        /**
         * {@code midConfigId}，用于定位 {@code MerchantChannelMidBindingSaveRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotNull(message = "midConfigId is required")
        private Long midConfigId;
        /**
         * {@code bindingStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        @NotNull(message = "bindingStatus is required")
        private Integer bindingStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime expireTime;
        /**
         * 备注，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantChannelMidBindingResponse
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 商户渠道midbinding响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class MerchantChannelMidBindingResponse {
        /**
         * {@code MerchantChannelMidBindingResponse} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 渠道ID，用于定位 {@code MerchantChannelMidBindingResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;
        /**
         * 渠道名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelName;
        /**
         * {@code midConfigId}，用于定位 {@code MerchantChannelMidBindingResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long midConfigId;
        /**
         * {@code channelMid}，用于定位 {@code MerchantChannelMidBindingResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private String channelMid;
        /**
         * {@code midName}，用于定位渠道商户号配置或渠道侧 MID。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String midName;
        /**
         * {@code bindingStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer bindingStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime expireTime;
        /**
         * 备注，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CapabilityQuery
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : Capability Query 查询条件模型，位于 运营后台服务，承载筛选字段、时间范围、分页参数和列表查询边界。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapabilityQuery extends PageRequest {
        /**
         * 渠道ID，用于定位 {@code CapabilityQuery} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String paymentMethod;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String transactionType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String currencyCode;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String cardBrand;
        /**
         * 渠道能力状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer capabilityStatus;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CapabilitySaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道能力写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class CapabilitySaveRequest {
        /**
         * 渠道ID，用于定位 {@code CapabilitySaveRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> transactionTypes = new ArrayList<>();
        /**
         * {@code currencyCodes}，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> currencyCodes = new ArrayList<>();
        /** 默认交易币种，必须且只能从允许币种中选择一个。 */
        @NotBlank(message = "defaultTransactionCurrency is required")
        private String defaultTransactionCurrency;
        /**
         * {@code cardBrands}集合，承载 {@code CapabilitySaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> cardBrands = new ArrayList<>();
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer support3ds;
        /**
         * {@code supportIncrementalAuthorization}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道能力状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        @NotNull(message = "capabilityStatus is required")
        private Integer capabilityStatus;
        /**
         * {@code sortOrder}，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CapabilitySupportRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道能力支持标识请求模型，位于 运营后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     * @status : create
     */
    @Data
    public static class CapabilitySupportRequest {
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer support3ds;
        /**
         * {@code supportIncrementalAuthorization}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer supportIncrementalAuthorization;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CapabilityResponse
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道能力响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class CapabilityResponse {
        /**
         * {@code CapabilityResponse} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 渠道ID，用于定位 {@code CapabilityResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;
        /**
         * 渠道名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelName;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> transactionTypes = new ArrayList<>();
        /**
         * {@code currencyCodes}，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> currencyCodes = new ArrayList<>();
        /** 默认交易币种，渠道不直接支持请求币种时用于交易换汇路由。 */
        private String defaultTransactionCurrency;
        /**
         * {@code cardBrands}集合，承载 {@code CapabilityResponse} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> cardBrands = new ArrayList<>();
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer support3ds;
        /**
         * {@code supportIncrementalAuthorization}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道能力状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer capabilityStatus;
        /**
         * {@code sortOrder}，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : LimitQuery
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : Limit Query 查询条件模型，位于 运营后台服务，承载筛选字段、时间范围、分页参数和列表查询边界。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LimitQuery extends PageRequest {
        /**
         * 渠道ID，用于定位 {@code LimitQuery} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String paymentMethod;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String cardBrand;
        /**
         * {@code limitType}，用于区分 {@code LimitQuery} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private String limitType;
        /**
         * 规则状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer ruleStatus;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : LimitSaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 限额写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class LimitSaveRequest {
        /**
         * 渠道ID，用于定位 {@code LimitSaveRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBrand;
        /**
         * {@code limitType}，用于区分 {@code LimitSaveRequest} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        @NotBlank(message = "limitType is required")
        private String limitType;
        /**
         * {@code limitAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        @NotNull(message = "limitAmount is required")
        private BigDecimal limitAmount;
        /**
         * 规则状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : LimitBatchSaveRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : limit批次写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class LimitBatchSaveRequest {
        /**
         * {@code items}集合，承载 {@code LimitBatchSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        @Valid
        private List<LimitSaveRequest> items = new ArrayList<>();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : LimitResponse
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 限额响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class LimitResponse {
        /**
         * {@code LimitResponse} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 渠道ID，用于定位 {@code LimitResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;
        /**
         * 渠道名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelName;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String businessType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBrand;
        /**
         * {@code limitType}，用于区分 {@code LimitResponse} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private String limitType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String limitCurrency;
        /**
         * {@code limitAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal limitAmount;
        /**
         * 规则状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建人账号标识，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String createBy;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String updateBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : StatusRequest
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : Status Request 状态变更请求模型，位于 运营后台服务，承载启停、冻结、审核或处理状态更新所需字段。
     * @status : create
     */
    @Data
    public static class StatusRequest {
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ChannelOption
     * @date : 2026-07-03 16:10
     * @email : scott_x@163.com
     * @description : 渠道管理页面通用选项 DTO，返回受控代码、名称和可选状态。
     * @status : create
     */
    @Data
    public static class ChannelOption {
        /**
         * 渠道选项 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Long id;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String channelCode;
        /**
         * 渠道名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String channelName;
        /**
         * 渠道状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer channelStatus;
        /**
         * {@code supportAcquiring}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer supportAcquiring;
        /**
         * 支持代付，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer supportPayout;
        /**
         * {@code support3ds}，表示当前渠道、配置或接口是否支持对应能力。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Integer support3ds;
    }
}

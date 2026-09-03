package com.scott.payment.merchant.dto.system;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemDTOs
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Merchant System DTOs 聚合类型，位于 商户后台服务，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 * @status : create
 */
public final class MerchantSystemDTOs {

    private MerchantSystemDTOs() {
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : IdsRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : ID请求模型，位于 商户后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     * @status : create
     */
    @Data
    public static class IdsRequest {
        /**
         * {@code ids}集合，承载 ID请求 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> ids = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : StatusRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Status Request 状态变更请求模型，位于 商户后台服务，承载启停、冻结、审核或处理状态更新所需字段。
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
        private Integer status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 部门查询条件模型，位于 商户后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeptQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 部门写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class DeptSaveRequest {
        /**
         * {@code parentId}，用于定位 {@code DeptSaveRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long parentId = 0L;
        /**
         * 部门编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String deptCode;
        /**
         * 部门名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String deptName;
        /**
         * {@code leaderAccountId}，用于定位 {@code DeptSaveRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户部门展示 DTO，承载部门层级、负责人、状态和审计时间等管理页面字段。
     * @status : create
     */
    @Data
    public static class DeptDTO {
        /**
         * 部门ID，用于定位 {@code DeptDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long deptId;
        /**
         * {@code parentId}，用于定位 {@code DeptDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long parentId;
        /**
         * 部门编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String deptCode;
        /**
         * 部门名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String deptName;
        /**
         * {@code leaderAccountId}，用于定位 {@code DeptDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
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
        private LocalDateTime createdAt;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updatedAt;
        /**
         * 子节点集合，承载 {@code DeptDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<DeptDTO> children = new ArrayList<>();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 岗位查询条件模型，位于 商户后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PostQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 岗位写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class PostSaveRequest {
        /**
         * 岗位编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postCode;
        /**
         * 岗位名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postName;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户岗位展示 DTO，承载岗位编码、名称、排序和启停状态。
     * @status : create
     */
    @Data
    public static class PostDTO {
        /**
         * 岗位ID，用于定位 {@code PostDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long postId;
        /**
         * 岗位编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postCode;
        /**
         * 岗位名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postName;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
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
        private LocalDateTime createdAt;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号查询条件模型，位于 商户后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 角色ID，用于定位 账号查询请求 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long roleId;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountBaseSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号基础信息写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class AccountBaseSaveRequest {
        /**
         * 请求中的登录账号，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String loginAccount;
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String password;
        /**
         * {@code realName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String realName;
        /**
         * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountSaveRequest extends AccountBaseSaveRequest {
        /**
         * {@code roleIds}集合，承载 {@code AccountSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * {@code deptIds}集合，承载 {@code AccountSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * {@code postIds}集合，承载 {@code AccountSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountResetPasswordRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号重置password请求模型，位于 商户后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     * @status : create
     */
    @Data
    public static class AccountResetPasswordRequest {
        /**
         * 商户员工新登录密码。接口层只接收明文，服务层必须立即哈希后落库，禁止写入日志。
         */
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        private String password;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户账号展示 DTO，聚合登录账号、人员信息、组织归属、角色和 MFA 状态。
     * @status : create
     */
    @Data
    public static class AccountDTO {
        /**
         * 账号ID，用于定位 {@code AccountDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long accountId;
        /**
         * 用户ID，用于定位 {@code AccountDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long userId;
        /**
         * 商户用户ID，用于定位 {@code AccountDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long merchantUserId;
        /**
         * 登录账号字段，保存 {@code AccountDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String loginAccount;
        /**
         * {@code realName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String realName;
        /**
         * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * {@code locked}字段，保存 {@code AccountDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer locked;
        /**
         * {@code lastLoginAt}字段，保存 {@code AccountDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime lastLoginAt;
        /**
         * OTP 策略：OPTIONAL、REQUIRED、EXEMPT。
         */
        private String mfaPolicy;
        /**
         * OTP 状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED。
         */
        private String mfaStatus;
        /**
         * OTP 完成绑定时间。
         */
        private LocalDateTime mfaBindTime;
        /**
         * 最近一次 OTP 验证成功时间。
         */
        private LocalDateTime mfaLastVerifyTime;
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime mfaExemptUntil;
        /**
         * OTP 连续失败后的临时锁定截止时间。
         */
        private LocalDateTime mfaLockedUntil;
        /**
         * 是否当前登录账号，用于页面隐藏重置、豁免、停用等自我降级 OTP 操作。
         */
        private Boolean currentAccount;
        /**
         * {@code roleIds}集合，承载 {@code AccountDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * {@code roleNames}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> roleNames = Collections.emptyList();
        /**
         * {@code deptIds}集合，承载 {@code AccountDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * {@code postIds}集合，承载 {@code AccountDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createdAt;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaActionRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号MFA动作请求模型，位于 商户后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     * @status : create
     */
    @Data
    public static class AccountMfaActionRequest {
        /**
         * OTP 安全操作原因，必须写明审批依据或处理背景。
         */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must not exceed 500")
        private String reason;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaExemptRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号MFAexempt请求模型，位于 商户后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountMfaExemptRequest extends AccountMfaActionRequest {
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime exemptUntil;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaStatusResponse
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 账号MFA状态响应模型，位于 商户后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class AccountMfaStatusResponse {
        /**
         * 登录账号ID。
         */
        private Long accountId;
        /**
         * 商户员工登录账号。
         */
        private String loginAccount;
        /**
         * OTP 策略。
         */
        private String mfaPolicy;
        /**
         * OTP 状态。
         */
        private String mfaStatus;
        /**
         * 完成绑定时间。
         */
        private LocalDateTime bindTime;
        /**
         * 最近验证成功时间。
         */
        private LocalDateTime lastVerifyTime;
        /**
         * 临时锁定截止时间。
         */
        private LocalDateTime lockedUntil;
        /**
         * 豁免截止时间。
         */
        private LocalDateTime exemptUntil;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 角色查询条件模型，位于 商户后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
     * @status : create
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoleQueryRequest extends PageRequest {
        /**
         * 角色名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleName;
        /**
         * 角色编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleCode;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * 请求中的创建开始时间，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String createdStartTime;
        /**
         * 请求中的创建结束时间，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String createdEndTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 角色写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class RoleSaveRequest {
        /**
         * 角色编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleCode;
        /**
         * 角色名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleName;
        /**
         * 请求中的{@code dataScope}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String dataScope;
        /**
         * 说明，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String description;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo = 100;
        /**
         * {@code menuIds}集合，承载 {@code RoleSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> menuIds;
        /**
         * {@code permissionIds}集合，承载 {@code RoleSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> permissionIds;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户角色展示 DTO，承载角色编码、数据范围、状态和授权摘要。
     * @status : create
     */
    @Data
    public static class RoleDTO {
        /**
         * 角色ID，用于定位 {@code RoleDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long roleId;
        /**
         * 角色编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleCode;
        /**
         * 角色名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleName;
        /**
         * 角色类型，用于区分 {@code RoleDTO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleType;
        /**
         * {@code dataScope}字段，保存 {@code RoleDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String dataScope;
        /**
         * 说明，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String description;
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer sortNo;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleMenuAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户角色菜单授权 DTO，返回已授权菜单主键和可展示菜单明细。
     * @status : create
     */
    @Data
    public static class RoleMenuAuthDTO {
        /**
         * 角色ID，用于定位 {@code RoleMenuAuthDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long roleId;
        /**
         * {@code checkedMenuIds}集合，承载 {@code RoleMenuAuthDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * 菜单集合，承载 {@code RoleMenuAuthDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RolePermissionAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户角色权限授权 DTO，返回已授权权限主键和权限明细。
     * @status : create
     */
    @Data
    public static class RolePermissionAuthDTO {
        /**
         * 角色ID，用于定位 {@code RolePermissionAuthDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long roleId;
        /**
         * {@code checkedPermissionIds}集合，承载 {@code RolePermissionAuthDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * 权限集合，承载 {@code RolePermissionAuthDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<PermissionDTO> permissions = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 角色授权树写操作请求模型，位于 商户后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     * @status : create
     */
    @Data
    public static class RoleGrantTreeSaveRequest {
        /**
         * {@code menuIds}集合，承载 {@code RoleGrantTreeSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> menuIds = Collections.emptyList();
        /**
         * {@code permissionIds}集合，承载 {@code RoleGrantTreeSaveRequest} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> permissionIds = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户角色统一授权树 DTO，将目录、菜单、按钮和权限节点按父子关系返回。
     * @status : create
     */
    @Data
    public static class RoleGrantTreeDTO {
        /**
         * 角色ID，用于定位 {@code RoleGrantTreeDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long roleId;
        /**
         * 角色字段，保存 {@code RoleGrantTreeDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private RoleDTO role;
        /**
         * {@code checkedMenuIds}集合，承载 {@code RoleGrantTreeDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * {@code checkedPermissionIds}集合，承载 {@code RoleGrantTreeDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * 树集合，承载 {@code RoleGrantTreeDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AuthGrantNodeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户授权树节点 DTO，记录节点类型、父节点、选中状态及其子节点。
     * @status : create
     */
    @Data
    public static class AuthGrantNodeDTO {
        /**
         * {@code AuthGrantNodeDTO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String id;
        /**
         * 节点ID，用于定位 {@code AuthGrantNodeDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long nodeId;
        /**
         * 菜单ID，用于定位 {@code AuthGrantNodeDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long menuId;
        /**
         * 权限ID，用于定位 {@code AuthGrantNodeDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long permissionId;
        /**
         * 节点类型，用于区分 {@code AuthGrantNodeDTO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String nodeType;
        /**
         * 名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String name;
        /**
         * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String code;
        /**
         * 子节点集合，承载 {@code AuthGrantNodeDTO} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<AuthGrantNodeDTO> children = new ArrayList<>();
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PermissionDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : 商户可分配权限 DTO，承载权限编码、名称、分组和所属菜单信息。
     * @status : create
     */
    @Data
    public static class PermissionDTO {
        /**
         * 权限ID，用于定位 {@code PermissionDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long permissionId;
        /**
         * 菜单ID，用于定位 {@code PermissionDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long menuId;
        /**
         * 权限编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String permissionCode;
        /**
         * 权限名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String permissionName;
        /**
         * 权限类型，用于区分 {@code PermissionDTO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String permissionType;
        /**
         * 资源方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String resourceMethod;
        /**
         * {@code resourcePath}，表示接口路径、资源路径或路由匹配路径。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String resourcePath;
    }
}

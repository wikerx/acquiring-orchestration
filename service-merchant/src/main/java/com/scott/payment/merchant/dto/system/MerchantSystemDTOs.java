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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : IdsRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Ids Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class IdsRequest {
        /**
         * ids，用于保存 Ids Request 中与 ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> ids = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : StatusRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Status Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class StatusRequest {
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Dept Query Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class DeptQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Dept Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class DeptSaveRequest {
        /**
         * parent ID，用于定位 Dept Save Request 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long parentId = 0L;
        /**
         * dept Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String deptCode;
        /**
         * dept Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String deptName;
        /**
         * leader Account ID，用于定位 Dept Save Request 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * phone，用于保存 Dept Save Request 中与 phone 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * status，表示当前记录在业务流程中的处理状态。
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Dept DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class DeptDTO {
        /**
         * dept ID，用于定位 Dept DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long deptId;
        /**
         * parent ID，用于定位 Dept DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long parentId;
        /**
         * dept Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String deptCode;
        /**
         * dept Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String deptName;
        /**
         * leader Account ID，用于定位 Dept DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * phone，用于保存 Dept DTO 中与 phone 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo;
        /**
         * status，表示当前记录在业务流程中的处理状态。
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
         * created At，用于保存 Dept DTO 中与 createdat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At，用于保存 Dept DTO 中与 updatedat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updatedAt;
        /**
         * children，用于保存 Dept DTO 中与 children 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<DeptDTO> children = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Post Query Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class PostQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Post Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class PostSaveRequest {
        /**
         * post Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String postCode;
        /**
         * post Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String postName;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * status，表示当前记录在业务流程中的处理状态。
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Post DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class PostDTO {
        /**
         * post ID，用于定位 Post DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long postId;
        /**
         * post Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String postCode;
        /**
         * post Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String postName;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo;
        /**
         * status，表示当前记录在业务流程中的处理状态。
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
         * created At，用于保存 Post DTO 中与 createdat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At，用于保存 Post DTO 中与 updatedat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Query Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * role ID，用于定位 Account Query Request 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long roleId;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountBaseSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Base Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountBaseSaveRequest {
        /**
         * login Account，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String loginAccount;
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String password;
        /**
         * real Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String realName;
        /**
         * mobile，用于保存 Account Base Save Request 中与 mobile 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountSaveRequest extends AccountBaseSaveRequest {
        /**
         * role Ids，用于保存 Account Save Request 中与 角色ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * dept Ids，用于保存 Account Save Request 中与 部门ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * post Ids，用于保存 Account Save Request 中与 岗位ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountResetPasswordRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Reset Password Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountResetPasswordRequest {
        /**
         * 商户员工新登录密码。接口层只接收明文，服务层必须立即哈希后落库，禁止写入日志。
         */
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        private String password;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountDTO {
        /**
         * account ID，用于定位 Account DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long accountId;
        /**
         * user ID，用于定位 Account DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long userId;
        /**
         * merchant User ID，用于定位 Account DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long merchantUserId;
        /**
         * login Account，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String loginAccount;
        /**
         * real Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String realName;
        /**
         * mobile，用于保存 Account DTO 中与 mobile 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * locked，用于保存 Account DTO 中与 locked 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer locked;
        /**
         * last Login At，用于保存 Account DTO 中与 lastloginat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
         * role Ids，用于保存 Account DTO 中与 角色ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * role Names，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<String> roleNames = Collections.emptyList();
        /**
         * dept Ids，用于保存 Account DTO 中与 部门ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * post Ids，用于保存 Account DTO 中与 岗位ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
        /**
         * created At，用于保存 Account DTO 中与 createdat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createdAt;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaActionRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Mfa Action Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountMfaActionRequest {
        /**
         * OTP 安全操作原因，必须写明审批依据或处理背景。
         */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must not exceed 500")
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaExemptRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Mfa Exempt Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AccountMfaExemptRequest extends AccountMfaActionRequest {
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime exemptUntil;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaStatusResponse
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Account Mfa Status Response 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Query Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleQueryRequest extends PageRequest {
        /**
         * role Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleName;
        /**
         * role Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleCode;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * created Start Time，用于保存 Role Query Request 中与 createdstarttime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createdStartTime;
        /**
         * created End Time，用于保存 Role Query Request 中与 createdendtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createdEndTime;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleSaveRequest {
        /**
         * role Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleCode;
        /**
         * role Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleName;
        /**
         * data Scope，用于保存 Role Save Request 中与 datascope 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String dataScope;
        /**
         * description，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String description;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status = 1;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo = 100;
        /**
         * menu Ids，用于保存 Role Save Request 中与 菜单ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> menuIds;
        /**
         * permission Ids，用于保存 Role Save Request 中与 权限ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> permissionIds;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleDTO {
        /**
         * role ID，用于定位 Role DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long roleId;
        /**
         * role Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleCode;
        /**
         * role Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleName;
        /**
         * role Type，用于区分 Role DTO 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String roleType;
        /**
         * data Scope，用于保存 Role DTO 中与 datascope 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String dataScope;
        /**
         * description，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String description;
        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private Integer status;
        /**
         * sort No，用于控制列表展示或规则匹配时的排序优先级。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Integer sortNo;
        /**
         * created At，用于保存 Role DTO 中与 createdat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At，用于保存 Role DTO 中与 updatedat 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleMenuAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Menu Auth DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleMenuAuthDTO {
        /**
         * role ID，用于定位 Role Menu Auth DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long roleId;
        /**
         * checked Menu Ids，用于保存 Role Menu Auth DTO 中与 checked菜单ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * menus，用于保存 Role Menu Auth DTO 中与 菜单 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RolePermissionAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Permission Auth DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RolePermissionAuthDTO {
        /**
         * role ID，用于定位 Role Permission Auth DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long roleId;
        /**
         * checked Permission Ids，用于保存 Role Permission Auth DTO 中与 checked权限ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * permissions，用于保存 Role Permission Auth DTO 中与 权限 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<PermissionDTO> permissions = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Grant Tree Save Request 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleGrantTreeSaveRequest {
        /**
         * menu Ids，用于保存 Role Grant Tree Save Request 中与 菜单ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> menuIds = Collections.emptyList();
        /**
         * permission Ids，用于保存 Role Grant Tree Save Request 中与 权限ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> permissionIds = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Role Grant Tree DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class RoleGrantTreeDTO {
        /**
         * role ID，用于定位 Role Grant Tree DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long roleId;
        /**
         * role，用于保存 Role Grant Tree DTO 中与 角色 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private RoleDTO role;
        /**
         * checked Menu Ids，用于保存 Role Grant Tree DTO 中与 checked菜单ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * checked Permission Ids，用于保存 Role Grant Tree DTO 中与 checked权限ID 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * tree，用于保存 Role Grant Tree DTO 中与 树 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AuthGrantNodeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Auth Grant Node DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class AuthGrantNodeDTO {
        /**
         * Auth Grant Node DTO 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String id;
        /**
         * node ID，用于定位 Auth Grant Node DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long nodeId;
        /**
         * menu ID，用于定位 Auth Grant Node DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long menuId;
        /**
         * permission ID，用于定位 Auth Grant Node DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long permissionId;
        /**
         * node Type，用于区分 Auth Grant Node DTO 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String nodeType;
        /**
         * name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String name;
        /**
         * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String code;
        /**
         * children，用于保存 Auth Grant Node DTO 中与 children 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private List<AuthGrantNodeDTO> children = new ArrayList<>();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PermissionDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : Permission DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class PermissionDTO {
        /**
         * permission ID，用于定位 Permission DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long permissionId;
        /**
         * menu ID，用于定位 Permission DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long menuId;
        /**
         * permission Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String permissionCode;
        /**
         * permission Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String permissionName;
        /**
         * permission Type，用于区分 Permission DTO 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String permissionType;
        /**
         * resource Method，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String resourceMethod;
        /**
         * resource Path，表示接口路径、资源路径或路由匹配路径。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String resourcePath;
    }
}

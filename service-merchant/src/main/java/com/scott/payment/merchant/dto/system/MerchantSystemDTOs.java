package com.scott.payment.merchant.dto.system;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 商户组织、员工账号、角色和权限管理接口模型聚合。
 */
public final class MerchantSystemDTOs {

    /**
     * 禁止实例化商户系统 DTO 聚合类。
     */
    private MerchantSystemDTOs() {
    }

    /**
     * 批量主键请求。
     */
    @Data
    public static class IdsRequest {
        private List<Long> ids = Collections.emptyList();
    }

    /**
     * 账号或角色启停状态变更请求。
     */
    @Data
    public static class StatusRequest {
        private Integer status;
    }

    /**
     * 部门分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeptQueryRequest extends PageRequest {
        private String keyword;
        private Integer status;
    }

    /**
     * 部门新增或编辑请求。
     */
    @Data
    public static class DeptSaveRequest {
        private Long parentId = 0L;
        private String deptCode;
        private String deptName;
        private Long leaderAccountId;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;
        private String email;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo = 0;
        private Integer status = 1;
        private String remark;
    }

    /**
     * 商户部门展示 DTO，承载部门层级、负责人、状态和审计时间等管理页面字段。
     */
    @Data
    public static class DeptDTO {
        private Long deptId;
        private Long parentId;
        private String deptCode;
        private String deptName;
        private Long leaderAccountId;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;
        private String email;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<DeptDTO> children = new ArrayList<>();
    }

    /**
     * 岗位分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PostQueryRequest extends PageRequest {
        private String keyword;
        private Integer status;
    }

    /**
     * 岗位新增或编辑请求。
     */
    @Data
    public static class PostSaveRequest {
        private String postCode;
        private String postName;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo = 0;
        private Integer status = 1;
        private String remark;
    }

    /**
     * 商户岗位展示 DTO，承载岗位编码、名称、排序和启停状态。
     */
    @Data
    public static class PostDTO {
        private Long postId;
        private String postCode;
        private String postName;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 商户员工账号分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountQueryRequest extends PageRequest {
        private String keyword;
        private Long roleId;
        private Integer status;
    }

    /**
     * 商户员工账号基础资料编辑请求。
     */
    @Data
    public static class AccountBaseSaveRequest {
        private String loginAccount;
        private String realName;
        /**
         * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String mobile;
        private String email;
        private Integer status = 1;
    }

    /**
     * 商户员工账号新增请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountSaveRequest extends AccountBaseSaveRequest {
        /**
         * 激活邮件语言，目前支持 zh-CN 和 en-US。
         */
        @Pattern(regexp = "^(zh-CN|en-US)?$", message = "locale is invalid")
        private String locale;
        private List<Long> roleIds = Collections.emptyList();
        private List<Long> deptIds = Collections.emptyList();
        private List<Long> postIds = Collections.emptyList();
    }

    /**
     * 管理员重置员工登录密码请求。
     */
    @Data
    public static class AccountResetPasswordRequest {
        /**
         * 重置邮件语言，目前支持 zh-CN 和 en-US。
         */
        @Pattern(regexp = "^(zh-CN|en-US)?$", message = "locale is invalid")
        private String locale;
    }

    /**
     * 商户账号展示 DTO，聚合登录账号、人员信息、组织归属、角色和 MFA 状态。
     */
    @Data
    public static class AccountDTO {
        private Long accountId;
        private Long userId;
        private Long merchantUserId;
        private String loginAccount;
        private String realName;
        /**
         * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String mobile;
        private String email;
        /**
         * 待验证的新邮箱；验证完成前正式邮箱保持不变。
         */
        private String pendingEmail;
        /**
         * 账号激活状态：PENDING_ACTIVATION、ACTIVE。
         */
        private String activationStatus;
        /**
         * 账号激活完成时间。
         */
        private LocalDateTime activatedAt;
        /**
         * 当前正式邮箱验证完成时间；为空表示未验证。
         */
        private LocalDateTime emailVerifiedAt;
        /**
         * 当前正式邮箱验证状态：PENDING、VERIFIED。
         */
        private String emailVerificationStatus;
        private Integer status;
        private Integer locked;
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
        private List<Long> roleIds = Collections.emptyList();
        private List<String> roleNames = Collections.emptyList();
        private List<Long> deptIds = Collections.emptyList();
        private List<Long> postIds = Collections.emptyList();
        private LocalDateTime createdAt;
    }

    /**
     * 员工多因素认证管理操作请求。
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
     * 员工多因素认证豁免请求。
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
     * 员工多因素认证策略和绑定状态响应。
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
     * 商户角色分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoleQueryRequest extends PageRequest {
        private String roleName;
        private String roleCode;
        private Integer status;
        private String createdStartTime;
        private String createdEndTime;
    }

    /**
     * 商户角色新增或编辑请求。
     */
    @Data
    public static class RoleSaveRequest {
        private String roleCode;
        private String roleName;
        private String dataScope;
        private String description;
        private Integer status = 1;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo = 100;
        private List<Long> menuIds;
        private List<Long> permissionIds;
    }

    /**
     * 商户角色展示 DTO，承载角色编码、数据范围、状态和授权摘要。
     */
    @Data
    public static class RoleDTO {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String roleType;
        private String dataScope;
        private String description;
        private Integer status;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 商户角色菜单授权 DTO，返回已授权菜单主键和可展示菜单明细。
     */
    @Data
    public static class RoleMenuAuthDTO {
        private Long roleId;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    /**
     * 商户角色权限授权 DTO，返回已授权权限主键和权限明细。
     */
    @Data
    public static class RolePermissionAuthDTO {
        private Long roleId;
        private List<Long> checkedPermissionIds = Collections.emptyList();
        private List<PermissionDTO> permissions = Collections.emptyList();
    }

    /**
     * 角色菜单和权限授权树保存请求。
     */
    @Data
    public static class RoleGrantTreeSaveRequest {
        private List<Long> menuIds = Collections.emptyList();
        private List<Long> permissionIds = Collections.emptyList();
    }

    /**
     * 商户角色统一授权树 DTO，将目录、菜单、按钮和权限节点按父子关系返回。
     */
    @Data
    public static class RoleGrantTreeDTO {
        private Long roleId;
        private RoleDTO role;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<Long> checkedPermissionIds = Collections.emptyList();
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

    /**
     * 商户授权树节点 DTO，记录节点类型、父节点、选中状态及其子节点。
     */
    @Data
    public static class AuthGrantNodeDTO {
        private String id;
        private Long nodeId;
        private Long menuId;
        private Long permissionId;
        private String nodeType;
        private String name;
        private String code;
        private List<AuthGrantNodeDTO> children = new ArrayList<>();
    }

    /**
     * 商户可分配权限 DTO，承载权限编码、名称、分组和所属菜单信息。
     */
    @Data
    public static class PermissionDTO {
        private Long permissionId;
        private Long menuId;
        private String permissionCode;
        private String permissionName;
        private String permissionType;
        private String resourceMethod;
        private String resourcePath;
    }
}

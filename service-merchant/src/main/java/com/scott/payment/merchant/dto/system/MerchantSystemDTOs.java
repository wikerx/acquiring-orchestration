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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Merchant System  DTO 集合，位于 service-merchant 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class MerchantSystemDTOs {

    private MerchantSystemDTOs() {
    }

    @Data
    public static class IdsRequest {
        private List<Long> ids = Collections.emptyList();
    }

    @Data
    public static class StatusRequest {
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeptQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }

    @Data
    public static class DeptSaveRequest {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long parentId = 0L;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String deptCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String deptName;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long leaderAccountId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo = 0;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class DeptDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long deptId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long parentId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String deptCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String deptName;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long leaderAccountId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private LocalDateTime createdAt;
        /**
         * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updatedAt;
        private List<DeptDTO> children = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PostQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }

    @Data
    public static class PostSaveRequest {
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String postCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String postName;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo = 0;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class PostDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long postId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String postCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String postName;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private LocalDateTime createdAt;
        /**
         * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updatedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }

    @Data
    public static class AccountBaseSaveRequest {
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String loginAccount;
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String password;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String realName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status = 1;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountSaveRequest extends AccountBaseSaveRequest {
        private List<Long> roleIds = Collections.emptyList();
        private List<Long> deptIds = Collections.emptyList();
        private List<Long> postIds = Collections.emptyList();
    }

    @Data
    public static class AccountResetPasswordRequest {
        /**
         * 商户员工新登录密码。接口层只接收明文，服务层必须立即哈希后落库，禁止写入日志。
         */
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        private String password;
    }

    @Data
    public static class AccountDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long accountId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long userId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long merchantUserId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String loginAccount;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String realName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer locked;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
        private List<Long> roleIds = Collections.emptyList();
        private List<String> roleNames = Collections.emptyList();
        private List<Long> deptIds = Collections.emptyList();
        private List<Long> postIds = Collections.emptyList();
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private LocalDateTime createdAt;
    }

    @Data
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
    public static class AccountMfaExemptRequest extends AccountMfaActionRequest {
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime exemptUntil;
    }

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

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoleQueryRequest extends PageRequest {
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleName;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String roleCode;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String createdStartTime;
        /**
         * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String createdEndTime;
    }

    @Data
    public static class RoleSaveRequest {
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String roleCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String dataScope;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String description;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status = 1;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo = 100;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private List<Long> menuIds;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private List<Long> permissionIds;
    }

    @Data
    public static class RoleDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String roleCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleType;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String dataScope;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String description;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private LocalDateTime createdAt;
        /**
         * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updatedAt;
    }

    @Data
    public static class RoleMenuAuthDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    @Data
    public static class RolePermissionAuthDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        private List<Long> checkedPermissionIds = Collections.emptyList();
        private List<PermissionDTO> permissions = Collections.emptyList();
    }

    @Data
    public static class RoleGrantTreeSaveRequest {
        private List<Long> menuIds = Collections.emptyList();
        private List<Long> permissionIds = Collections.emptyList();
    }

    @Data
    public static class RoleGrantTreeDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private RoleDTO role;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<Long> checkedPermissionIds = Collections.emptyList();
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

    @Data
    public static class AuthGrantNodeDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String id;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long nodeId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long menuId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long permissionId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String name;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String code;
        private List<AuthGrantNodeDTO> children = new ArrayList<>();
    }

    @Data
    public static class PermissionDTO {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long permissionId;
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long menuId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String permissionCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String permissionName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String permissionType;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String resourceMethod;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String resourcePath;
    }
}

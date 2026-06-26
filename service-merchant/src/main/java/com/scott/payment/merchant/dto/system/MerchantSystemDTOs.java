package com.scott.payment.merchant.dto.system;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 商户系统基础管理 DTO 集合。
 *
 * <p>当前用于商户端部门、岗位、员工账号、角色和授权接口，所有写操作都由后端登录上下文补齐商户号。</p>
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
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeptQueryRequest extends PageRequest {
        private String keyword;
        private Integer status;
    }

    @Data
    public static class DeptSaveRequest {
        private Long parentId = 0L;
        private String deptCode;
        private String deptName;
        private Long leaderAccountId;
        private String phone;
        private String email;
        private Integer sortNo = 0;
        private Integer status = 1;
        private String remark;
    }

    @Data
    public static class DeptDTO {
        private Long deptId;
        private Long parentId;
        private String deptCode;
        private String deptName;
        private Long leaderAccountId;
        private String phone;
        private String email;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<DeptDTO> children = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PostQueryRequest extends PageRequest {
        private String keyword;
        private Integer status;
    }

    @Data
    public static class PostSaveRequest {
        private String postCode;
        private String postName;
        private Integer sortNo = 0;
        private Integer status = 1;
        private String remark;
    }

    @Data
    public static class PostDTO {
        private Long postId;
        private String postCode;
        private String postName;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccountQueryRequest extends PageRequest {
        private String keyword;
        private Long roleId;
        private Integer status;
    }

    @Data
    public static class AccountBaseSaveRequest {
        private String loginAccount;
        private String password;
        private String realName;
        private String mobile;
        private String email;
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
    public static class AccountDTO {
        private Long accountId;
        private Long userId;
        private Long merchantUserId;
        private String loginAccount;
        private String realName;
        private String mobile;
        private String email;
        private Integer status;
        private Integer locked;
        private LocalDateTime lastLoginAt;
        private List<Long> roleIds = Collections.emptyList();
        private List<String> roleNames = Collections.emptyList();
        private List<Long> deptIds = Collections.emptyList();
        private List<Long> postIds = Collections.emptyList();
        private LocalDateTime createdAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoleQueryRequest extends PageRequest {
        private String roleName;
        private String roleCode;
        private Integer status;
        private String createdStartTime;
        private String createdEndTime;
    }

    @Data
    public static class RoleSaveRequest {
        private String roleCode;
        private String roleName;
        private String dataScope;
        private String description;
        private Integer status = 1;
        private Integer sortNo = 100;
        private List<Long> menuIds;
        private List<Long> permissionIds;
    }

    @Data
    public static class RoleDTO {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String roleType;
        private String dataScope;
        private String description;
        private Integer status;
        private Integer sortNo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class RoleMenuAuthDTO {
        private Long roleId;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    @Data
    public static class RolePermissionAuthDTO {
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
        private Long roleId;
        private RoleDTO role;
        private List<Long> checkedMenuIds = Collections.emptyList();
        private List<Long> checkedPermissionIds = Collections.emptyList();
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

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

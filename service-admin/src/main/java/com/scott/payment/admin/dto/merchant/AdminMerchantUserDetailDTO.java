package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Admin 商户用户详情 DTO，聚合账号、商户、部门、岗位、角色和登录安全状态。
 */
@Data
public class AdminMerchantUserDetailDTO {

    private AdminMerchantUserListDTO account;
    private MerchantSummary merchant;
    private List<DeptSummary> depts = Collections.emptyList();
    private List<PostSummary> posts = Collections.emptyList();
    private List<RoleSummary> roles = Collections.emptyList();
    private List<SysMenuDTO> menus = Collections.emptyList();
    private List<String> permissions = Collections.emptyList();

    /**
     * 商户用户详情中的商户摘要，提供商户号、名称和状态等关联展示字段。
     */
    @Data
    public static class MerchantSummary {
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String merchantName;
        private String merchantShortName;
        private Integer merchantStatus;
    }

    /**
     * 商户用户详情中的部门摘要，提供部门主键、编码和名称。
     */
    @Data
    public static class DeptSummary {
        private Long deptId;
        private String deptName;
        private String deptCode;
    }

    /**
     * 商户用户详情中的岗位摘要，提供岗位主键、编码和名称。
     */
    @Data
    public static class PostSummary {
        private Long postId;
        private String postName;
        private String postCode;
    }

    /**
     * 商户用户详情中的角色摘要，提供角色主键、编码、名称和状态。
     */
    @Data
    public static class RoleSummary {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String roleType;
        private Integer status;
    }
}

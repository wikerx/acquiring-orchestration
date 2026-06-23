package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 管理端商户用户详情响应。
 *
 * <p>聚合账号、商户、组织岗位、角色以及当前账号最终菜单和权限。响应不包含任何登录凭据或密钥。</p>
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

    @Data
    public static class MerchantSummary {
        private String merchantId;
        private String merchantName;
        private String merchantShortName;
        private Integer merchantStatus;
    }

    @Data
    public static class DeptSummary {
        private Long deptId;
        private String deptName;
        private String deptCode;
    }

    @Data
    public static class PostSummary {
        private Long postId;
        private String postName;
        private String postCode;
    }

    @Data
    public static class RoleSummary {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String roleType;
        private Integer status;
    }
}

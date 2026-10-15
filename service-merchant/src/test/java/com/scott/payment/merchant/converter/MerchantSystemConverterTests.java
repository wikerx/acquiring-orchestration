package com.scott.payment.merchant.converter;

import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysMerchantDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantSystemConverterTests {

    @Test
    void mapsDepartmentAndPostWithoutChangingTreeDefaults() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 10, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        SysMerchantDeptDO dept = new SysMerchantDeptDO();
        dept.setId(11L);
        dept.setParentId(1L);
        dept.setDeptCode("OPS");
        dept.setDeptName("Operations");
        dept.setLeaderAccountId(21L);
        dept.setPhone("123456789");
        dept.setEmail("ops@example.com");
        dept.setSortNo(2);
        dept.setStatus(1);
        dept.setRemark("active");
        dept.setCreatedAt(createdAt);
        dept.setUpdatedAt(updatedAt);

        var deptDTO = MerchantSystemConverter.INSTANCE.toDeptDTO(dept);
        assertThat(deptDTO).extracting("deptId", "parentId", "deptCode", "deptName",
                        "leaderAccountId", "phone", "email", "sortNo", "status", "remark",
                        "createdAt", "updatedAt")
                .containsExactly(11L, 1L, "OPS", "Operations", 21L, "123456789",
                        "ops@example.com", 2, 1, "active", createdAt, updatedAt);
        assertThat(deptDTO.getChildren()).isEmpty();

        SysMerchantPostDO post = new SysMerchantPostDO();
        post.setId(31L);
        post.setPostCode("REVIEWER");
        post.setPostName("Reviewer");
        post.setSortNo(3);
        post.setStatus(1);
        post.setRemark("post");
        post.setCreatedAt(createdAt);
        post.setUpdatedAt(updatedAt);

        assertThat(MerchantSystemConverter.INSTANCE.toPostDTO(post))
                .extracting("postId", "postCode", "postName", "sortNo", "status", "remark",
                        "createdAt", "updatedAt")
                .containsExactly(31L, "REVIEWER", "Reviewer", 3, 1, "post", createdAt, updatedAt);
    }

    @Test
    void mapsOnlyExistingRoleAndPermissionResponseFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 10, 0);
        SysRoleDO role = new SysRoleDO();
        role.setId(41L);
        role.setRoleCode("AUDITOR");
        role.setRoleName("Auditor");
        role.setRoleType("CUSTOM");
        role.setDataScope("SELF");
        role.setDescription("Audit access");
        role.setStatus(1);
        role.setSortNo(4);
        role.setCreatedAt(createdAt);
        role.setUpdatedAt(createdAt.plusDays(1));

        assertThat(MerchantSystemConverter.INSTANCE.toRoleDTO(role))
                .extracting("roleId", "roleCode", "roleName", "roleType", "dataScope",
                        "description", "status", "sortNo", "createdAt", "updatedAt")
                .containsExactly(41L, "AUDITOR", "Auditor", "CUSTOM", "SELF",
                        "Audit access", 1, 4, createdAt, createdAt.plusDays(1));

        SysPermissionDO permission = new SysPermissionDO();
        permission.setId(51L);
        permission.setMenuId(61L);
        permission.setPermissionCode("merchant:read");
        permission.setPermissionName("Read merchant");
        permission.setPermissionType("API");
        permission.setResourceMethod("GET");
        permission.setResourcePath("/merchant");
        permission.setStatus(0);

        assertThat(MerchantSystemConverter.INSTANCE.toPermissionDTO(permission))
                .extracting("permissionId", "menuId", "permissionCode", "permissionName",
                        "permissionType", "resourceMethod", "resourcePath")
                .containsExactly(51L, 61L, "merchant:read", "Read merchant", "API", "GET", "/merchant");
    }

    @Test
    void mapsMenuFieldsAndRetainsEmptyChildren() {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(71L);
        menu.setParentId(1L);
        menu.setMenuCode("SETTINGS");
        menu.setMenuName("Settings");
        menu.setMenuType("MENU");
        menu.setRoutePath("/settings");
        menu.setComponentPath("settings/index");
        menu.setPermissionCode("settings:view");
        menu.setIcon("settings");
        menu.setVisible(1);
        menu.setSortNo(5);
        menu.setExternalLink(0);
        menu.setStatus(0);

        var dto = MerchantSystemConverter.INSTANCE.toAuthMenuDTO(menu);
        assertThat(dto).extracting("id", "parentId", "menuCode", "menuName", "menuType",
                        "routePath", "componentPath", "permissionCode", "icon", "visible",
                        "sortNo", "externalLink")
                .containsExactly(71L, 1L, "SETTINGS", "Settings", "MENU", "/settings",
                        "settings/index", "settings:view", "settings", 1, 5, 0);
        assertThat(dto.getChildren()).isEmpty();
        assertThat(MerchantSystemConverter.INSTANCE.toAuthMenuDTO(null)).isNull();
    }
}

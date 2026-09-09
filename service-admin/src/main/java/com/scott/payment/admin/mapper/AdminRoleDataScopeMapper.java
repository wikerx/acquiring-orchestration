package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.system.AdminRoleMerchantScopeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleDataScopeMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 角色商户数据范围只读 Mapper。
 * @status : create
 */
public interface AdminRoleDataScopeMapper {

    /**
     * 查询账号生效角色对应的商户数据范围，用于构建可信管理端访问边界。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param appId 应用主键，用于限定角色、账号和数据范围所属的管理应用
     * @param accountId 登录账号主键，用于查询该账号当前生效的角色授权
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT role_row.data_scope, scope_row.scope_value
            FROM sys_account_role account_role
            JOIN sys_role role_row
              ON role_row.app_id = account_role.app_id
             AND role_row.id = account_role.role_id
             AND role_row.status = 1
             AND role_row.deleted = 0
            LEFT JOIN sys_role_data_scope scope_row
              ON scope_row.app_id = role_row.app_id
             AND scope_row.role_id = role_row.id
             AND scope_row.scope_type = 'MERCHANT'
             AND scope_row.deleted = 0
            WHERE account_role.app_id = #{appId}
              AND account_role.account_id = #{accountId}
              AND account_role.deleted = 0
            ORDER BY role_row.id, scope_row.id
            """)
    List<AdminRoleMerchantScopeDO> selectActiveMerchantScopes(
            @Param("appId") Long appId,
            @Param("accountId") Long accountId);
}

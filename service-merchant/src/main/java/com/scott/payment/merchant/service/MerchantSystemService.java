package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaActionRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaExemptRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaStatusResponse;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountResetPasswordRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.IdsRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleMenuAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RolePermissionAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;

import java.util.List;


/**
 * 商户组织、员工账号、角色和权限管理服务契约。
 */
public interface MerchantSystemService {

    /**
     * 查询当前商户全部有效部门。
     */
    List<DeptDTO> listDepts();

    /**
     * 分页查询当前商户部门。
     */
    PageResult<DeptDTO> pageDepts(DeptQueryRequest request);

    /**
     * 构建部门树，按层级关系组装树形业务视图。
     * @return 当前商户可见的部门树
     */
    List<DeptDTO> deptTree();

    /**
     * 创建部门并校验上级部门归属及部门编码唯一性。
     */
    DeptDTO createDept(DeptSaveRequest request);

    /**
     * 更新部门资料并校验层级关系及部门编码唯一性。
     */
    DeptDTO updateDept(Long id, DeptSaveRequest request);

    /**
     * 软删除当前商户的指定部门。
     */
    void deleteDept(Long id);

    /**
     * 查询当前商户全部有效岗位。
     */
    List<PostDTO> listPosts();

    /**
     * 分页查询当前商户岗位。
     */
    PageResult<PostDTO> pagePosts(PostQueryRequest request);

    /**
     * 创建岗位并校验岗位编码唯一性。
     */
    PostDTO createPost(PostSaveRequest request);

    /**
     * 更新岗位资料并校验岗位编码唯一性。
     */
    PostDTO updatePost(Long id, PostSaveRequest request);

    /**
     * 软删除当前商户的指定岗位。
     */
    void deletePost(Long id);

    /**
     * 查询当前商户全部有效员工账号。
     */
    List<AccountDTO> listAccounts();

    /**
     * 分页查询当前商户员工账号。
     */
    PageResult<AccountDTO> pageAccounts(AccountQueryRequest request);

    /**
     * 创建员工账号及其默认多因素认证和组织授权关系。
     */
    AccountDTO createAccount(AccountSaveRequest request);

    /**
     * 更新员工账号基础资料及角色、部门和岗位关系。
     */
    AccountDTO updateAccount(Long id, AccountSaveRequest request);

    /**
     * 更新员工账号基础资料，不变更组织和角色关系。
     */
    AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request);

    /**
     * 软删除员工账号及其商户归属关系。
     */
    void deleteAccount(Long id);

    /**
     * 管理员重置员工登录密码并记录安全审计信息。
     */
    void resetAccountPassword(Long id, AccountResetPasswordRequest request, String clientIp, String userAgent);

    /**
     * 为当前商户内指定员工重发账号激活或邮箱所有权验证邮件。
     *
     * @param id        员工账号主键
     * @param locale    邮件语言，目前支持 zh-CN 和 en-US
     * @param clientIp  可信客户端 IP，用于独立限流
     * @param userAgent 客户端标识，仅截断后用于审计
     */
    void sendAccountEmailVerification(Long id, String locale, String clientIp, String userAgent);

    /**
     * 启用或停用当前商户的员工账号。
     */
    void updateAccountStatus(Long id, Integer status);

    /**
     * 替换员工账号的角色授权关系。
     */
    void assignAccountRoles(Long id, IdsRequest request);

    /**
     * 替换员工账号的部门归属关系。
     */
    void assignAccountDepts(Long id, IdsRequest request);

    /**
     * 替换员工账号的岗位归属关系。
     */
    void assignAccountPosts(Long id, IdsRequest request);

    /**
     * 强制指定员工账号使用多因素认证，并返回最新状态。
     */
    AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 重置指定员工账号的多因素认证绑定。
     */
    AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 豁免指定员工账号的多因素认证要求并记录原因。
     */
    AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request);

    /**
     * 停用指定员工账号的多因素认证。
     */
    AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 解除指定员工账号的多因素认证锁定状态。
     */
    AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 重新发送指定员工账号的多因素认证绑定邮件。
     */
    AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request);

    /**
     * 查询当前商户全部有效角色。
     */
    List<RoleDTO> listRoles();

    /**
     * 分页查询当前商户角色。
     */
    PageResult<RoleDTO> pageRoles(RoleQueryRequest request);

    /**
     * 查询当前商户的指定角色详情。
     */
    RoleDTO getRole(Long id);

    /**
     * 创建自定义角色并写入初始菜单和权限授权。
     */
    RoleDTO createRole(RoleSaveRequest request);

    /**
     * 更新自定义角色资料及菜单和权限授权。
     */
    RoleDTO updateRole(Long id, RoleSaveRequest request);

    /**
     * 软删除当前商户的非系统内置角色。
     */
    void deleteRole(Long id);

    /**
     * 启用或停用当前商户的非系统内置角色。
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 构建角色授权树空白模板，供新增角色时展示全部可授权节点。
     * @return 未勾选授权项的完整角色授权树
     */
    RoleGrantTreeDTO roleGrantTreeTemplate();

    /**
     * 构建角色授权树，按层级关系组装树形业务视图。
     * @return 带当前角色授权勾选状态的授权树
     */
    RoleGrantTreeDTO roleGrantTree(Long id);

    /**
     * 替换指定角色的菜单和权限授权关系。
     */
    void grantRoleTree(Long id, RoleGrantTreeSaveRequest request);

    /**
     * 查询指定角色已授权的菜单集合。
     * @return 已授权菜单主键及菜单明细
     */
    RoleMenuAuthDTO roleMenus(Long id);

    /**
     * 替换指定角色的菜单授权关系。
     */
    void grantRoleMenus(Long id, IdsRequest request);

    /**
     * 查询指定角色已授权的权限集合。
     * @return 已授权权限主键及权限明细
     */
    RolePermissionAuthDTO rolePermissions(Long id);

    /**
     * 替换指定角色的权限授权关系。
     */
    void grantRolePermissions(Long id, IdsRequest request);

    /**
     * 查询当前商户授权范围内可分配的权限集合。
     * @return 可分配权限列表
     */
    List<PermissionDTO> grantedPermissions();
}

package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.IdsRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleMenuAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RolePermissionAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;

import java.util.List;

/**
 * 商户系统基础管理领域服务。
 */
public interface MerchantSystemService {

    List<DeptDTO> listDepts();

    List<DeptDTO> deptTree();

    DeptDTO createDept(DeptSaveRequest request);

    DeptDTO updateDept(Long id, DeptSaveRequest request);

    void deleteDept(Long id);

    List<PostDTO> listPosts();

    PostDTO createPost(PostSaveRequest request);

    PostDTO updatePost(Long id, PostSaveRequest request);

    void deletePost(Long id);

    List<AccountDTO> listAccounts();

    AccountDTO createAccount(AccountSaveRequest request);

    AccountDTO updateAccount(Long id, AccountSaveRequest request);

    AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request);

    void deleteAccount(Long id);

    void updateAccountStatus(Long id, Integer status);

    void assignAccountRoles(Long id, IdsRequest request);

    void assignAccountDepts(Long id, IdsRequest request);

    void assignAccountPosts(Long id, IdsRequest request);

    List<RoleDTO> listRoles();

    RoleDTO getRole(Long id);

    RoleDTO createRole(RoleSaveRequest request);

    RoleDTO updateRole(Long id, RoleSaveRequest request);

    void deleteRole(Long id);

    void updateRoleStatus(Long id, Integer status);

    RoleGrantTreeDTO roleGrantTreeTemplate();

    RoleGrantTreeDTO roleGrantTree(Long id);

    void grantRoleTree(Long id, RoleGrantTreeSaveRequest request);

    RoleMenuAuthDTO roleMenus(Long id);

    void grantRoleMenus(Long id, IdsRequest request);

    RolePermissionAuthDTO rolePermissions(Long id);

    void grantRolePermissions(Long id, IdsRequest request);

    List<PermissionDTO> grantedPermissions();
}

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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户系统基础管理领域服务。
 * @status : create
 */
public interface MerchantSystemService {

    List<DeptDTO> listDepts();

    PageResult<DeptDTO> pageDepts(DeptQueryRequest request);

    List<DeptDTO> deptTree();

    DeptDTO createDept(DeptSaveRequest request);

    DeptDTO updateDept(Long id, DeptSaveRequest request);

    void deleteDept(Long id);

    List<PostDTO> listPosts();

    PageResult<PostDTO> pagePosts(PostQueryRequest request);

    PostDTO createPost(PostSaveRequest request);

    PostDTO updatePost(Long id, PostSaveRequest request);

    void deletePost(Long id);

    List<AccountDTO> listAccounts();

    PageResult<AccountDTO> pageAccounts(AccountQueryRequest request);

    AccountDTO createAccount(AccountSaveRequest request);

    AccountDTO updateAccount(Long id, AccountSaveRequest request);

    AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request);

    void deleteAccount(Long id);

    void resetAccountPassword(Long id, AccountResetPasswordRequest request);

    void updateAccountStatus(Long id, Integer status);

    void assignAccountRoles(Long id, IdsRequest request);

    void assignAccountDepts(Long id, IdsRequest request);

    void assignAccountPosts(Long id, IdsRequest request);

    AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request);

    AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request);

    AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request);

    AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request);

    AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request);

    AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request);

    List<RoleDTO> listRoles();

    PageResult<RoleDTO> pageRoles(RoleQueryRequest request);

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

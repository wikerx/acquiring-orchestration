package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysMenuCreateRequest;
import com.scott.payment.admin.dto.SysMenuDeleteRequest;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.dto.SysMenuStatusRequest;
import com.scott.payment.admin.dto.SysMenuUpdateRequest;
import com.scott.payment.admin.service.AdminMenuService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台菜单管理应用服务
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Menu Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMenuApplicationService {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminMenuService adminMenuService;

    /**
     * 创建后台菜单应用服务。
     *
     * @param adminMenuService 菜单领域服务
     */
    public AdminMenuApplicationService(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    /**
     * 查询菜单树。
     *
     * @param request 查询条件
     * @return 菜单树
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        return adminMenuService.treeMenus(request);
    }

    /**
     * 查询商户系统菜单树。
     *
     * @param request 查询条件
     * @return 菜单树
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<SysMenuDTO> treeMerchantMenus(SysMenuQueryRequest request) {
        return adminMenuService.treeMenus(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 新增菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        return adminMenuService.createMenu(request);
    }

    /**
     * 新增商户系统菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysMenuDTO createMerchantMenu(SysMenuCreateRequest request) {
        return adminMenuService.createMenu(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 更新菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysMenuDTO updateMenu(SysMenuUpdateRequest request) {
        return adminMenuService.updateMenu(request);
    }

    /**
     * 更新商户系统菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysMenuDTO updateMerchantMenu(SysMenuUpdateRequest request) {
        return adminMenuService.updateMenu(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 更新菜单状态。
     *
     * @param request 状态请求
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void updateStatus(SysMenuStatusRequest request) {
        adminMenuService.updateStatus(request);
    }

    /**
     * 更新商户系统菜单状态。
     *
     * @param request 状态请求
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void updateMerchantMenuStatus(SysMenuStatusRequest request) {
        adminMenuService.updateStatus(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 删除商户系统菜单。
     *
     * @param request 删除请求
     */
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void deleteMerchantMenu(SysMenuDeleteRequest request) {
        adminMenuService.deleteMenu(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }
}

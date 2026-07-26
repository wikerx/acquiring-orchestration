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
@Service
public class AdminMenuApplicationService {

    /**
     * admin Menu Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        return adminMenuService.treeMenus(request);
    }

    /**
     * 查询商户系统菜单树。
     *
     * @param request 查询条件
     * @return 菜单树
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
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        return adminMenuService.createMenu(request);
    }

    /**
     * 新增商户系统菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
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
    public SysMenuDTO updateMenu(SysMenuUpdateRequest request) {
        return adminMenuService.updateMenu(request);
    }

    /**
     * 更新商户系统菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
    public SysMenuDTO updateMerchantMenu(SysMenuUpdateRequest request) {
        return adminMenuService.updateMenu(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 更新菜单状态。
     *
     * @param request 状态请求
     */
    public void updateStatus(SysMenuStatusRequest request) {
        adminMenuService.updateStatus(request);
    }

    /**
     * 更新商户系统菜单状态。
     *
     * @param request 状态请求
     */
    public void updateMerchantMenuStatus(SysMenuStatusRequest request) {
        adminMenuService.updateStatus(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 删除商户系统菜单。
     *
     * @param request 删除请求
     */
    public void deleteMerchantMenu(SysMenuDeleteRequest request) {
        adminMenuService.deleteMenu(com.scott.payment.component.db.auth.constant.AuthConstants.APP_MERCHANT, request);
    }
}

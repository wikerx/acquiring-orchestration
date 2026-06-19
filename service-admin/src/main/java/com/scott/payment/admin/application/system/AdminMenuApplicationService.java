package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysMenuCreateRequest;
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
     * 新增菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        return adminMenuService.createMenu(request);
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
     * 更新菜单状态。
     *
     * @param request 状态请求
     */
    public void updateStatus(SysMenuStatusRequest request) {
        adminMenuService.updateStatus(request);
    }
}

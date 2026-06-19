package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysMenuCreateRequest;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.dto.SysMenuStatusRequest;
import com.scott.payment.admin.dto.SysMenuUpdateRequest;

import java.util.List;

/**
 * 后台菜单领域服务。
 */
public interface AdminMenuService {

    /**
     * 查询后台菜单树。
     *
     * @param request 查询条件
     * @return 菜单树
     */
    List<SysMenuDTO> treeMenus(SysMenuQueryRequest request);

    /**
     * 新增后台菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
    SysMenuDTO createMenu(SysMenuCreateRequest request);

    /**
     * 编辑后台菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
    SysMenuDTO updateMenu(SysMenuUpdateRequest request);

    /**
     * 更新后台菜单状态。
     *
     * @param request 状态请求
     */
    void updateStatus(SysMenuStatusRequest request);
}

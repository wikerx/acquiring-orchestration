package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuService
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单服务
 * @status : create
 */
public interface AdminMenuService {

    List<SysMenuDTO> treeMenus(SysMenuQueryRequest request);
}

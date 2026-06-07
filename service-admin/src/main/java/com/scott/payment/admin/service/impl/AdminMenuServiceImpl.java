package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.service.AdminMenuService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuServiceImpl
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单服务实现
 * @status : create
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final long NOT_DELETED = 0L;

    private final SysAppMapper sysAppMapper;
    private final SysMenuMapper sysMenuMapper;

    public AdminMenuServiceImpl(SysAppMapper sysAppMapper, SysMenuMapper sysMenuMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        SysMenuQueryRequest query = request == null ? new SysMenuQueryRequest() : request;
        SysAppDO app = getAdminApp();
        List<SysMenuDO> menus = sysMenuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, app.getId())
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .like(StringUtils.hasText(query.getMenuName()), SysMenuDO::getMenuName, query.getMenuName())
                        .eq(StringUtils.hasText(query.getMenuType()), SysMenuDO::getMenuType, query.getMenuType())
                        .eq(SysMenuDO::getStatus, query.getStatus() == null ? AuthConstants.ENABLED : query.getStatus())
                        .eq(SysMenuDO::getVisible, query.getVisible() == null ? AuthConstants.ENABLED : query.getVisible())
                        .orderByAsc(SysMenuDO::getSortNo)
                        .orderByAsc(SysMenuDO::getId)
        );
        return buildTree(menus);
    }

    private SysAppDO getAdminApp() {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                        .eq(SysAppDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "ADMIN app not found");
        }
        return app;
    }

    private List<SysMenuDTO> buildTree(List<SysMenuDO> menus) {
        Map<Long, SysMenuDTO> menuMap = new LinkedHashMap<>();
        menus.forEach(menu -> menuMap.put(menu.getId(), toDTO(menu)));
        List<SysMenuDTO> roots = new ArrayList<>();
        menuMap.values().forEach(menu -> {
            SysMenuDTO parent = menuMap.get(menu.getParentId());
            if (parent == null || ROOT_PARENT_ID == menu.getParentId()) {
                roots.add(menu);
                return;
            }
            parent.getChildren().add(menu);
        });
        return roots;
    }

    private SysMenuDTO toDTO(SysMenuDO menu) {
        SysMenuDTO dto = new SysMenuDTO();
        dto.setMenuId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setMenuName(menu.getMenuName());
        dto.setMenuType(menu.getMenuType());
        dto.setRoutePath(menu.getRoutePath());
        dto.setComponentPath(menu.getComponentPath());
        dto.setPermissionCode(menu.getPermissionCode());
        dto.setIcon(menu.getIcon());
        dto.setRedirect(menu.getRedirect());
        dto.setVisible(menu.getVisible());
        dto.setKeepAlive(menu.getKeepAlive());
        dto.setExternalLink(menu.getExternalLink());
        dto.setSortNo(menu.getSortNo());
        dto.setStatus(menu.getStatus());
        return dto;
    }
}

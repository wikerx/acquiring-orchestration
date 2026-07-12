package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MenuConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 后台菜单对象转换器，位于 service-admin 转换层；负责菜单实体到后台菜单 DTO 的字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MenuConverter {

    /**
     * 菜单实体转 DTO。
     *
     * @param menu 菜单实体
     * @return 菜单 DTO
     */
    @Mapping(source = "id", target = "menuId")
    SysMenuDTO toDTO(SysMenuDO menu);
}

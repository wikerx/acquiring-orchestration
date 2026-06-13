package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MenuConverter
 * @date : 2026-06-12 22:00
 * @email : scott_x@163.com
 * @description : 菜单实体与 DTO 转换器
 * @status : create
 */
@Mapper
public interface MenuConverter {

    MenuConverter INSTANCE = Mappers.getMapper(MenuConverter.class);

    /**
     * 菜单实体转 DTO。
     *
     * @param menu 菜单实体
     * @return 菜单 DTO
     */
    @Mapping(source = "id", target = "menuId")
    SysMenuDTO toDTO(SysMenuDO menu);
}

package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import org.mapstruct.Mapper;

/**
 * 登录日志对象转换器，位于 service-admin 转换层；负责登录日志实体到后台响应 DTO 的普通字段映射。
 */
@Mapper(componentModel = "spring")
public interface LoginLogConverter {

    /**
     * 登录日志实体转响应 DTO。
     *
     * @param entity 登录日志实体
     * @return 登录日志 DTO
     */
    SysLoginLogDTO toDTO(SysLoginLogDO entity);
}

package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 登录日志对象转换器。
 */
@Mapper
public interface LoginLogConverter {

    LoginLogConverter INSTANCE = Mappers.getMapper(LoginLogConverter.class);

    /**
     * 登录日志实体转响应 DTO。
     *
     * @param entity 登录日志实体
     * @return 登录日志 DTO
     */
    SysLoginLogDTO toDTO(SysLoginLogDO entity);
}

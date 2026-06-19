package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginLogConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 登录日志对象转换器
 * @status : create
 *
 * <p>负责登录日志实体与后台响应 DTO 之间的转换，避免应用层直接暴露持久化对象。</p>
 */

@Mapper
public interface LoginLogConverter {

    /**
     * 转换器单例。
     */
    LoginLogConverter INSTANCE = Mappers.getMapper(LoginLogConverter.class);

    /**
     * 登录日志实体转响应 DTO。
     *
     * @param entity 登录日志实体
     * @return 登录日志 DTO
     */
    SysLoginLogDTO toDTO(SysLoginLogDO entity);
}

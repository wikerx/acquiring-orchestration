package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.entity.SysConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ConfigConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统参数配置对象转换器
 * @status : create
 *
 * <p>负责系统参数实体与后台响应 DTO 之间的转换，避免应用层直接暴露持久化对象。</p>
 */

@Mapper
public interface ConfigConverter {

    /**
     * 转换器单例。
     */
    ConfigConverter INSTANCE = Mappers.getMapper(ConfigConverter.class);

    /**
     * 配置实体转响应 DTO。
     *
     * @param entity 配置实体
     * @return 配置 DTO
     */
    SysConfigDTO toDTO(SysConfigDO entity);
}

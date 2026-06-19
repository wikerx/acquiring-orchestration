package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.entity.SysConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 系统参数配置对象转换器。
 */
@Mapper
public interface ConfigConverter {

    ConfigConverter INSTANCE = Mappers.getMapper(ConfigConverter.class);

    /**
     * 配置实体转响应 DTO。
     *
     * @param entity 配置实体
     * @return 配置 DTO
     */
    SysConfigDTO toDTO(SysConfigDO entity);
}

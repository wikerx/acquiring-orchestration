package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.export.SysConfigExportRow;
import com.scott.payment.admin.entity.SysConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ConfigConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统参数配置对象转换器，位于 service-admin 转换层；只负责配置 DO、DTO 与导出行之间的普通字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface ConfigConverter {

    /**
     * 配置实体转响应 DTO。
     *
     * @param entity 配置实体
     * @return 配置 DTO
     */
    SysConfigDTO toDTO(SysConfigDO entity);

    /**
     * 配置 DTO 转导出行对象。
     *
     * @param dto 配置 DTO
     * @return 导出行对象
     */
    @Mapping(target = "status", ignore = true)
    SysConfigExportRow toExportRow(SysConfigDTO dto);
}

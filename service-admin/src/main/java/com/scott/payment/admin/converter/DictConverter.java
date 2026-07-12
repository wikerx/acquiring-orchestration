package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.export.SysDictDataExportRow;
import com.scott.payment.admin.dto.export.SysDictTypeExportRow;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 数据字典对象转换器，位于 service-admin 转换层；负责字典 DO、DTO 与导出行之间的普通字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface DictConverter {

    /**
     * 字典类型实体转响应 DTO。
     *
     * @param entity 字典类型实体
     * @return 字典类型 DTO
     */
    SysDictTypeDTO toTypeDTO(SysDictTypeDO entity);

    /**
     * 字典类型 DTO 转导出行对象。
     *
     * @param dto 字典类型 DTO
     * @return 导出行对象
     */
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "systemBuiltin", ignore = true)
    SysDictTypeExportRow toTypeExportRow(SysDictTypeDTO dto);

    /**
     * 字典数据实体转响应 DTO。
     *
     * @param entity 字典数据实体
     * @return 字典数据 DTO
     */
    SysDictDataDTO toDataDTO(SysDictDataDO entity);

    /**
     * 字典数据 DTO 转导出行对象。
     *
     * @param dto 字典数据 DTO
     * @return 导出行对象
     */
    @Mapping(target = "sortNo", source = "dictSort")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "defaultFlag", ignore = true)
    SysDictDataExportRow toDataExportRow(SysDictDataDTO dto);
}

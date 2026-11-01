package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.export.SysDictDataExportRow;
import com.scott.payment.admin.dto.export.SysDictTypeExportRow;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 数据字典对象转换器，位于 service-admin 转换层；负责字典 DO、DTO 与导出行之间的普通字段映射。
 */
@Mapper
public interface DictConverter {

    DictConverter INSTANCE = Mappers.getMapper(DictConverter.class);

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
     * 将字典数据实体转换为管理端响应 DTO。
     *
     * <p>公共字典快照不包含管理备注和审计时间，这些字段在下拉查询响应中保持为空。</p>
     *
     * @param snapshot 公共字典选项快照
     * @return 字典数据 DTO
     */
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysDictDataDTO toDataDTO(DictionaryOptionSnapshot snapshot);

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

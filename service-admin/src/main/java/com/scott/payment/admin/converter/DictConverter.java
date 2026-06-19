package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 数据字典对象转换器。
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
     * 字典数据实体转响应 DTO。
     *
     * @param entity 字典数据实体
     * @return 字典数据 DTO
     */
    SysDictDataDTO toDataDTO(SysDictDataDO entity);
}

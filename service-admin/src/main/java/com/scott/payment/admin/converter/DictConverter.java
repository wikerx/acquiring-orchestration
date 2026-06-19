package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 数据字典对象转换器
 * @status : create
 *
 * <p>负责字典类型、字典项实体与后台响应 DTO 之间的转换，避免应用层直接暴露持久化对象。</p>
 */

@Mapper
public interface DictConverter {

    /**
     * 转换器单例。
     */
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

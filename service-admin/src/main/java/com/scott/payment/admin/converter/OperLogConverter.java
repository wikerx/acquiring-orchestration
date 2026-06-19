package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.entity.SysOperLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 操作日志对象转换器。
 */
@Mapper
public interface OperLogConverter {

    OperLogConverter INSTANCE = Mappers.getMapper(OperLogConverter.class);

    /**
     * 操作日志实体转响应 DTO。
     *
     * @param entity 操作日志实体
     * @return 操作日志 DTO
     */
    SysOperLogDTO toDTO(SysOperLogDO entity);
}

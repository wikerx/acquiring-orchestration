package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.entity.SysOperLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperLogConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 操作日志对象转换器
 * @status : create
 *
 * <p>负责操作日志实体与后台响应 DTO 之间的转换，避免应用层直接暴露持久化对象。</p>
 */

@Mapper
public interface OperLogConverter {

    /**
     * 转换器单例。
     */
    OperLogConverter INSTANCE = Mappers.getMapper(OperLogConverter.class);

    /**
     * 操作日志实体转响应 DTO。
     *
     * @param entity 操作日志实体
     * @return 操作日志 DTO
     */
    SysOperLogDTO toDTO(SysOperLogDO entity);
}

package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.entity.SysOperLogDO;
import org.mapstruct.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperLogConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 操作日志对象转换器，位于 service-admin 转换层；负责操作日志实体到后台响应 DTO 的普通字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface OperLogConverter {

    /**
     * 操作日志实体转响应 DTO。
     *
     * @param entity 操作日志实体
     * @return 操作日志 DTO
     */
    SysOperLogDTO toDTO(SysOperLogDO entity);
}

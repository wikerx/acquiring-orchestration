package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UserExportConverter
 * @date : 2026-06-19 23:58
 * @email : scott_x@163.com
 * @description : 后台用户导出对象转换器
 * @status : create
 */
@Mapper
public interface UserExportConverter {

    /**
     * 转换器单例。
     */
    UserExportConverter INSTANCE = Mappers.getMapper(UserExportConverter.class);

    /**
     * 用户 DTO 转导出行对象。
     *
     * @param dto 用户 DTO
     * @return 导出行对象
     */
    SysUserAccountExportRow toExportRow(SysUserAccountDTO dto);
}

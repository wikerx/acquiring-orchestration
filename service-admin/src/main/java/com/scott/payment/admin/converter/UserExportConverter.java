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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付User Export Converter，位于 service-admin 的对象转换层，用于定义调用契约和职责边界。
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
    @Mapping(target = "postNamesText", ignore = true)
    SysUserAccountExportRow toExportRow(SysUserAccountDTO dto);
}

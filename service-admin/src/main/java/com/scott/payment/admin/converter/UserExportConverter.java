package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UserExportConverter
 * @date : 2026-06-20 01:15
 * @email : scott_x@163.com
 * @description : UserExportConverter 转换组件，用于在实体、DTO、VO 和外部协议对象之间转换字段，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface UserExportConverter {

    /**
     * 用户 DTO 转导出行对象。
     *
     * @param dto 用户 DTO
     * @return 导出行对象
     */
    @Mapping(target = "postNamesText", ignore = true)
    @Mapping(target = "roleNamesText", ignore = true)
    SysUserAccountExportRow toExportRow(SysUserAccountDTO dto);
}

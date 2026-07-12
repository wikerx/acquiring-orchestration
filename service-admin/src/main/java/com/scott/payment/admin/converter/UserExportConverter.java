package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UserExportConverter
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 后台用户导出转换器，位于 service-admin 转换层；负责用户 DTO 到 Excel 行对象的普通字段映射，导出展示文案由应用层补充。
 * @status : create
 */
@Mapper(componentModel = "spring")
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

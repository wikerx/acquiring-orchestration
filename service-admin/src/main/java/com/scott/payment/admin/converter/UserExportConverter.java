package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


/**
 * 管理端用户导出模型转换器。
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

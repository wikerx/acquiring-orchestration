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
 * @description : User Export Converter 转换组件，位于 运营后台服务，在接口模型、领域对象、数据库记录或消息载荷之间复制并规范化字段。
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

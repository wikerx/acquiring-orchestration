package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 后台用户导出对象转换器。
 *
 * <p>只负责 DTO 与导出行之间的字段映射，语言相关展示文案由应用服务按当前 locale 填充。</p>
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

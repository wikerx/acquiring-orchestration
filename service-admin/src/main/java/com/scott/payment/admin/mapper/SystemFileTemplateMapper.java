package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 系统文件模板定义数据访问组件。 */
@Mapper
public interface SystemFileTemplateMapper extends BaseMapper<FileTemplateDO> {

    @Select("SELECT * FROM sys_file_template WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    FileTemplateDO selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE sys_file_template SET owner_system=#{ownerSystem}, module_code=#{moduleCode}, "
            + "name_zh=#{nameZh}, name_en=#{nameEn}, admin_permission_code=#{adminPermissionCode}, "
            + "merchant_permission_code=#{merchantPermissionCode}, "
            + "status=#{status}, remark=#{remark}, version=version+1, update_by=#{operator}, "
            + "update_time=#{now} WHERE id=#{id} AND version=#{expectedVersion} AND deleted=0")
    int updateMetadataCas(@Param("id") Long id,
                          @Param("ownerSystem") String ownerSystem,
                          @Param("moduleCode") String moduleCode,
                          @Param("nameZh") String nameZh,
                          @Param("nameEn") String nameEn,
                          @Param("adminPermissionCode") String adminPermissionCode,
                          @Param("merchantPermissionCode") String merchantPermissionCode,
                          @Param("status") Integer status,
                          @Param("remark") String remark,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("operator") String operator,
                          @Param("now") LocalDateTime now);

    @Update("UPDATE sys_file_template SET current_version_id=#{versionId}, last_operation_request_id=#{requestId}, version=version+1, "
            + "update_by=#{operator}, update_time=#{now} WHERE id=#{id} AND version=#{expectedVersion} "
            + "AND status=1 AND deleted=0")
    int pointCurrentVersionCas(@Param("id") Long id,
                               @Param("versionId") Long versionId,
                               @Param("requestId") String requestId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("operator") String operator,
                               @Param("now") LocalDateTime now);
}

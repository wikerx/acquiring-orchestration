package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.admin.dto.export.SysDeptExportRow;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DeptConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 部门对象转换器，位于 service-admin 转换层；负责部门实体、树形 DTO 和导出行之间的字段映射与树组装。
 * @status : create
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeptConverter {

    /**
     * 部门实体转树形 DTO。
     *
     * @param dept 部门实体
     * @return 部门树节点
     */
    SysDeptDTO toDTO(SysDeptDO dept);

    /**
     * 部门实体转导出行对象。
     *
     * @param dept 部门实体
     * @return 导出行对象
     */
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "status", ignore = true)
    SysDeptExportRow toExportRow(SysDeptDO dept);

    /**
     * 批量转换并构建树形结构。
     *
     * @param all 全部部门实体列表
     * @return 树形部门 DTO 列表
     */
    default List<SysDeptDTO> buildTree(List<SysDeptDO> all) {
        Map<Long, SysDeptDTO> map = new LinkedHashMap<>();
        for (SysDeptDO d : all) {
            map.put(d.getId(), toDTO(d));
        }
        List<SysDeptDTO> roots = new ArrayList<>();
        for (SysDeptDTO d : map.values()) {
            if (d.getParentId() == null || d.getParentId() == 0) {
                roots.add(d);
            } else {
                SysDeptDTO parent = map.get(d.getParentId());
                if (parent != null) {
                    parent.getChildren().add(d);
                }
            }
        }
        return roots;
    }
}

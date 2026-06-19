package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门实体与 DTO 转换器。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeptConverter {

    DeptConverter INSTANCE = Mappers.getMapper(DeptConverter.class);

    /**
     * 部门实体转树形 DTO。
     *
     * @param dept 部门实体
     * @return 部门树节点
     */
    SysDeptDTO toDTO(SysDeptDO dept);

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

package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.system.PostDTOs.Response;
import com.scott.payment.admin.dto.system.PostDTOs.SaveRequest;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 系统岗位接口模型转换器。
 *
 * <p>负责管理端请求、响应与持久化实体之间的普通字段映射。</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostConverter {

    /**
     * 将岗位保存请求转换为持久化实体。
     *
     * @param request 保存请求
     * @return 待持久化实体
     */
    SysPostDO toEntity(SaveRequest request);

    /**
     * 将岗位持久化实体转换为管理端响应。
     *
     * @param entity 持久化实体
     * @return 管理端响应
     */
    Response toResponse(SysPostDO entity);

    /**
     * 批量转换岗位持久化实体。
     *
     * @param entities 持久化实体列表
     * @return 管理端响应列表
     */
    List<Response> toResponses(List<SysPostDO> entities);
}

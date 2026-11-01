package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.base.IsoCountryDTOs.Response;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.SaveRequest;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * ISO 国家和地区接口模型转换器。
 *
 * <p>负责管理端请求、响应与持久化实体之间的普通字段映射。</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IsoCountryConverter {

    /**
     * 将管理端保存请求转换为国家和地区持久化实体。
     *
     * @param request 保存请求
     * @return 待持久化实体
     */
    IsoCountryDO toEntity(SaveRequest request);

    /**
     * 将国家和地区持久化实体转换为管理端响应。
     *
     * @param entity 持久化实体
     * @return 管理端响应
     */
    Response toResponse(IsoCountryDO entity);

    /**
     * 批量转换国家和地区持久化实体。
     *
     * @param entities 持久化实体列表
     * @return 管理端响应列表
     */
    List<Response> toResponses(List<IsoCountryDO> entities);
}

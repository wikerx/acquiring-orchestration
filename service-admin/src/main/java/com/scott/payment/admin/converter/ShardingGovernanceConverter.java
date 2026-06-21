package com.scott.payment.admin.converter;

import com.scott.payment.admin.client.job.dto.ShardingTablePreCreateRemoteRequest;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateRequest;
import com.scott.payment.admin.entity.SysShardingPhysicalTableDO;
import com.scott.payment.admin.entity.SysShardingTableCreateLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 分表治理对象转换器。
 *
 * <p>负责治理表实体、后台响应 DTO 和 service-job 远程请求之间的转换。</p>
 */
@Mapper
public interface ShardingGovernanceConverter {

    /**
     * 转换器单例。
     */
    ShardingGovernanceConverter INSTANCE = Mappers.getMapper(ShardingGovernanceConverter.class);

    /**
     * 物理表登记实体转后台响应。
     *
     * @param entity 物理表登记实体
     * @return 物理表响应
     */
    ShardingPhysicalTableResponse toPhysicalTableResponse(SysShardingPhysicalTableDO entity);

    /**
     * 建表日志实体转后台响应。
     *
     * @param entity 建表日志实体
     * @return 建表日志响应
     */
    ShardingTableCreateLogResponse toCreateLogResponse(SysShardingTableCreateLogDO entity);

    /**
     * 管理后台建表请求转 service-job 内部请求。
     *
     * @param request      管理后台请求
     * @param dryRun       是否只预演
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return service-job 内部请求
     */
    @Mapping(target = "dryRun", source = "dryRun")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "operatorName", source = "operatorName")
    ShardingTablePreCreateRemoteRequest toRemoteRequest(ShardingTableCreateRequest request,
                                                        Boolean dryRun,
                                                        String operatorId,
                                                        String operatorName);
}

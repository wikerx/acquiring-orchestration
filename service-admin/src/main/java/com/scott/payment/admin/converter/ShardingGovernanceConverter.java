package com.scott.payment.admin.converter;

import com.scott.payment.admin.client.job.dto.ShardingTablePreCreateRemoteRequest;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateRequest;
import com.scott.payment.admin.entity.SysShardingPhysicalTableDO;
import com.scott.payment.admin.entity.SysShardingTableCreateLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingGovernanceConverter
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Governance Converter 转换组件，位于 运营后台服务，在接口模型、领域对象、数据库记录或消息载荷之间复制并规范化字段。
 * @status : create
 */
public interface ShardingGovernanceConverter {

    /**
     * 物理表登记实体转后台响应。
     *
     * @param entity 物理表登记实体
     * @return 物理表响应
     */
    @Mapping(target = "ruleVersion", ignore = true)
    @Mapping(target = "ruleChecksumPrefix", ignore = true)
    @Mapping(target = "nodeRegistered", ignore = true)
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

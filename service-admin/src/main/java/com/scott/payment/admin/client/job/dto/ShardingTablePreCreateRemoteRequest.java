package com.scott.payment.admin.client.job.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateRemoteRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Remote 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingTablePreCreateRemoteRequest {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean dryRun;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean includeCurrentQuarter = Boolean.TRUE;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean includeNextQuarter = Boolean.TRUE;

    private List<String> logicalTables = new ArrayList<>();

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean compareSchemaIfExists = Boolean.TRUE;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String operatorId;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String operatorName;
}

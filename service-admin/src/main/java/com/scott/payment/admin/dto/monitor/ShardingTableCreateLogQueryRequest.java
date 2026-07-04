package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableCreateLogQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Sharding Table Create Log Query 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShardingTableCreateLogQueryRequest extends PageRequest {

    /**
     * 监控治理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String batchNo;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String triggerType;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer dryRun;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String runStatus;
}

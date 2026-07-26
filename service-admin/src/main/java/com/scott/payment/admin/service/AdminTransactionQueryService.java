package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.core.model.PageResult;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionQueryService
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 管理后台交易只读查询服务，位于 service-admin 服务层，直接读取交易备库分表。
 * @status : create
 */
public interface AdminTransactionQueryService {

    /**
     * 执行 page Orders 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 执行 page Operations 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    /**
     * 执行 search Operations 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 执行 detail 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    TransactionDetailResponse detail(String transactionId);

    /**
     * 执行 page Channel Logs 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    /**
     * 执行 page Channel Callbacks 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 执行 page Merchant Notifications 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);
}

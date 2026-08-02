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

import java.time.LocalDateTime;
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
     * 查询交易主单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 查询交易动作，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    /**
     * 查询交易动作，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 规范化detail，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    TransactionDetailResponse detail(String transactionId,
                                     LocalDateTime transactionDateTime,
                                     LocalDateTime rootTransactionDateTime);

    /**
     * 查询渠道日志，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    /**
     * 查询渠道callbacks，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 查询商户通知任务，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);
}

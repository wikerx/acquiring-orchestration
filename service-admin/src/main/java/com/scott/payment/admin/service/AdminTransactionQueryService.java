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
     * 查询交易主单；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 查询交易动作；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    /**
     * 查询交易动作；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 查询指定业务单据详情，并执行调用方数据范围校验。
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param rootTransactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 包含主单、动作、金额、支付工具和状态时间线的交易详情
     */
    TransactionDetailResponse detail(String transactionId,
                                     LocalDateTime transactionDateTime,
                                     LocalDateTime rootTransactionDateTime);

    /**
     * 查询渠道日志；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    /**
     * 查询{@code pageChannelCallbacks}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 查询商户通知任务；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);

    /**
     * 查询单个商户通知任务及其全部投递尝试日志。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 页面列表返回的真实交易分片时间
     * @return 包含 notification 和 deliveryLogs 的详情视图
     */
    Map<String, Object> merchantNotificationDetail(String notifyId,
                                                   LocalDateTime transactionDateTime);

    /**
     * 判断精确交易分片中是否存在允许人工重发的终态商户通知。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 页面查询得到的真实交易分片时间
     * @return true 表示交易为成功或失败终态，且通知任务当前允许人工重发
     */
    boolean existsRetryableTerminalMerchantNotification(String transactionId,
                                                         LocalDateTime transactionDateTime);
}

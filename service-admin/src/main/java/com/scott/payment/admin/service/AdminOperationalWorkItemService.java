package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.ActionRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.AssigneeOption;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemDetail;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemHistory;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemSummary;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * 统一运营待办领域边界，隔离来源事实查询与运营元数据命令。
 */
public interface AdminOperationalWorkItemService {

    /**
     * 查询当前账号在商户数据范围内可访问的待办分页及全量汇总。
     *
     * @param request 查询条件
     * @param dataScope 当前账号的商户数据范围
     * @param currentAccountId 当前管理账号标识
     * @return 待办分页及汇总
     */
    SearchResponse search(SearchRequest request, AdminMerchantDataScope dataScope, Long currentAccountId);

    /**
     * 查询当前账号可导出的完整待办摘要，仍受查询预算和商户数据范围约束。
     *
     * @param request 查询条件
     * @param dataScope 当前账号的商户数据范围
     * @param currentAccountId 当前管理账号标识
     * @return 可导出的待办摘要
     */
    List<WorkItemSummary> listForExport(SearchRequest request, AdminMerchantDataScope dataScope,
                                        Long currentAccountId);

    /**
     * 按冻结查询和冻结商户范围读取一页异步导出数据。
     *
     * @param request 查询条件和分页参数
     * @param dataScope 创建任务时冻结的商户数据范围
     * @param currentAccountId 创建任务的管理账号标识
     * @return 权威待办摘要分页
     */
    PageResult<WorkItemSummary> pageForExport(SearchRequest request, AdminMerchantDataScope dataScope,
                                              Long currentAccountId);

    /**
     * 查询指定待办的实时来源摘要和完整运营轨迹。
     *
     * @param workItemNo 待办编号
     * @param dataScope 当前账号的商户数据范围
     * @return 待办详情
     */
    WorkItemDetail detail(String workItemNo, AdminMerchantDataScope dataScope);

    /**
     * 分页查询指定待办的处理轨迹，访问权限与待办详情保持一致。
     *
     * @param workItemNo 待办编号
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @param dataScope 当前账号的商户数据范围
     * @return 按操作时间倒序排列的处理轨迹
     */
    PageResult<WorkItemHistory> searchHistory(String workItemNo, Integer pageNo, Integer pageSize,
                                              AdminMerchantDataScope dataScope);

    /**
     * 校验当前账号是否仍可访问指定待办对应的实时来源事实。
     *
     * @param workItemNo 待办编号
     * @param dataScope 当前账号的商户数据范围
     */
    void requireAccess(String workItemNo, AdminMerchantDataScope dataScope);

    /**
     * 由当前操作人认领未分派的活动待办。
     *
     * @param workItemNo 待办编号
     * @param request 带请求幂等键和预期版本的操作命令
     * @param operatorAccountId 操作人账号标识
     * @param operatorAccountName 操作人名称
     * @param dataScope 当前账号的商户数据范围
     * @return 更新后的待办详情
     */
    WorkItemDetail claim(String workItemNo, ActionRequest request, Long operatorAccountId,
                         String operatorAccountName, AdminMerchantDataScope dataScope);

    /**
     * 将活动待办分派给同一管理应用中的有效账号。
     *
     * @param workItemNo 待办编号
     * @param request 带目标账号、请求幂等键和预期版本的操作命令
     * @param operatorAccountId 操作人账号标识
     * @param operatorAccountName 操作人名称
     * @param appId 当前管理应用标识
     * @param dataScope 当前账号的商户数据范围
     * @return 更新后的待办详情
     */
    WorkItemDetail assign(String workItemNo, ActionRequest request, Long operatorAccountId,
                          String operatorAccountName, Long appId, AdminMerchantDataScope dataScope);

    /**
     * 将已分派活动待办转交给同一管理应用中的其他有效账号。
     *
     * @param workItemNo 待办编号
     * @param request 带目标账号、请求幂等键和预期版本的操作命令
     * @param operatorAccountId 操作人账号标识
     * @param operatorAccountName 操作人名称
     * @param appId 当前管理应用标识
     * @param dataScope 当前账号的商户数据范围
     * @return 更新后的待办详情
     */
    WorkItemDetail transfer(String workItemNo, ActionRequest request, Long operatorAccountId,
                            String operatorAccountName, Long appId, AdminMerchantDataScope dataScope);

    /**
     * 人工完成活动待办并保留处理结论，不修改来源业务状态。
     *
     * @param workItemNo 待办编号
     * @param request 带完成原因、请求幂等键和预期版本的操作命令
     * @param operatorAccountId 操作人账号标识
     * @param operatorAccountName 操作人名称
     * @param dataScope 当前账号的商户数据范围
     * @return 更新后的待办详情
     */
    WorkItemDetail complete(String workItemNo, ActionRequest request, Long operatorAccountId,
                            String operatorAccountName, AdminMerchantDataScope dataScope);

    /**
     * 为待办追加处理备注，不覆盖既有轨迹。
     *
     * @param workItemNo 待办编号
     * @param request 带备注、请求幂等键和预期版本的操作命令
     * @param operatorAccountId 操作人账号标识
     * @param operatorAccountName 操作人名称
     * @param dataScope 当前账号的商户数据范围
     * @return 更新后的待办详情
     */
    WorkItemDetail remark(String workItemNo, ActionRequest request, Long operatorAccountId,
                          String operatorAccountName, AdminMerchantDataScope dataScope);

    /**
     * 查询同一管理应用中可接收待办的激活账号。
     *
     * @param appId 当前管理应用标识
     * @param keyword 登录账号或人员名称关键字
     * @return 可分派账号选项
     */
    List<AssigneeOption> listAssignees(Long appId, String keyword);

    /**
     * 按请求键幂等同步六类来源事实及其当前状态投影。
     *
     * @param request 同步请求
     * @return 本轮来源扫描和状态变更统计
     */
    SyncResponse synchronize(SyncRequest request);
}

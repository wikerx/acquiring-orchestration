package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingDataTemplate
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 分表数据访问统一入口，位于 component-db 分表基础层，统一完成分表上下文校验、物理表解析和数据源意图约束。
 * @status : create
 */
@Component
public class ShardingDataTemplate {

    /**
     * 分表物理表范围解析器。
     */
    private final ShardingTableRangeResolver tableRangeResolver;

    /**
     * 创建分表数据访问统一入口。
     *
     * @param tableRangeResolver 分表物理表范围解析器
     */
    public ShardingDataTemplate(ShardingTableRangeResolver tableRangeResolver) {
        this.tableRangeResolver = tableRangeResolver;
    }

    /**
     * 按分表键解析单张物理表并执行查询。
     *
     * @param context 单表查询上下文
     * @param callback 查询回调
     * @return 查询结果
     */
    public <T> T queryOne(ShardingSingleTableContext context, ShardingPhysicalTableCallback<T> callback) {
        validateDataSource(context == null ? null : context.dataSource(), false);
        return executeSingle(context, callback);
    }

    /**
     * 按时间范围解析多张物理表并执行查询。
     *
     * @param context 范围查询上下文
     * @param callback 查询回调
     * @return 查询结果
     */
    public <T> T queryRange(ShardingRangeTableContext context, ShardingPhysicalTablesCallback<T> callback) {
        validateRangeContext(context);
        if (callback == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding callback is required");
        }
        validateDataSource(context.dataSource(), false);
        List<String> physicalTables = tableRangeResolver.physicalTablesInRange(
                context.logicalTable(),
                context.beginTime(),
                context.endTime());
        if (CollectionUtils.isEmpty(physicalTables)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "no sharding physical table resolved");
        }
        return callback.execute(physicalTables);
    }

    /**
     * 按分表键解析单张物理表并执行新增。
     *
     * @param context 单表写入上下文
     * @param callback 新增回调
     * @return 新增结果
     */
    public <T> T insert(ShardingSingleTableContext context, ShardingPhysicalTableCallback<T> callback) {
        validateDataSource(context == null ? null : context.dataSource(), true);
        return executeSingle(context, callback);
    }

    /**
     * 按分表键解析单张物理表并执行更新。
     *
     * @param context 单表更新上下文
     * @param callback 更新回调
     * @return 更新结果
     */
    public <T> T update(ShardingSingleTableContext context, ShardingPhysicalTableCallback<T> callback) {
        validateDataSource(context == null ? null : context.dataSource(), true);
        return executeSingle(context, callback);
    }

    /**
     * 按分表键解析单张物理表并执行删除。
     *
     * @param context 单表删除上下文
     * @param callback 删除回调
     * @return 删除结果
     */
    public <T> T delete(ShardingSingleTableContext context, ShardingPhysicalTableCallback<T> callback) {
        validateDataSource(context == null ? null : context.dataSource(), true);
        return executeSingle(context, callback);
    }

    /**
     * 只解析单张物理表，不执行数据访问。
     *
     * @param context 单表上下文
     * @return 安全物理表名
     */
    public String resolvePhysicalTable(ShardingSingleTableContext context) {
        validateSingleContext(context);
        validateDataSource(context.dataSource(), false);
        return tableRangeResolver.physicalTable(context.logicalTable(), context.shardingTime());
    }

    /**
     * 只解析范围物理表，不执行数据访问。
     *
     * @param context 范围上下文
     * @return 安全物理表名列表
     */
    public List<String> resolvePhysicalTables(ShardingRangeTableContext context) {
        validateRangeContext(context);
        validateDataSource(context.dataSource(), false);
        return tableRangeResolver.physicalTablesInRange(context.logicalTable(), context.beginTime(), context.endTime());
    }

    /**
     * 解析并校验逻辑表分表规则。
     *
     * @param logicalTable 逻辑表名
     * @return 已启用的分表规则
     */
    public PaymentQuarterShardingProperties.TableRule resolveRule(String logicalTable) {
        return tableRangeResolver.resolveRule(logicalTable);
    }

    private <T> T executeSingle(ShardingSingleTableContext context, ShardingPhysicalTableCallback<T> callback) {
        validateSingleContext(context);
        if (callback == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding callback is required");
        }
        String physicalTable = tableRangeResolver.physicalTable(context.logicalTable(), context.shardingTime());
        return callback.execute(physicalTable);
    }

    /**
     * 校验singlecontext输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param context context 输入值，参与 context 的查询、校验、转换、写入或日志摘要
     */
    private void validateSingleContext(ShardingSingleTableContext context) {
        if (context == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding single table context is required");
        }
        if (!StringUtils.hasText(context.logicalTable())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logical table is required");
        }
        if (context.shardingTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding time is required");
        }
    }

    /**
     * 校验范围context输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param context context 输入值，参与 context 的查询、校验、转换、写入或日志摘要
     */
    private void validateRangeContext(ShardingRangeTableContext context) {
        if (context == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding range context is required");
        }
        if (!StringUtils.hasText(context.logicalTable())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logical table is required");
        }
    }

    /**
     * 校验data来源输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param dataSource data Source 输入值，参与 data来源 的查询、校验、转换、写入或日志摘要
     * @param writeOperation write Operation 输入值，参与 write动作 的查询、校验、转换、写入或日志摘要
     */
    private void validateDataSource(String dataSource, boolean writeOperation) {
        if (!StringUtils.hasText(dataSource)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding data source is required");
        }
        if (writeOperation && !DataSourceName.MASTER.equals(dataSource)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding write operation must use master data source");
        }
        if (!DataSourceName.MASTER.equals(dataSource)
                && !DataSourceName.SLAVE.equals(dataSource)
                && !DataSourceName.SLAVE_1.equals(dataSource)
                && !DataSourceName.SLAVE_2.equals(dataSource)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding data source is invalid");
        }
    }
}

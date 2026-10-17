package com.scott.payment.payment.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentSchemaReadinessMapper
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 支付数据库契约只读 Mapper，检查交易动作逻辑表和现有月分表的接入 IP 审计字段。
 * @status : create
 */
public interface PaymentSchemaReadinessMapper {

    /**
     * 返回缺失逻辑表或缺少任一接入 IP 审计列的动作表。
     *
     * @return 不满足当前支付持久化契约的表名
     */
    @DS(DataSourceName.MASTER)
    @Select("""
            SELECT 'transaction_operation'
            WHERE NOT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'transaction_operation'
            )
            UNION ALL
            SELECT tables_found.table_name
            FROM (
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND (table_name = 'transaction_operation'
                       OR table_name REGEXP '^transaction_operation_[0-9]{6}$')
            ) tables_found
            WHERE NOT EXISTS (
                      SELECT 1 FROM information_schema.columns c
                      WHERE c.table_schema = DATABASE()
                        AND c.table_name = tables_found.table_name
                        AND c.column_name = 'ingress_client_ip'
                  )
               OR NOT EXISTS (
                      SELECT 1 FROM information_schema.columns c
                      WHERE c.table_schema = DATABASE()
                        AND c.table_name = tables_found.table_name
                        AND c.column_name = 'ingress_client_ip_hash'
                  )
            """)
    List<String> selectIncompatibleTransactionOperationTables();
}

package com.scott.payment.risk.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 风控数据库契约只读 Mapper，在主库查询 information_schema，不读取业务表数据或执行 DDL。
 */
public interface RiskSchemaReadinessMapper {

    /**
     * 查询实时风控运行所需表列中当前已存在的对象。
     *
     * @return {@code table:表名} 或 {@code column:表名.列名} 格式的已存在对象集合
     */
    @DS(DataSourceName.MASTER)
    @Select("""
            SELECT CONCAT('table:', table_name)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('risk_merchant_function_config',
                                 'risk_evaluation_record',
                                 'risk_evaluation_hit_detail')
            UNION ALL
            SELECT CONCAT('column:', table_name, '.', column_name)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND (
                    (table_name = 'risk_merchant_function_config'
                        AND column_name IN ('merchant_id', 'module_type', 'function_code', 'enabled', 'deleted'))
                 OR (table_name = 'risk_evaluation_record'
                        AND column_name = 'decision_reason_code')
                 OR (table_name = 'risk_evaluation_hit_detail'
                        AND column_name = 'reason_code')
                  )
            """)
    List<String> selectPresentRequiredColumns();
}

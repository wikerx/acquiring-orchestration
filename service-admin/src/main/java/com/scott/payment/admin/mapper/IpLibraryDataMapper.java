package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.base.IpLibraryEntities;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDataMapper
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : IpLibraryDataMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface IpLibraryDataMapper {

    /**
     * 查询某个分表内 IP 区间列表。
     */
    @Select("""
            <script>
            SELECT id,
                   ip_type AS ipType,
                   CAST(ip_number_start AS CHAR) AS ipNumberStart,
                   CAST(ip_number_end AS CHAR) AS ipNumberEnd,
                   country_alpha2 AS countryAlpha2,
                   country_alpha3 AS countryAlpha3,
                   country_numeric AS countryNumeric,
                   country_name AS countryName,
                   state_province AS stateProvince,
                   city,
                   data_version AS dataVersion,
                   create_time AS createTime,
                   create_by AS createBy
            FROM ${tableName}
            WHERE deleted = 0
            <if test="dataVersion != null and dataVersion != ''">
              AND data_version = #{dataVersion}
            </if>
            <if test="ipNumber != null and ipNumber != ''">
              AND ip_number_start &lt;= #{ipNumber}
              AND ip_number_end &gt;= #{ipNumber}
            </if>
            ORDER BY ip_number_start ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<IpLibraryEntities.IpLibraryDataRow> selectPageRows(@Param("tableName") String tableName,
                                                            @Param("dataVersion") String dataVersion,
                                                            @Param("ipNumber") String ipNumber,
                                                            @Param("offset") long offset,
                                                            /**
                                                             * 完成 m 分支的校验或状态更新。
                                                             * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                             * <p>
                                                             * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                             * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                             * </p>
                                                             * @param pageSize page Size 输入值，含义由调用方法名称和所属业务对象限定
                                                             */
                                                            @Param("pageSize") int pageSize);

    /**
     * 统计某个分表内符合条件的 IP 区间数量。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
            <if test="dataVersion != null and dataVersion != ''">
              AND data_version = #{dataVersion}
            </if>
            <if test="ipNumber != null and ipNumber != ''">
              AND ip_number_start &lt;= #{ipNumber}
              AND ip_number_end &gt;= #{ipNumber}
            </if>
            </script>
            """)
    long countRows(@Param("tableName") String tableName,
                   @Param("dataVersion") String dataVersion,
                   /**
                    * 完成 m 分支的校验或状态更新。
                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                    * <p>
                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                    * </p>
                    * @param ipNumber ip Number 输入值，含义由调用方法名称和所属业务对象限定
                    */
                   @Param("ipNumber") String ipNumber);

    /**
     * 按起始值倒序查询单个 IP 的最近候选区间。
     */
    @Select("""
            SELECT id,
                   ip_type AS ipType,
                   CAST(ip_number_start AS CHAR) AS ipNumberStart,
                   CAST(ip_number_end AS CHAR) AS ipNumberEnd,
                   country_alpha2 AS countryAlpha2,
                   country_alpha3 AS countryAlpha3,
                   country_numeric AS countryNumeric,
                   country_name AS countryName,
                   state_province AS stateProvince,
                   city,
                   data_version AS dataVersion,
                   create_time AS createTime,
                   create_by AS createBy
            FROM ${tableName}
            WHERE deleted = 0
              AND data_version = #{dataVersion}
              AND ip_number_start <= #{ipNumber}
            ORDER BY ip_number_start DESC
            LIMIT 1
            """)
    IpLibraryEntities.IpLibraryDataRow selectLookupCandidate(@Param("tableName") String tableName,
                                                             @Param("dataVersion") String dataVersion,
                                                             /**
                                                              * 完成 m 分支的校验或状态更新。
                                                              * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                              * <p>
                                                              * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                              * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                              * </p>
                                                              * @param ipNumber ip Number 输入值，含义由调用方法名称和所属业务对象限定
                                                              */
                                                             @Param("ipNumber") String ipNumber);
}

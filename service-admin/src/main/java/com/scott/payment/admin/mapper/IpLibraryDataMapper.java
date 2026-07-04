package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.base.IpLibraryEntities;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDataMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : IP 库物理分表查询 Mapper。 <p>{@code tableName} 只能由应用服务从固定白名单解析后传入，禁止使用前端参数直接拼接。</p>
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
                                                             @Param("ipNumber") String ipNumber);
}

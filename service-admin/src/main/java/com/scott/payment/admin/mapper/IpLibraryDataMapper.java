package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.base.IpLibraryEntities;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/**
 * IP Library Data 数据访问接口，负责对应持久化记录的查询和写入。
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

}

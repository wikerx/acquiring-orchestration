package com.scott.payment.component.db.reference.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.reference.entity.IpLibraryDataRow;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLocationLookupMapper
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 归属只读 Mapper，先选择唯一已就绪分片，再在受控物理表中执行区间命中查询
 * @status : create
 */
@Mapper
public interface IpLocationLookupMapper extends BaseMapper<IpLibraryShardDO> {

    /**
     * 查询覆盖目标 IP 数值的已就绪分片。
     *
     * @param ipType   IP 类型
     * @param ipNumber IP 无符号数值字符串
     * @return 匹配的分片配置；调用方要求结果必须且只能有一条
     */
    @Select("""
            SELECT id, ip_type, shard_no, table_name, data_version, active_flag, load_status
            FROM ip_library_split_model
            WHERE active_flag = 1
              AND load_status = 'READY'
              AND ip_type = #{ipType}
              AND range_start <= #{ipNumber}
              AND range_end >= #{ipNumber}
            ORDER BY shard_no ASC, id DESC
            """)
    List<IpLibraryShardDO> selectReadyShards(@Param("ipType") String ipType,
                                             @Param("ipNumber") String ipNumber);

    /**
     * 在服务端白名单确认后的物理分表中查询 IP 归属区间。
     *
     * @param tableName  已通过白名单校验的物理表名
     * @param dataVersion 当前生效数据版本
     * @param ipNumber   IP 无符号数值字符串
     * @return 命中的最接近区间，未命中返回 null
     */
    @Select("""
            SELECT country_alpha2 AS countryAlpha2,
                   country_alpha3 AS countryAlpha3,
                   country_numeric AS countryNumeric,
                   country_name AS countryName,
                   state_province AS stateProvince,
                   city
            FROM ${tableName}
            WHERE deleted = 0
              AND data_version = #{dataVersion}
              AND ip_number_start <= #{ipNumber}
              AND ip_number_end >= #{ipNumber}
            ORDER BY ip_number_start DESC, id DESC
            LIMIT 1
            """)
    IpLibraryDataRow selectLookupCandidate(@Param("tableName") String tableName,
                                           @Param("dataVersion") String dataVersion,
                                           @Param("ipNumber") String ipNumber);
}

package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskManagementMapper
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控管理 Mapper，位于 service-admin 数据访问层，仅接收服务层白名单解析后的物理表名。
 * @status : create
 */
public interface RiskManagementMapper {

    /**
     * 创建风控导入批次记录。
     *
     * @param moduleType   模块类型
     * @param functionCode 功能编码
     * @param batchNo      导入批次号
     * @param fileName     导入文件名
     * @param totalCount   CSV 数据行数
     * @param operator     操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_import_batch (
                module_type, function_code, batch_no, file_name,
                total_count, success_count, failed_count, status, operator, remark
            ) VALUES (
                #{moduleType}, #{functionCode}, #{batchNo}, #{fileName},
                #{totalCount}, 0, 0, 0, #{operator}, '处理中'
            )
            """)
    int insertImportBatch(@Param("moduleType") String moduleType,
                          @Param("functionCode") String functionCode,
                          @Param("batchNo") String batchNo,
                          @Param("fileName") String fileName,
                          @Param("totalCount") int totalCount,
                          @Param("operator") String operator);

    /**
     * 更新风控导入批次结果。
     *
     * @param batchNo      导入批次号
     * @param successCount 成功导入行数
     * @param failedCount  失败行数
     * @param status       批次状态：1成功、3失败
     * @param remark       结果说明
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_import_batch
            SET success_count = #{successCount},
                failed_count = #{failedCount},
                status = #{status},
                remark = #{remark},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE batch_no = #{batchNo}
            """)
    int updateImportBatch(@Param("batchNo") String batchNo,
                          @Param("successCount") int successCount,
                          @Param("failedCount") int failedCount,
                          @Param("status") int status,
                          @Param("remark") String remark);

    /**
     * 写入风控导入行级错误。
     *
     * @param batchNo      导入批次号
     * @param rowNo        CSV 文件行号
     * @param rawContent   脱敏后的原始行内容
     * @param errorMessage 错误说明
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_import_error (batch_no, row_no, raw_content, error_message)
            VALUES (#{batchNo}, #{rowNo}, #{rawContent}, #{errorMessage})
            """)
    int insertImportError(@Param("batchNo") String batchNo,
                          @Param("rowNo") int rowNo,
                          @Param("rawContent") String rawContent,
                          @Param("errorMessage") String errorMessage);

    /**
     * 查询通用名单总数。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param matchValue    脱敏匹配值，允许为空并支持模糊匹配
     * @param cardBinLookupNumber 卡BIN查询数值，允许为空
     * @param countryAlpha2 国家 Alpha-2 编码，允许为空
     * @param status        状态，允许为空
     * @param hasCountryFields 当前表是否保留国家字段
     * @return 满足条件的记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null">AND COALESCE(merchant_id, '') = #{merchantId}</if>
            <if test="cardBinLookupNumber != null">AND match_value_start_number &lt;= #{cardBinLookupNumber} AND match_value_end_number &gt;= #{cardBinLookupNumber}</if>
            <if test="cardBinLookupNumber == null and matchValue != null and matchValue != ''">AND match_value_masked LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="hasCountryFields and countryAlpha2 != null and countryAlpha2 != ''">AND country_alpha3 = #{countryAlpha2}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countList(@Param("tableName") String tableName,
                   @Param("merchantScope") String merchantScope,
                   @Param("merchantId") String merchantId,
                   @Param("matchValue") String matchValue,
                   @Param("cardBinLookupNumber") String cardBinLookupNumber,
                   @Param("countryAlpha2") String countryAlpha2,
                   @Param("status") Integer status,
                   @Param("hasCountryFields") boolean hasCountryFields);

    /**
     * 分页查询通用名单。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param matchValue    脱敏匹配值，允许为空并支持模糊匹配
     * @param cardBinLookupNumber 卡BIN查询数值，允许为空
     * @param countryAlpha2 国家 Alpha-2 编码，允许为空
     * @param status        状态，允许为空
     * @param offset        分页偏移量
     * @param pageSize      每页记录数
     * @param hasCountryFields 当前表是否保留国家字段
     * @return 通用名单记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${tableName}
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null">AND COALESCE(merchant_id, '') = #{merchantId}</if>
            <if test="cardBinLookupNumber != null">AND match_value_start_number &lt;= #{cardBinLookupNumber} AND match_value_end_number &gt;= #{cardBinLookupNumber}</if>
            <if test="cardBinLookupNumber == null and matchValue != null and matchValue != ''">AND match_value_masked LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="hasCountryFields and countryAlpha2 != null and countryAlpha2 != ''">AND country_alpha3 = #{countryAlpha2}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectListPage(@Param("tableName") String tableName,
                                             @Param("merchantScope") String merchantScope,
                                             @Param("merchantId") String merchantId,
                                             @Param("matchValue") String matchValue,
                                             @Param("cardBinLookupNumber") String cardBinLookupNumber,
                                             @Param("countryAlpha2") String countryAlpha2,
                                             @Param("status") Integer status,
                                             @Param("offset") long offset,
                                             @Param("pageSize") long pageSize,
                                             @Param("hasCountryFields") boolean hasCountryFields);

    /**
     * 查询高风险区域黑名单总数。
     *
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param matchValue    国家 Alpha-3、州省、城市关键字，允许为空并支持模糊匹配
     * @param countryAlpha3 国家或地区 Alpha-3 编码，允许为空
     * @param status        状态，允许为空
     * @return 满足条件的记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_black_region
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="matchValue != null and matchValue != ''">
                AND (
                    country_alpha3 LIKE CONCAT('%', UPPER(#{matchValue}), '%')
                    OR state_province_name LIKE CONCAT('%', #{matchValue}, '%')
                    OR city_name LIKE CONCAT('%', #{matchValue}, '%')
                )
            </if>
            <if test="countryAlpha3 != null and countryAlpha3 != ''">AND country_alpha3 = #{countryAlpha3}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countRegion(@Param("merchantScope") String merchantScope,
                     @Param("merchantId") String merchantId,
                     @Param("matchValue") String matchValue,
                     @Param("countryAlpha3") String countryAlpha3,
                     @Param("status") Integer status);

    /**
     * 分页查询高风险区域黑名单。
     *
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param matchValue    国家 Alpha-3、州省、城市关键字，允许为空并支持模糊匹配
     * @param countryAlpha3 国家或地区 Alpha-3 编码，允许为空
     * @param status        状态，允许为空
     * @param offset        分页偏移量
     * @param pageSize      每页记录数
     * @return 高风险区域记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM risk_black_region
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="matchValue != null and matchValue != ''">
                AND (
                    country_alpha3 LIKE CONCAT('%', UPPER(#{matchValue}), '%')
                    OR state_province_name LIKE CONCAT('%', #{matchValue}, '%')
                    OR city_name LIKE CONCAT('%', #{matchValue}, '%')
                )
            </if>
            <if test="countryAlpha3 != null and countryAlpha3 != ''">AND country_alpha3 = #{countryAlpha3}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectRegionPage(@Param("merchantScope") String merchantScope,
                                               @Param("merchantId") String merchantId,
                                               @Param("matchValue") String matchValue,
                                               @Param("countryAlpha3") String countryAlpha3,
                                               @Param("status") Integer status,
                                               @Param("offset") long offset,
                                               @Param("pageSize") long pageSize);

    /**
     * 查询单条配置记录。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param id        配置记录ID
     * @return 配置记录；不存在或已软删除时返回空
     */
    @Select("SELECT * FROM ${tableName} WHERE id = #{id} AND deleted = 0")
    Map<String, Object> selectById(@Param("tableName") String tableName, @Param("id") Long id);

    /**
     * 查询名单重复记录数。
     *
     * @param tableName      物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope  生效范围
     * @param merchantId     商户号，全局范围允许为空
     * @param matchValueHash 归一化匹配值哈希
     * @param excludeId      编辑时排除的记录ID，新增时为空
     * @return 未删除重复记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
              AND merchant_scope = #{merchantScope}
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND match_value_hash = #{matchValueHash}
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countListDuplicate(@Param("tableName") String tableName,
                            @Param("merchantScope") String merchantScope,
                            @Param("merchantId") String merchantId,
                            @Param("matchValueHash") String matchValueHash,
                            @Param("excludeId") Long excludeId);

    /**
     * 查询来源网址 AML 重复记录数。AML 来源网址是全局名单，唯一口径为 URL host。
     *
     * @param sourceHost 来源网址 host
     * @param excludeId  编辑时排除的记录ID，新增时为空
     * @return 未删除重复记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_aml_source_url
            WHERE deleted = 0
              AND source_host = #{sourceHost}
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countAmlSourceUrlHostDuplicate(@Param("sourceHost") String sourceHost,
                                        @Param("excludeId") Long excludeId);

    /**
     * 查询内风控规则重复记录数。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围
     * @param merchantId    商户号，全局范围允许为空
     * @param matchMode     匹配方式，允许为空
     * @param matchValue    匹配值，允许为空
     * @param limitType     限额类型，允许为空
     * @param amountMin     最小金额，限额规则用于重复判断，允许为空
     * @param amountMax     最大金额，限额规则用于重复判断，允许为空
     * @param currency      币种，允许为空
     * @param timeWindowSeconds 时间窗口秒数，频率规则用于重复判断，允许为空
     * @param thresholdCount 阈值次数，频率规则用于重复判断，允许为空
     * @param elementsJson  元素配置 JSON，频率规则用于重复判断，允许为空
     * @param excludeId     编辑时排除的记录ID，新增时为空
     * @return 未删除重复规则数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
              AND merchant_scope = #{merchantScope}
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND COALESCE(match_mode, '') = COALESCE(#{matchMode}, '')
              AND COALESCE(match_value, '') = COALESCE(#{matchValue}, '')
              AND COALESCE(limit_type, '') = COALESCE(#{limitType}, '')
              AND COALESCE(amount_min, 0) = COALESCE(#{amountMin}, 0)
              AND COALESCE(amount_max, 0) = COALESCE(#{amountMax}, 0)
              AND COALESCE(currency, '') = COALESCE(#{currency}, '')
              AND COALESCE(time_window_seconds, 0) = COALESCE(#{timeWindowSeconds}, 0)
              AND COALESCE(threshold_count, 0) = COALESCE(#{thresholdCount}, 0)
              AND COALESCE(elements_json, '') = COALESCE(#{elementsJson}, '')
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countRuleDuplicate(@Param("tableName") String tableName,
                            @Param("merchantScope") String merchantScope,
                            @Param("merchantId") String merchantId,
                            @Param("matchMode") String matchMode,
                            @Param("matchValue") String matchValue,
                            @Param("limitType") String limitType,
                            @Param("amountMin") java.math.BigDecimal amountMin,
                            @Param("amountMax") java.math.BigDecimal amountMax,
                            @Param("currency") String currency,
                            @Param("timeWindowSeconds") Integer timeWindowSeconds,
                            @Param("thresholdCount") Integer thresholdCount,
                            @Param("elementsJson") String elementsJson,
                            @Param("excludeId") Long excludeId);

    /**
     * 查询商户来源网址重复记录数。来源网址限定按商户号和 host 唯一，不使用生效范围、规则名称和匹配方式。
     *
     * @param merchantId 商户号
     * @param sourceHost 来源网址 host
     * @param excludeId  编辑时排除的记录ID，新增时为空
     * @return 未删除重复记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_source_url
            WHERE deleted = 0
              AND merchant_id = #{merchantId}
              AND source_host = #{sourceHost}
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countSourceUrlDuplicate(@Param("merchantId") String merchantId,
                                 @Param("sourceHost") String sourceHost,
                                 @Param("excludeId") Long excludeId);

    /**
     * 新增通用名单记录。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param data      通用名单字段映射，敏感值只允许写入脱敏值或哈希
     * @param operator  操作人
     * @param hasRangeFields 当前表是否保留区间字段
     * @param hasCardBrandField 当前表是否保留卡品牌字段
     * @param hasCountryFields 当前表是否保留国家字段
     * @param hasCountryNumericField 当前表是否保留国家数字码字段
     * @param hasIpVersionField 当前表是否保留 IP 版本字段
     * @param hasSourceHostField 当前表是否保留来源网址 host 字段
     * @return 影响行数
     */
    @Insert("""
            <script>
            INSERT INTO ${tableName} (
                merchant_scope, merchant_id, match_value_masked, match_value_hash, match_value_cipher
                <if test="hasRangeFields">
                , match_value_start, match_value_end, match_value_start_number, match_value_end_number
                </if>
                <if test="hasIpVersionField">
                , ip_version
                </if>
                <if test="hasCardBrandField">
                , card_brand
                </if>
                <if test="hasCountryFields">
                , country_alpha2, country_alpha3
                </if>
                <if test="hasCountryNumericField">
                , country_numeric
                </if>
                <if test="hasSourceHostField">
                , source_host
                </if>
                , risk_level, decision_action, effective_time, expire_time,
                validity_type, validity_days, source_type, status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.merchantScope}, #{data.merchantId}, #{data.matchValueMasked}, #{data.matchValueHash}, #{data.matchValueCipher}
                <if test="hasRangeFields">
                , #{data.matchValueStart}, #{data.matchValueEnd}, #{data.matchValueStartNumber}, #{data.matchValueEndNumber}
                </if>
                <if test="hasIpVersionField">
                , #{data.ipVersion}
                </if>
                <if test="hasCardBrandField">
                , #{data.cardBrand}
                </if>
                <if test="hasCountryFields">
                , #{data.countryAlpha2}, #{data.countryAlpha3}
                </if>
                <if test="hasCountryNumericField">
                , #{data.countryNumeric}
                </if>
                <if test="hasSourceHostField">
                , #{data.sourceHost}
                </if>
                , #{data.riskLevel}, #{data.decisionAction}, #{data.effectiveTime}, #{data.expireTime},
                #{data.validityType}, #{data.validityDays}, #{data.sourceType}, #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            </script>
            """)
    int insertListRecord(@Param("tableName") String tableName,
                         @Param("data") Map<String, Object> data,
                         @Param("operator") String operator,
                         @Param("hasRangeFields") boolean hasRangeFields,
                         @Param("hasCardBrandField") boolean hasCardBrandField,
                         @Param("hasCountryFields") boolean hasCountryFields,
                         @Param("hasCountryNumericField") boolean hasCountryNumericField,
                         @Param("hasIpVersionField") boolean hasIpVersionField,
                         @Param("hasSourceHostField") boolean hasSourceHostField);

    /**
     * 修改通用名单记录。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param id        配置记录ID
     * @param data      通用名单字段映射，敏感值只允许写入脱敏值或哈希
     * @param operator  操作人
     * @param hasRangeFields 当前表是否保留区间字段
     * @param hasCardBrandField 当前表是否保留卡品牌字段
     * @param hasCountryFields 当前表是否保留国家字段
     * @param hasCountryNumericField 当前表是否保留国家数字码字段
     * @param hasIpVersionField 当前表是否保留 IP 版本字段
     * @param hasSourceHostField 当前表是否保留来源网址 host 字段
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE ${tableName}
            SET merchant_scope = #{data.merchantScope},
                merchant_id = #{data.merchantId},
                match_value_masked = #{data.matchValueMasked},
                match_value_hash = #{data.matchValueHash},
                match_value_cipher = #{data.matchValueCipher},
            <if test="hasRangeFields">
                match_value_start = #{data.matchValueStart},
                match_value_end = #{data.matchValueEnd},
                match_value_start_number = #{data.matchValueStartNumber},
                match_value_end_number = #{data.matchValueEndNumber},
            </if>
            <if test="hasIpVersionField">
                ip_version = #{data.ipVersion},
            </if>
            <if test="hasCardBrandField">
                card_brand = #{data.cardBrand},
            </if>
            <if test="hasCountryFields">
                country_alpha2 = #{data.countryAlpha2},
                country_alpha3 = #{data.countryAlpha3},
            </if>
            <if test="hasCountryNumericField">
                country_numeric = #{data.countryNumeric},
            </if>
            <if test="hasSourceHostField">
                source_host = #{data.sourceHost},
            </if>
                risk_level = #{data.riskLevel},
                decision_action = #{data.decisionAction},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                validity_type = #{data.validityType},
                validity_days = #{data.validityDays},
                source_type = #{data.sourceType},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            </script>
            """)
    int updateListRecord(@Param("tableName") String tableName,
                         @Param("id") Long id,
                         @Param("data") Map<String, Object> data,
                         @Param("operator") String operator,
                         @Param("hasRangeFields") boolean hasRangeFields,
                         @Param("hasCardBrandField") boolean hasCardBrandField,
                         @Param("hasCountryFields") boolean hasCountryFields,
                         @Param("hasCountryNumericField") boolean hasCountryNumericField,
                         @Param("hasIpVersionField") boolean hasIpVersionField,
                         @Param("hasSourceHostField") boolean hasSourceHostField);

    /**
     * 新增高风险区域黑名单。
     *
     * @param data     区域字段映射，支持国家、州省、城市三级粒度
     * @param operator 操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_black_region (
                merchant_scope, merchant_id, region_match_level,
                country_alpha3, state_province_name, city_name,
                risk_level, decision_action,
                effective_time, expire_time, validity_type, validity_days, source_type,
                status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.merchantScope}, #{data.merchantId}, #{data.regionMatchLevel},
                #{data.countryAlpha3}, #{data.stateProvinceName}, #{data.cityName},
                #{data.riskLevel}, #{data.decisionAction},
                #{data.effectiveTime}, #{data.expireTime}, #{data.validityType}, #{data.validityDays}, #{data.sourceType},
                #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            """)
    int insertRegion(@Param("data") Map<String, Object> data, @Param("operator") String operator);

    /**
     * 修改高风险区域黑名单。
     *
     * @param id       区域记录ID
     * @param data     区域字段映射，支持国家、州省、城市三级粒度
     * @param operator 操作人
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_black_region
            SET merchant_scope = #{data.merchantScope},
                merchant_id = #{data.merchantId},
                region_match_level = #{data.regionMatchLevel},
                country_alpha3 = #{data.countryAlpha3},
                state_province_name = #{data.stateProvinceName},
                city_name = #{data.cityName},
                risk_level = #{data.riskLevel},
                decision_action = #{data.decisionAction},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                validity_type = #{data.validityType},
                validity_days = #{data.validityDays},
                source_type = #{data.sourceType},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int updateRegion(@Param("id") Long id, @Param("data") Map<String, Object> data, @Param("operator") String operator);

    /**
     * 查询高风险区域重复记录数。
     *
     * @param merchantScope     生效范围
     * @param merchantId        商户号，全局范围允许为空
     * @param regionMatchLevel  区域匹配级别
     * @param countryAlpha3     国家或地区 Alpha-3 编码
     * @param stateProvinceName 州省名称，允许为空
     * @param cityName          城市名称，允许为空
     * @param excludeId         编辑时排除的记录ID，新增时为空
     * @return 未删除重复记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_black_region
            WHERE deleted = 0
              AND merchant_scope = #{merchantScope}
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND region_match_level = #{regionMatchLevel}
              AND country_alpha3 = #{countryAlpha3}
              AND COALESCE(state_province_name, '') = COALESCE(#{stateProvinceName}, '')
              AND COALESCE(city_name, '') = COALESCE(#{cityName}, '')
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countRegionDuplicate(@Param("merchantScope") String merchantScope,
                              @Param("merchantId") String merchantId,
                              @Param("regionMatchLevel") String regionMatchLevel,
                              @Param("countryAlpha3") String countryAlpha3,
                              @Param("stateProvinceName") String stateProvinceName,
                              @Param("cityName") String cityName,
                              @Param("excludeId") Long excludeId);

    /**
     * 软删除名单或规则记录。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param id        配置记录ID
     * @param operator  操作人
     * @return 影响行数
     */
    @Update("UPDATE ${tableName} SET deleted = id, update_by = #{operator}, update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("tableName") String tableName, @Param("id") Long id, @Param("operator") String operator);

    /**
     * 更新名单或规则状态。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param id        配置记录ID
     * @param status    目标状态，0 停用，1 启用
     * @param operator  操作人
     * @return 影响行数
     */
    @Update("UPDATE ${tableName} SET status = #{status}, update_by = #{operator}, update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("tableName") String tableName, @Param("id") Long id, @Param("status") Integer status, @Param("operator") String operator);

    /**
     * 查询内风控规则总数。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param ruleName      规则名称，允许为空并支持模糊匹配
     * @param matchValue    规则匹配值，允许为空并支持模糊匹配
     * @param limitType     限额类型，商户交易限额规则使用，允许为空
     * @param currency      交易币种，允许为空
     * @param status        状态，允许为空
     * @return 满足条件的规则数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="matchValue != null and matchValue != ''">AND match_value LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="limitType != null and limitType != ''">AND limit_type = #{limitType}</if>
            <if test="currency != null and currency != ''">AND currency = #{currency}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countRules(@Param("tableName") String tableName,
                    @Param("merchantScope") String merchantScope,
                    @Param("merchantId") String merchantId,
                    @Param("ruleName") String ruleName,
                    @Param("matchValue") String matchValue,
                    @Param("limitType") String limitType,
                    @Param("currency") String currency,
                    @Param("status") Integer status);

    /**
     * 分页查询内风控规则。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param ruleName      规则名称，允许为空并支持模糊匹配
     * @param matchValue    规则匹配值，允许为空并支持模糊匹配
     * @param limitType     限额类型，商户交易限额规则使用，允许为空
     * @param currency      交易币种，允许为空
     * @param status        状态，允许为空
     * @param offset        分页偏移量
     * @param pageSize      每页记录数
     * @return 规则记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${tableName}
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="matchValue != null and matchValue != ''">AND match_value LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="limitType != null and limitType != ''">AND limit_type = #{limitType}</if>
            <if test="currency != null and currency != ''">AND currency = #{currency}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectRulePage(@Param("tableName") String tableName,
                                             @Param("merchantScope") String merchantScope,
                                             @Param("merchantId") String merchantId,
                                             @Param("ruleName") String ruleName,
                                             @Param("matchValue") String matchValue,
                                             @Param("limitType") String limitType,
                                             @Param("currency") String currency,
                                             @Param("status") Integer status,
                                             @Param("offset") long offset,
                                             @Param("pageSize") long pageSize);

    /**
     * 查询商户交易限额总数。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="matchValue != null and matchValue != ''">AND match_value LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="limitType != null and limitType != ''">AND limit_type = #{limitType}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countMerchantLimitRules(@Param("merchantScope") String merchantScope,
                                 @Param("merchantId") String merchantId,
                                 @Param("ruleName") String ruleName,
                                 @Param("matchValue") String matchValue,
                                 @Param("limitType") String limitType,
                                 @Param("status") Integer status);

    /**
     * 分页查询商户交易限额。
     */
    @Select("""
            <script>
            SELECT *
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="matchValue != null and matchValue != ''">AND match_value LIKE CONCAT('%', #{matchValue}, '%')</if>
            <if test="limitType != null and limitType != ''">AND limit_type = #{limitType}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectMerchantLimitRulePage(@Param("merchantScope") String merchantScope,
                                                          @Param("merchantId") String merchantId,
                                                          @Param("ruleName") String ruleName,
                                                          @Param("matchValue") String matchValue,
                                                          @Param("limitType") String limitType,
                                                          @Param("status") Integer status,
                                                          @Param("offset") long offset,
                                                          @Param("pageSize") long pageSize);

    /**
     * 查询商户交易限额重复记录数。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND merchant_scope = #{merchantScope}
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND COALESCE(rule_name, '') = COALESCE(#{ruleName}, '')
              AND COALESCE(match_value, '') = COALESCE(#{matchValue}, '')
              AND COALESCE(limit_type, '') = COALESCE(#{limitType}, '')
              AND COALESCE(amount_min, 0) = COALESCE(#{amountMin}, 0)
              AND COALESCE(amount_max, 0) = COALESCE(#{amountMax}, 0)
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countMerchantLimitDuplicate(@Param("merchantScope") String merchantScope,
                                     @Param("merchantId") String merchantId,
                                     @Param("ruleName") String ruleName,
                                     @Param("matchValue") String matchValue,
                                     @Param("limitType") String limitType,
                                     @Param("amountMin") java.math.BigDecimal amountMin,
                                     @Param("amountMax") java.math.BigDecimal amountMax,
                                     @Param("excludeId") Long excludeId);

    /**
     * 查询 3DS 规则总数。3DS 规则按商户、渠道、卡品牌、金额区间、风险条件和触发动作做结构化匹配。
     *
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param ruleName      规则名称，允许为空并支持模糊匹配
     * @param ruleType      规则类型，允许为空
     * @param channelCode   收单渠道编码，允许为空
     * @param paymentMethod 支付方式，允许为空
     * @param cardBrand     卡品牌，允许为空
     * @param currency      交易币种，允许为空
     * @param triggerAction 触发动作，允许为空
     * @param status        状态，允许为空
     * @return 满足条件的规则数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_3ds
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="ruleType != null and ruleType != ''">AND rule_type = #{ruleType}</if>
            <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
            <if test="paymentMethod != null and paymentMethod != ''">AND payment_method = #{paymentMethod}</if>
            <if test="cardBrand != null and cardBrand != ''">AND card_brand = #{cardBrand}</if>
            <if test="currency != null and currency != ''">AND currency = #{currency}</if>
            <if test="triggerAction != null and triggerAction != ''">AND trigger_action = #{triggerAction}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countThreeDsRules(@Param("merchantScope") String merchantScope,
                           @Param("merchantId") String merchantId,
                           @Param("ruleName") String ruleName,
                           @Param("ruleType") String ruleType,
                           @Param("channelCode") String channelCode,
                           @Param("paymentMethod") String paymentMethod,
                           @Param("cardBrand") String cardBrand,
                           @Param("currency") String currency,
                           @Param("triggerAction") String triggerAction,
                           @Param("status") Integer status);

    /**
     * 分页查询 3DS 规则。
     *
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，允许为空
     * @param ruleName      规则名称，允许为空并支持模糊匹配
     * @param ruleType      规则类型，允许为空
     * @param channelCode   收单渠道编码，允许为空
     * @param paymentMethod 支付方式，允许为空
     * @param cardBrand     卡品牌，允许为空
     * @param currency      交易币种，允许为空
     * @param triggerAction 触发动作，允许为空
     * @param status        状态，允许为空
     * @param offset        分页偏移量
     * @param pageSize      每页记录数
     * @return 3DS 规则记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM risk_rule_3ds
            WHERE deleted = 0
            <if test="merchantScope != null and merchantScope != ''">AND merchant_scope = #{merchantScope}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="ruleName != null and ruleName != ''">AND rule_name LIKE CONCAT('%', #{ruleName}, '%')</if>
            <if test="ruleType != null and ruleType != ''">AND rule_type = #{ruleType}</if>
            <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
            <if test="paymentMethod != null and paymentMethod != ''">AND payment_method = #{paymentMethod}</if>
            <if test="cardBrand != null and cardBrand != ''">AND card_brand = #{cardBrand}</if>
            <if test="currency != null and currency != ''">AND currency = #{currency}</if>
            <if test="triggerAction != null and triggerAction != ''">AND trigger_action = #{triggerAction}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectThreeDsRulePage(@Param("merchantScope") String merchantScope,
                                                    @Param("merchantId") String merchantId,
                                                    @Param("ruleName") String ruleName,
                                                    @Param("ruleType") String ruleType,
                                                    @Param("channelCode") String channelCode,
                                                    @Param("paymentMethod") String paymentMethod,
                                                    @Param("cardBrand") String cardBrand,
                                                    @Param("currency") String currency,
                                                    @Param("triggerAction") String triggerAction,
                                                    @Param("status") Integer status,
                                                    @Param("offset") long offset,
                                                    @Param("pageSize") long pageSize);

    /**
     * 查询 3DS 精确重复规则数。金额区间重叠需要服务层额外校验，精确重复由该查询兜底。
     *
     * @param merchantScope   生效范围
     * @param merchantId      商户号，全局范围为空字符串
     * @param channelCode     收单渠道编码
     * @param paymentMethod   支付方式
     * @param cardBrand       卡品牌
     * @param amountMatchType 金额匹配类型
     * @param amountMin       最小金额
     * @param amountMax       最大金额
     * @param currency        交易币种
     * @param riskCondition   风险条件
     * @param triggerAction   触发动作
     * @param excludeId       编辑时排除的记录ID，新增时为空
     * @return 未删除重复规则数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_3ds
            WHERE deleted = 0
              AND merchant_scope = #{merchantScope}
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND channel_code = #{channelCode}
              AND payment_method = #{paymentMethod}
              AND card_brand = #{cardBrand}
              AND amount_match_type = #{amountMatchType}
              AND COALESCE(amount_min, 0) = COALESCE(#{amountMin}, 0)
              AND COALESCE(amount_max, 0) = COALESCE(#{amountMax}, 0)
              AND currency = #{currency}
              AND risk_condition = #{riskCondition}
              AND trigger_action = #{triggerAction}
            <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    long countThreeDsDuplicate(@Param("merchantScope") String merchantScope,
                               @Param("merchantId") String merchantId,
                               @Param("channelCode") String channelCode,
                               @Param("paymentMethod") String paymentMethod,
                               @Param("cardBrand") String cardBrand,
                               @Param("amountMatchType") String amountMatchType,
                               @Param("amountMin") java.math.BigDecimal amountMin,
                               @Param("amountMax") java.math.BigDecimal amountMax,
                               @Param("currency") String currency,
                               @Param("riskCondition") String riskCondition,
                               @Param("triggerAction") String triggerAction,
                               @Param("excludeId") Long excludeId);

    /**
     * 查询同一商户交易限额维度下的周期限额金额，用于保存前校验日、周、月限额倍数关系。
     *
     * @param tableName     物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param merchantScope 生效范围，允许为空
     * @param merchantId    商户号，全局范围允许为空
     * @param matchValue    限额场景，允许为空
     * @param excludeId     编辑时排除的当前记录ID，新增时为空
     * @return 同组周期限额规则
     */
    @Select("""
            <script>
            SELECT limit_type, amount_min, amount_max
            FROM ${tableName}
            WHERE deleted = 0
              AND limit_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
              AND COALESCE(merchant_scope, '') = COALESCE(#{merchantScope}, '')
              AND COALESCE(merchant_id, '') = COALESCE(#{merchantId}, '')
              AND COALESCE(match_value, '') = COALESCE(#{matchValue}, '')
              <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
            </script>
            """)
    List<Map<String, Object>> selectMerchantLimitAmounts(@Param("tableName") String tableName,
                                                         @Param("merchantScope") String merchantScope,
                                                         @Param("merchantId") String merchantId,
                                                         @Param("matchValue") String matchValue,
                                                         @Param("excludeId") Long excludeId);

    /**
     * 查询商户来源网址限定总数。
     *
     * @param merchantId 商户号，允许为空
     * @param sourceUrl  商户录入 URL，允许为空并支持模糊匹配
     * @param sourceHost 来源网址 host，允许为空并支持模糊匹配
     * @param status     状态，允许为空
     * @return 满足条件的记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_source_url
            WHERE deleted = 0
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="sourceUrl != null and sourceUrl != ''">AND source_url LIKE CONCAT('%', #{sourceUrl}, '%')</if>
            <if test="sourceHost != null and sourceHost != ''">AND source_host LIKE CONCAT('%', #{sourceHost}, '%')</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countSourceUrlRules(@Param("merchantId") String merchantId,
                             @Param("sourceUrl") String sourceUrl,
                             @Param("sourceHost") String sourceHost,
                             @Param("status") Integer status);

    /**
     * 分页查询商户来源网址限定。
     *
     * @param merchantId 商户号，允许为空
     * @param sourceUrl  商户录入 URL，允许为空并支持模糊匹配
     * @param sourceHost 来源网址 host，允许为空并支持模糊匹配
     * @param status     状态，允许为空
     * @param offset     分页偏移量
     * @param pageSize   每页记录数
     * @return 来源网址记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM risk_rule_source_url
            WHERE deleted = 0
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="sourceUrl != null and sourceUrl != ''">AND source_url LIKE CONCAT('%', #{sourceUrl}, '%')</if>
            <if test="sourceHost != null and sourceHost != ''">AND source_host LIKE CONCAT('%', #{sourceHost}, '%')</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectSourceUrlRulePage(@Param("merchantId") String merchantId,
                                                      @Param("sourceUrl") String sourceUrl,
                                                      @Param("sourceHost") String sourceHost,
                                                      @Param("status") Integer status,
                                                      @Param("offset") long offset,
                                                      @Param("pageSize") long pageSize);

    /**
     * 新增内风控规则。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param data      规则字段映射，金额字段使用 BigDecimal
     * @param operator  操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${tableName} (
                merchant_scope, merchant_id, rule_name, match_mode, match_value, limit_type,
                amount_min, amount_max, currency, time_window_seconds, threshold_count,
                elements_json, risk_level, decision_action, effective_time, expire_time,
                status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.merchantScope}, #{data.merchantId}, #{data.ruleName}, #{data.matchMode}, #{data.matchValue}, #{data.limitType},
                #{data.amountMin}, #{data.amountMax}, #{data.currency}, #{data.timeWindowSeconds}, #{data.thresholdCount},
                #{data.elementsJson}, #{data.riskLevel}, #{data.decisionAction}, #{data.effectiveTime}, #{data.expireTime},
                #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            """)
    int insertRule(@Param("tableName") String tableName,
                   @Param("data") Map<String, Object> data,
                   @Param("operator") String operator);

    /**
     * 新增商户交易限额。
     */
    @Insert("""
            INSERT INTO risk_rule_merchant_limit (
                merchant_scope, merchant_id, rule_name, match_value, limit_type, amount_min, amount_max, currency,
                risk_level, decision_action, effective_time, expire_time,
                status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.merchantScope}, #{data.merchantId}, #{data.ruleName}, #{data.matchValue}, #{data.limitType}, #{data.amountMin}, #{data.amountMax}, #{data.currency},
                #{data.riskLevel}, #{data.decisionAction}, #{data.effectiveTime}, #{data.expireTime},
                #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            """)
    int insertMerchantLimitRule(@Param("data") Map<String, Object> data,
                                @Param("operator") String operator);

    /**
     * 新增 3DS 规则。
     *
     * @param data     3DS 规则字段映射
     * @param operator 操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_rule_3ds (
                rule_group_no, merchant_scope, merchant_id, merchant_name, rule_name, rule_type,
                channel_code, payment_method, card_brand, amount_match_type, amount_min, amount_max, currency,
                risk_condition, trigger_action, priority, effective_time, expire_time,
                status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.ruleGroupNo}, #{data.merchantScope}, #{data.merchantId}, #{data.merchantName}, #{data.ruleName}, #{data.ruleType},
                #{data.channelCode}, #{data.paymentMethod}, #{data.cardBrand}, #{data.amountMatchType}, #{data.amountMin}, #{data.amountMax}, #{data.currency},
                #{data.riskCondition}, #{data.triggerAction}, #{data.priority}, #{data.effectiveTime}, #{data.expireTime},
                #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            """)
    int insertThreeDsRule(@Param("data") Map<String, Object> data,
                          @Param("operator") String operator);

    /**
     * 新增商户来源网址限定。
     *
     * @param data     来源网址字段映射
     * @param operator 操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_rule_source_url (
                merchant_id, source_url, source_host, risk_level, decision_action,
                effective_time, expire_time, status, remark, create_by, update_by, deleted
            ) VALUES (
                #{data.merchantId}, #{data.sourceUrl}, #{data.sourceHost}, #{data.riskLevel}, #{data.decisionAction},
                #{data.effectiveTime}, #{data.expireTime}, #{data.status}, #{data.remark}, #{operator}, #{operator}, 0
            )
            """)
    int insertSourceUrlRule(@Param("data") Map<String, Object> data,
                            @Param("operator") String operator);

    /**
     * 修改内风控规则。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @param id        规则记录ID
     * @param data      规则字段映射，金额字段使用 BigDecimal
     * @param operator  操作人
     * @return 影响行数
     */
    @Update("""
            UPDATE ${tableName}
            SET merchant_scope = #{data.merchantScope},
                merchant_id = #{data.merchantId},
                rule_name = #{data.ruleName},
                match_mode = #{data.matchMode},
                match_value = #{data.matchValue},
                limit_type = #{data.limitType},
                amount_min = #{data.amountMin},
                amount_max = #{data.amountMax},
                currency = #{data.currency},
                time_window_seconds = #{data.timeWindowSeconds},
                threshold_count = #{data.thresholdCount},
                elements_json = #{data.elementsJson},
                risk_level = #{data.riskLevel},
                decision_action = #{data.decisionAction},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int updateRule(@Param("tableName") String tableName,
                   @Param("id") Long id,
                   @Param("data") Map<String, Object> data,
                   @Param("operator") String operator);

    /**
     * 修改商户交易限额。
     */
    @Update("""
            UPDATE risk_rule_merchant_limit
            SET merchant_scope = #{data.merchantScope},
                merchant_id = #{data.merchantId},
                rule_name = #{data.ruleName},
                match_value = #{data.matchValue},
                limit_type = #{data.limitType},
                amount_min = #{data.amountMin},
                amount_max = #{data.amountMax},
                currency = #{data.currency},
                risk_level = #{data.riskLevel},
                decision_action = #{data.decisionAction},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int updateMerchantLimitRule(@Param("id") Long id,
                                @Param("data") Map<String, Object> data,
                                @Param("operator") String operator);

    /**
     * 修改 3DS 规则。
     *
     * @param id       规则记录ID
     * @param data     3DS 规则字段映射
     * @param operator 操作人
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_rule_3ds
            SET merchant_scope = #{data.merchantScope},
                merchant_id = #{data.merchantId},
                merchant_name = #{data.merchantName},
                rule_name = #{data.ruleName},
                rule_type = #{data.ruleType},
                channel_code = #{data.channelCode},
                payment_method = #{data.paymentMethod},
                card_brand = #{data.cardBrand},
                amount_match_type = #{data.amountMatchType},
                amount_min = #{data.amountMin},
                amount_max = #{data.amountMax},
                currency = #{data.currency},
                risk_condition = #{data.riskCondition},
                trigger_action = #{data.triggerAction},
                priority = #{data.priority},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int updateThreeDsRule(@Param("id") Long id,
                          @Param("data") Map<String, Object> data,
                          @Param("operator") String operator);

    /**
     * 修改商户来源网址限定。
     *
     * @param id       来源网址记录ID
     * @param data     来源网址字段映射
     * @param operator 操作人
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_rule_source_url
            SET merchant_id = #{data.merchantId},
                source_url = #{data.sourceUrl},
                source_host = #{data.sourceHost},
                risk_level = #{data.riskLevel},
                decision_action = #{data.decisionAction},
                effective_time = #{data.effectiveTime},
                expire_time = #{data.expireTime},
                status = #{data.status},
                remark = #{data.remark},
                update_by = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int updateSourceUrlRule(@Param("id") Long id,
                            @Param("data") Map<String, Object> data,
                            @Param("operator") String operator);

    /**
     * 写入配置变更日志。
     *
     * @param moduleType     模块类型
     * @param functionCode   功能编码
     * @param businessId     业务记录ID，批量导入时允许为空
     * @param operationType  操作类型
     * @param beforeSnapshot 修改前快照，允许为空
     * @param afterSnapshot  修改后快照，允许为空
     * @param operator       操作人
     * @param remark         操作说明
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_config_change_log (
                module_type, function_code, business_id, operation_type, before_snapshot,
                after_snapshot, operator, remark
            ) VALUES (
                #{moduleType}, #{functionCode}, #{businessId}, #{operationType}, #{beforeSnapshot},
                #{afterSnapshot}, #{operator}, #{remark}
            )
            """)
    int insertChangeLog(@Param("moduleType") String moduleType,
                        @Param("functionCode") String functionCode,
                        @Param("businessId") Long businessId,
                        @Param("operationType") String operationType,
                        @Param("beforeSnapshot") String beforeSnapshot,
                        @Param("afterSnapshot") String afterSnapshot,
                        @Param("operator") String operator,
                        @Param("remark") String remark);

    /**
     * 查询配置变更日志。
     *
     * @param offset   分页偏移量
     * @param pageSize 每页记录数
     * @return 配置变更日志列表
     */
    @Select("""
            SELECT *
            FROM risk_config_change_log
            ORDER BY operation_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<Map<String, Object>> selectChangeLogs(@Param("offset") long offset, @Param("pageSize") long pageSize);

    /**
     * 查询配置变更日志总数。
     *
     * @return 配置变更日志总数
     */
    @Select("SELECT COUNT(1) FROM risk_config_change_log")
    long countChangeLogs();

    /**
     * 查询今日风险事件。
     *
     * @param limit 返回数量上限
     * @return 当日风控评估记录
     */
    @Select("""
            SELECT risk_record_no,
                   merchant_id,
                   merchant_name,
                   merchant_order_no,
                   payment_order_no,
                   transaction_amount,
                   transaction_currency,
                   risk_level,
                   decision_result,
                   decision_reason,
                   hit_count,
                   evaluation_time
            FROM risk_evaluation_record
            WHERE evaluation_time >= CURRENT_DATE()
              AND evaluation_time < DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY)
            ORDER BY evaluation_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectTodayRiskEvents(@Param("limit") int limit);

    /**
     * 查询近 30 天高风险商户排行。
     *
     * @param limit 返回数量上限
     * @return 商户风险统计
     */
    @Select("""
            SELECT COALESCE(NULLIF(merchant_id, ''), '-') AS merchant_id,
                   COALESCE(NULLIF(merchant_name, ''), '-') AS merchant_name,
                   COUNT(1) AS risk_count,
                   SUM(CASE WHEN risk_level IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END) AS high_risk_count,
                   SUM(CASE WHEN decision_result = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,
                   MAX(evaluation_time) AS latest_evaluation_time
            FROM risk_evaluation_record
            WHERE evaluation_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY)
            GROUP BY COALESCE(NULLIF(merchant_id, ''), '-'), COALESCE(NULLIF(merchant_name, ''), '-')
            ORDER BY high_risk_count DESC, risk_count DESC, latest_evaluation_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectMerchantRiskRanking(@Param("limit") int limit);

    /**
     * 查询基础字典项。
     *
     * @param dictType 字典类型
     * @param locale   语言环境
     * @return 字典下拉选项
     */
    @Select("""
            SELECT dict_label AS label, dict_value AS value, list_class AS extra
            FROM sys_dict_data
            WHERE dict_type = #{dictType}
              AND locale = #{locale}
              AND status = 1
              AND deleted = 0
            ORDER BY dict_sort ASC, id ASC
            """)
    List<Map<String, Object>> selectDictOptions(@Param("dictType") String dictType, @Param("locale") String locale);

    /**
     * 查询国家地区下拉。
     *
     * @return 国家地区下拉选项
     */
    @Select("""
            SELECT COALESCE(chinese_name, short_english_name, alpha2_code) AS label,
                   alpha2_code AS value,
                   alpha3_code AS extra,
                   numeric_code AS numericCode,
                   flag_emoji AS flagEmoji,
                   continent_code AS continentCode,
                   continent_name AS continentName
            FROM base_iso_country
            WHERE status = 1 AND deleted = 0
            ORDER BY continent_code ASC, alpha2_code ASC
            """)
    List<Map<String, Object>> selectCountryOptions();

    /**
     * 查询单个国家或地区元数据。
     *
     * @param alpha2 国家或地区 Alpha-2 编码
     * @return 国家或地区元数据；不存在时返回空
     */
    @Select("""
            SELECT COALESCE(chinese_name, short_english_name, alpha2_code) AS label,
                   alpha2_code AS value,
                   alpha3_code AS extra,
                   numeric_code AS numericCode,
                   flag_emoji AS flagEmoji,
                   continent_code AS continentCode,
                   continent_name AS continentName
            FROM base_iso_country
            WHERE alpha2_code = #{alpha2}
              AND status = 1
              AND deleted = 0
            LIMIT 1
            """)
    Map<String, Object> selectCountryOptionByAlpha2(@Param("alpha2") String alpha2);

    /**
     * 根据 Alpha-3 查询单个国家或地区元数据。
     *
     * @param alpha3 国家或地区 Alpha-3 编码
     * @return 国家或地区元数据；不存在时返回空
     */
    @Select("""
            SELECT COALESCE(chinese_name, short_english_name, alpha2_code) AS label,
                   alpha2_code AS value,
                   alpha3_code AS extra,
                   numeric_code AS numericCode,
                   flag_emoji AS flagEmoji,
                   continent_code AS continentCode,
                   continent_name AS continentName
            FROM base_iso_country
            WHERE alpha3_code = #{alpha3}
              AND status = 1
              AND deleted = 0
            LIMIT 1
            """)
    Map<String, Object> selectCountryOptionByAlpha3(@Param("alpha3") String alpha3);

    /**
     * 查询币种下拉。
     *
     * @return 币种下拉选项
     */
    @Select("""
            SELECT COALESCE(chinese_name, english_name, alpha3_code) AS label,
                   alpha3_code AS value,
                   numeric_code AS extra
            FROM base_iso_currency
            WHERE status = 1 AND deleted = 0
            ORDER BY alpha3_code ASC
            """)
    List<Map<String, Object>> selectCurrencyOptions();

    /**
     * 查询商户名称。
     *
     * @param merchantId 商户号
     * @return 商户名称；不存在时返回空
     */
    @Select("""
            SELECT merchant_name
            FROM base_merchant_info
            WHERE merchant_id = #{merchantId} AND deleted = 0
            LIMIT 1
            """)
    String selectMerchantName(@Param("merchantId") String merchantId);

    /**
     * 查询指定风控配置表总览统计。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition 白名单
     * @return 未删除总数、启用数量和最近更新时间
     */
    @Select("""
            SELECT COUNT(1) AS total,
                   COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS enabled,
                   MAX(update_time) AS latest_update_time
            FROM ${tableName}
            WHERE deleted = 0
            """)
    Map<String, Object> selectDashboardStats(@Param("tableName") String tableName);

    /**
     * 查询指定风控功能最近一条配置变更。
     *
     * @param moduleType   模块类型
     * @param functionCode 功能编码
     * @return 最近配置变更；没有变更日志时返回空
     */
    @Select("""
            SELECT operation_type,
                   operator,
                   operation_time
            FROM risk_config_change_log
            WHERE module_type = #{moduleType}
              AND function_code = #{functionCode}
            ORDER BY operation_time DESC, id DESC
            LIMIT 1
            """)
    Map<String, Object> selectLatestChangeLog(@Param("moduleType") String moduleType,
                                              @Param("functionCode") String functionCode);

    /**
     * 查询风控评估总数。
     *
     * @param merchantId      商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo  平台支付订单号，允许为空
     * @param decisionResult  决策结果，允许为空
     * @param riskLevel 风险等级，允许为空
     * @param evaluationStartTime 评估时间起点（包含），允许为空
     * @param evaluationEndTimeExclusive 评估时间终点（不包含），允许为空
     * @return 风控评估记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_evaluation_record
            WHERE 1 = 1
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="decisionResult != null and decisionResult != ''">AND decision_result = #{decisionResult}</if>
            <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
            <if test="evaluationStartTime != null">AND evaluation_time &gt;= #{evaluationStartTime}</if>
            <if test="evaluationEndTimeExclusive != null">AND evaluation_time &lt; #{evaluationEndTimeExclusive}</if>
            </script>
            """)
    long countEvaluations(@Param("merchantId") String merchantId,
                          @Param("merchantOrderNo") String merchantOrderNo,
                          @Param("paymentOrderNo") String paymentOrderNo,
                          @Param("decisionResult") String decisionResult,
                          @Param("riskLevel") String riskLevel,
                          @Param("evaluationStartTime") LocalDateTime evaluationStartTime,
                          @Param("evaluationEndTimeExclusive") LocalDateTime evaluationEndTimeExclusive);

    /**
     * 分页查询风控评估记录。
     *
     * @param merchantId      商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo  平台支付订单号，允许为空
     * @param decisionResult  决策结果，允许为空
     * @param riskLevel       风险等级，允许为空
     * @param evaluationStartTime 评估时间起点（包含），允许为空
     * @param evaluationEndTimeExclusive 评估时间终点（不包含），允许为空
     * @param offset          分页偏移量
     * @param pageSize        每页记录数
     * @return 风控评估记录列表
     */
    @Select("""
            <script>
            SELECT id,
                   risk_record_no,
                   merchant_id,
                   merchant_name,
                   merchant_order_no,
                   payment_order_no,
                   transaction_amount,
                   transaction_currency,
                   risk_level,
                   decision_result,
                   decision_reason,
                   hit_count,
                   evaluation_time
            FROM risk_evaluation_record
            WHERE 1 = 1
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="decisionResult != null and decisionResult != ''">AND decision_result = #{decisionResult}</if>
            <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
            <if test="evaluationStartTime != null">AND evaluation_time &gt;= #{evaluationStartTime}</if>
            <if test="evaluationEndTimeExclusive != null">AND evaluation_time &lt; #{evaluationEndTimeExclusive}</if>
            ORDER BY evaluation_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectEvaluations(@Param("merchantId") String merchantId,
                                                @Param("merchantOrderNo") String merchantOrderNo,
                                                @Param("paymentOrderNo") String paymentOrderNo,
                                                @Param("decisionResult") String decisionResult,
                                                @Param("riskLevel") String riskLevel,
                                                @Param("evaluationStartTime") LocalDateTime evaluationStartTime,
                                                @Param("evaluationEndTimeExclusive") LocalDateTime evaluationEndTimeExclusive,
                                                @Param("offset") long offset,
                                                @Param("pageSize") long pageSize);

    /**
     * 查询风控评估命中明细。
     *
     * @param riskRecordNo 风控记录号
     * @return 命中明细列表
     */
    @Select("""
            SELECT *
            FROM risk_evaluation_hit_detail
            WHERE risk_record_no = #{riskRecordNo}
            ORDER BY COALESCE(stage_order, 9999) ASC, decision_time ASC, id ASC
            """)
    List<Map<String, Object>> selectEvaluationHits(@Param("riskRecordNo") String riskRecordNo);

    /**
     * 查询交易加黑总数。
     *
     * @param merchantId      商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo  平台支付订单号，允许为空
     * @param blackTargetType 加黑对象类型，允许为空
     * @param status          状态，允许为空
     * @return 交易加黑记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_trade_black_record
            WHERE deleted = 0
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="blackTargetType != null and blackTargetType != ''">AND black_target_type = #{blackTargetType}</if>
            <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countTradeBlack(@Param("merchantId") String merchantId,
                         @Param("merchantOrderNo") String merchantOrderNo,
                         @Param("paymentOrderNo") String paymentOrderNo,
                         @Param("blackTargetType") String blackTargetType,
                         @Param("status") Integer status);

    /**
     * 分页查询交易加黑。
     *
     * @param merchantId      商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo  平台支付订单号，允许为空
     * @param blackTargetType 加黑对象类型，允许为空
     * @param status          状态，允许为空
     * @param offset          分页偏移量
     * @param pageSize        每页记录数
     * @return 交易加黑记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM risk_trade_black_record
            WHERE deleted = 0
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="blackTargetType != null and blackTargetType != ''">AND black_target_type = #{blackTargetType}</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectTradeBlack(@Param("merchantId") String merchantId,
                                               @Param("merchantOrderNo") String merchantOrderNo,
                                               @Param("paymentOrderNo") String paymentOrderNo,
                                               @Param("blackTargetType") String blackTargetType,
                                               @Param("status") Integer status,
                                               @Param("offset") long offset,
                                               @Param("pageSize") long pageSize);

    /**
     * 新增交易加黑记录。
     *
     * @param data     交易加黑字段映射，敏感对象只允许写入脱敏值或哈希
     * @param operator 操作人
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_trade_black_record (
                merchant_id, merchant_name, merchant_order_no, payment_order_no, black_target_type,
                black_target_value_masked, black_target_hash, source_type, action_type,
                action_reason, status, operator, deleted
            ) VALUES (
                #{data.merchantId}, #{data.merchantName}, #{data.merchantOrderNo}, #{data.paymentOrderNo}, #{data.blackTargetType},
                #{data.blackTargetValueMasked}, #{data.blackTargetHash}, #{data.sourceType}, #{data.actionType},
                #{data.actionReason}, #{data.status}, #{operator}, 0
            )
            """)
    int insertTradeBlack(@Param("data") Map<String, Object> data, @Param("operator") String operator);

    /**
     * 解除交易加黑。
     *
     * @param id       交易加黑记录ID
     * @param reason   解除原因，允许为空
     * @param operator 操作人
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_trade_black_record
            SET status = 0,
                action_type = 'RELEASE',
                action_reason = #{reason},
                operator = #{operator},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted = 0
            """)
    int releaseTradeBlack(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);
}

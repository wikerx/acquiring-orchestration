package com.scott.payment.risk.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuleSnapshotRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控运行时 Mapper。
 */
public interface RiskRuntimeMapper {

    /**
     * 统计受控名单表中当前商户可用的启用规则。
     *
     * @param tableName 已通过 {@link com.scott.payment.risk.domain.RiskListFunction} 白名单校验的表名
     * @param merchantId 当前商户号
     * @return 商户级和适用全局规则的总数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            </script>
            """)
    long countActiveListRules(@Param("tableName") String tableName,
                              @Param("merchantId") String merchantId);

    /**
     * 加载精确哈希名单的完整有效快照。
     *
     * @param tableName    已通过 RiskListFunction 白名单校验的物理表
     * @param moduleType   WHITE、BLACK 或 AML
     * @param functionCode 稳定功能编码
     * @param functionName 功能展示名称
     * @param hitElement   参与匹配的交易元素
     * @param merchantId   当前商户号
     * @param maxRows      查询硬上限，调用方使用配置上限加一识别大集合
     * @return 商户级优先、更新时间倒序的完整有效行
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   match_value_hash AS matchValueHash
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveHashSnapshotRows(
            @Param("tableName") String tableName,
            @Param("moduleType") String moduleType,
            @Param("functionCode") String functionCode,
            @Param("functionName") String functionName,
            @Param("hitElement") String hitElement,
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 加载 IP 区间名单的完整有效快照。
     *
     * @return 商户级优先、更新时间倒序的有界区间行
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   ip_version AS ipVersion,
                   match_value_start_number AS matchValueStartNumber,
                   match_value_end_number AS matchValueEndNumber
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveIpRangeSnapshotRows(
            @Param("tableName") String tableName,
            @Param("moduleType") String moduleType,
            @Param("functionCode") String functionCode,
            @Param("functionName") String functionName,
            @Param("hitElement") String hitElement,
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 加载卡 BIN 区间名单的完整有效快照。
     *
     * @return 商户级优先、更新时间倒序的有界 BIN 区间行
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   match_value_start_number AS matchValueStartNumber,
                   match_value_end_number AS matchValueEndNumber
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveCardBinSnapshotRows(
            @Param("tableName") String tableName,
            @Param("moduleType") String moduleType,
            @Param("functionCode") String functionCode,
            @Param("functionName") String functionName,
            @Param("hitElement") String hitElement,
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 加载国家或地区等值名单的完整有效快照。
     *
     * @return 商户级优先、更新时间倒序的有界国家规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   country_alpha3 AS countryAlpha3
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveCountrySnapshotRows(
            @Param("tableName") String tableName,
            @Param("moduleType") String moduleType,
            @Param("functionCode") String functionCode,
            @Param("functionName") String functionName,
            @Param("hitElement") String hitElement,
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 加载高风险地域的完整有效快照。
     *
     * @return 商户级和地域具体程度均从高到低排列的有界地域规则
     */
    @Select("""
            SELECT id AS ruleId,
                   'BLACK' AS moduleType,
                   'region' AS functionCode,
                   '高风险区域黑名单' AS functionName,
                   'region' AS hitElement,
                   CONCAT_WS('/', country_alpha3, NULLIF(state_province_name, ''), NULLIF(city_name, ''))
                       AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, '高风险区域黑名单') AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   region_match_level AS regionMatchLevel,
                   country_alpha3 AS countryAlpha3,
                   state_province_name AS stateProvinceName,
                   city_name AS cityName
            FROM risk_black_region
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     CASE region_match_level WHEN 'CITY' THEN 0 WHEN 'STATE' THEN 1 ELSE 2 END,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            """)
    List<RiskRuleSnapshotRow> selectActiveRegionSnapshotRows(
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 加载 AML 来源主机的完整有效快照。
     *
     * @return 全局有效来源主机规则，按更新时间倒序排列
     */
    @Select("""
            SELECT id AS ruleId,
                   'AML' AS moduleType,
                   'sourceUrl' AS functionCode,
                   '来源网址AML' AS functionName,
                   'sourceUrl' AS hitElement,
                   source_host AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, '来源网址AML') AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   source_host AS sourceHost
            FROM risk_aml_source_url
            WHERE deleted = 0
              AND status = 1
              AND source_host IS NOT NULL
              AND source_host <> ''
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY update_time DESC, id DESC
            LIMIT #{maxRows}
            """)
    List<RiskRuleSnapshotRow> selectActiveAmlSourceHostSnapshotRows(@Param("maxRows") int maxRows);

    /**
     * 按敏感值哈希查询当前商户适用的名单规则，商户级规则优先于全局规则。
     *
     * @param tableName 已通过 {@link com.scott.payment.risk.domain.RiskListFunction} 白名单校验的表名
     * @param moduleType 风控模块类型，用于组装统一命中结果
     * @param functionCode 名单功能编码
     * @param functionName 名单功能名称
     * @param hitElement 参与匹配的交易要素名称
     * @param merchantId 当前商户号
     * @param matchValueHash 规范化敏感值的不可逆哈希，禁止传入明文卡号等敏感数据
     * @return 优先级最高的有效规则；未命中时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND match_value_hash = #{matchValueHash}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END, update_time DESC, id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectHashMatch(@Param("tableName") String tableName,
                                  @Param("moduleType") String moduleType,
                                  @Param("functionCode") String functionCode,
                                  @Param("functionName") String functionName,
                                  @Param("hitElement") String hitElement,
                                  @Param("merchantId") String merchantId,
                                  @Param("matchValueHash") String matchValueHash);

    /**
     * 按 IP 版本和无符号整数区间查询当前商户适用的名单规则。
     *
     * @param tableName 已通过 {@link com.scott.payment.risk.domain.RiskListFunction} 白名单校验的表名
     * @param moduleType 风控模块类型，用于组装统一命中结果
     * @param functionCode 名单功能编码
     * @param functionName 名单功能名称
     * @param hitElement 参与匹配的 IP 要素名称
     * @param merchantId 当前商户号
     * @param ipVersion 规范化 IP 版本，取值为 IPv4 或 IPv6
     * @param numericValue IP 地址对应的无符号整数，避免文本格式差异影响区间判断
     * @return 优先级最高的有效 IP 区间规则；未命中时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND ip_version = #{ipVersion}
              AND match_value_start_number &lt;= #{numericValue}
              AND match_value_end_number &gt;= #{numericValue}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END, update_time DESC, id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectIpRangeMatch(@Param("tableName") String tableName,
                                     @Param("moduleType") String moduleType,
                                     @Param("functionCode") String functionCode,
                                     @Param("functionName") String functionName,
                                     @Param("hitElement") String hitElement,
                                     @Param("merchantId") String merchantId,
                                     @Param("ipVersion") String ipVersion,
                                     @Param("numericValue") BigDecimal numericValue);

    /**
     * 按规范化卡 BIN 数值区间查询当前商户适用的名单规则。
     *
     * @param tableName 已通过 {@link com.scott.payment.risk.domain.RiskListFunction} 白名单校验的表名
     * @param moduleType 风控模块类型，用于组装统一命中结果
     * @param functionCode 名单功能编码
     * @param functionName 名单功能名称
     * @param hitElement 参与匹配的卡 BIN 要素名称
     * @param merchantId 当前商户号
     * @param numericValue 补齐到固定长度的 BIN 数值，不得传入完整卡号
     * @return 优先级最高的有效 BIN 区间规则；未命中时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND match_value_start_number &lt;= #{numericValue}
              AND match_value_end_number &gt;= #{numericValue}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END, update_time DESC, id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectCardBinRangeMatch(@Param("tableName") String tableName,
                                          @Param("moduleType") String moduleType,
                                          @Param("functionCode") String functionCode,
                                          @Param("functionName") String functionName,
                                          @Param("hitElement") String hitElement,
                                          @Param("merchantId") String merchantId,
                                          @Param("numericValue") BigDecimal numericValue);

    /**
     * 在受控名单表中按 ISO alpha-3 代码查询优先级最高的国家规则。
     *
     * @return 商户级规则优先于全局规则的首条命中；未命中时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   match_value_masked AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM ${tableName}
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND country_alpha3 = #{countryAlpha3}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END, update_time DESC, id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectCountryMatch(@Param("tableName") String tableName,
                                     @Param("moduleType") String moduleType,
                                     @Param("functionCode") String functionCode,
                                     @Param("functionName") String functionName,
                                     @Param("hitElement") String hitElement,
                                     @Param("merchantId") String merchantId,
                                     @Param("countryAlpha3") String countryAlpha3);

    /**
     * 按国家、州省、城市逐级查询最具体的高风险区域规则。
     *
     * @return 城市级优先于州省级和国家级的首条命中；未命中时返回 {@code null}
     */
    @Select("""
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   CONCAT_WS('/',
                       country_alpha3,
                       NULLIF(state_province_name, ''),
                       NULLIF(city_name, '')) AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM risk_black_region
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND country_alpha3 = #{countryAlpha3}
              AND (
                    region_match_level = 'COUNTRY'
                    OR (region_match_level = 'STATE'
                        AND #{stateProvinceName} IS NOT NULL
                        AND state_province_name = #{stateProvinceName})
                    OR (region_match_level = 'CITY'
                        AND #{stateProvinceName} IS NOT NULL
                        AND #{cityName} IS NOT NULL
                        AND state_province_name = #{stateProvinceName}
                        AND city_name = #{cityName})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     CASE region_match_level
                         WHEN 'CITY' THEN 0
                         WHEN 'STATE' THEN 1
                         ELSE 2
                     END,
                     update_time DESC,
                     id DESC
            LIMIT 1
            """)
    RiskListMatch selectRegionMatch(@Param("moduleType") String moduleType,
                                    @Param("functionCode") String functionCode,
                                    @Param("functionName") String functionName,
                                    @Param("hitElement") String hitElement,
                                    @Param("merchantId") String merchantId,
                                    @Param("countryAlpha3") String countryAlpha3,
                                    @Param("stateProvinceName") String stateProvinceName,
                                    @Param("cityName") String cityName);

    /**
     * 按规范化来源主机名查询启用的 AML 来源网址规则。
     *
     * @return 最新一条 AML 命中；未命中时返回 {@code null}
     */
    @Select("""
            SELECT id AS ruleId,
                   #{moduleType} AS moduleType,
                   #{functionCode} AS functionCode,
                   #{functionName} AS functionName,
                   #{hitElement} AS hitElement,
                   source_host AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, #{functionName}) AS decisionReason
            FROM risk_aml_source_url
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND source_host = #{sourceHost}
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    RiskListMatch selectAmlSourceHostMatch(@Param("moduleType") String moduleType,
                                           @Param("functionCode") String functionCode,
                                           @Param("functionName") String functionName,
                                           @Param("hitElement") String hitElement,
                                           @Param("sourceHost") String sourceHost);

    /**
     * 查询当前商户与来源主机完全匹配的启用网址限制规则。
     *
     * @return 最新一条商户来源网址规则；未命中时返回 {@code null}
     */
    @Select("""
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'sourceUrl' AS functionCode,
                   '商户来源网址限定' AS functionName,
                   'sourceUrl' AS hitElement,
                   source_host AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, '商户来源网址限定') AS decisionReason
            FROM risk_rule_source_url
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND merchant_id = #{merchantId}
              AND source_host = #{sourceHost}
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    RiskListMatch selectSourceUrlRule(@Param("merchantId") String merchantId,
                                      @Param("sourceHost") String sourceHost);

    /**
     * 加载当前商户全部有效来源网址允许规则。
     *
     * @param merchantId 当前商户号
     * @param maxRows    查询硬上限
     * @return 按更新时间倒序的来源主机快照行
     */
    @Select("""
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'sourceUrl' AS functionCode,
                   '商户来源网址限定' AS functionName,
                   'sourceUrl' AS hitElement,
                   source_host AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, '商户来源网址限定') AS decisionReason,
                   'MERCHANT' AS merchantScope,
                   merchant_id AS merchantId,
                   source_host AS sourceHost
            FROM risk_rule_source_url
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND merchant_id = #{merchantId}
            ORDER BY update_time DESC, id DESC
            LIMIT #{maxRows}
            """)
    List<RiskRuleSnapshotRow> selectActiveSourceUrlSnapshotRows(
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 统计当前商户处于有效期内的来源网址限制规则。
     *
     * @param merchantId 当前商户号
     * @return 启用且有效的规则数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM risk_rule_source_url
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND merchant_id = #{merchantId}
            """)
    long countActiveSourceUrlRules(@Param("merchantId") String merchantId);

    /**
     * 统计当前主机命中的有效来源网址限制规则。
     *
     * @param merchantId 当前商户号
     * @param sourceHost 已规范化为 ASCII 小写形式的主机名
     * @return 当前主机的有效命中数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM risk_rule_source_url
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND merchant_id = #{merchantId}
              AND source_host = #{sourceHost}
            """)
    long countActiveSourceUrlHit(@Param("merchantId") String merchantId,
                                 @Param("sourceHost") String sourceHost);

    /**
     * 读取商户 OpenAPI 访问配置中的 IP 白名单开关。
     *
     * @param merchantId 当前商户号
     * @return 数据库开关值；尚未配置时返回 {@code null}
     */
    @Select("""
            SELECT ip_whitelist_enabled
            FROM merchant_openapi_access_config
            WHERE deleted = 0
              AND merchant_id = #{merchantId}
            ORDER BY gmt_modified DESC, id DESC
            LIMIT 1
            """)
    Integer selectMerchantIpWhitelistEnabled(@Param("merchantId") String merchantId);

    /**
     * 统计商户当前启用的精确 IP 白名单记录。
     *
     * @param merchantId 当前商户号
     * @return 启用记录数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM merchant_ip_whitelist
            WHERE deleted = 0
              AND status = 1
              AND merchant_id = #{merchantId}
            """)
    long countActiveMerchantIpWhitelist(@Param("merchantId") String merchantId);

    /**
     * 统计商户 IP 白名单中与规范地址完全相等的记录。
     *
     * @param merchantId 当前商户号
     * @param ipValue 已规范化的 IPv4 或 IPv6 地址
     * @return 精确命中数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM merchant_ip_whitelist
            WHERE deleted = 0
              AND status = 1
              AND merchant_id = #{merchantId}
              AND ip_value = #{ipValue}
            """)
    long countMerchantIpWhitelistHit(@Param("merchantId") String merchantId,
                                     @Param("ipValue") String ipValue);

    /**
     * 查询本次交易触发的单笔最低或最高金额限制。
     *
     * @param merchantId 当前商户号
     * @param amount 交易金额
     * @param currency ISO 4217 币种代码
     * @return 商户级优先的首条超限规则；未超限时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'merchantLimit' AS functionCode,
                   '商户交易限额管理' AS functionName,
                   limit_type AS hitElement,
                   CONCAT(limit_type, ':', COALESCE(amount_min, amount_max)) AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND currency = #{currency}
              AND (
                    (limit_type = 'SINGLE_MIN' AND amount_min IS NOT NULL AND #{amount} &lt; amount_min)
                    OR (limit_type = 'SINGLE_MAX' AND amount_max IS NOT NULL AND #{amount} &gt; amount_max)
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     CASE WHEN limit_type = 'SINGLE_MAX' THEN amount_max ELSE amount_min END ASC,
                     update_time DESC,
                     id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectMerchantLimitRule(@Param("merchantId") String merchantId,
                                          @Param("amount") BigDecimal amount,
                                          @Param("currency") String currency);

    /**
     * 加载当前商户和币种下全部有效限额规则。
     *
     * @param merchantId 当前商户号
     * @param currency   ISO 4217 Alpha-3 币种
     * @param maxRows    查询硬上限
     * @return 商户级优先且按从严顺序排列的限额规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'merchantLimit' AS functionCode,
                   '商户交易限额管理' AS functionName,
                   limit_type AS hitElement,
                   CONCAT(limit_type, ':', COALESCE(amount_min, amount_max)) AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   limit_type AS limitType,
                   amount_min AS amountMin,
                   amount_max AS amountMax,
                   currency AS currency,
                   CASE
                       WHEN limit_type = 'SINGLE_MIN' THEN amount_min
                       ELSE amount_max
                   END AS amountLimit
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND currency = #{currency}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     CASE WHEN limit_type = 'SINGLE_MIN' THEN amount_min ELSE amount_max END ASC,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveMerchantLimitSnapshotRows(
            @Param("merchantId") String merchantId,
            @Param("currency") String currency,
            @Param("maxRows") int maxRows);

    /**
     * 统计当前商户和币种下处于有效期内的金额限制规则。
     *
     * @param merchantId 当前商户号
     * @param currency ISO 4217 币种代码
     * @return 单笔及累计限额规则总数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND currency = #{currency}
            </script>
            """)
    long countActiveMerchantLimitRules(@Param("merchantId") String merchantId,
                                       @Param("currency") String currency);

    /**
     * 查询当前商户和币种的日、周、月累计限额规则。
     *
     * @return 按周期及限额从严排序的有效规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'merchantLimit' AS functionCode,
                   '商户交易限额管理' AS functionName,
                   limit_type AS hitElement,
                   CONCAT(limit_type, ':', amount_max) AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason,
                   amount_max AS amountLimit
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND status = 1
              AND limit_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
              AND amount_max IS NOT NULL
              AND amount_max &gt; 0
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND currency = #{currency}
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     FIELD(limit_type, 'DAILY', 'WEEKLY', 'MONTHLY'),
                     amount_max ASC,
                     update_time DESC,
                     id DESC
            </script>
            """)
    List<RiskListMatch> selectActiveCumulativeMerchantLimitRules(@Param("merchantId") String merchantId,
                                                                 @Param("currency") String currency);

    /**
     * 从主库受控交易分表汇总指定半开时间区间内已通过风控的交易金额。
     *
     * <p>排除当前交易的根交易号和最新交易号，避免补偿或重试重复计入。</p>
     *
     * @return 与交易金额同币种、同精度的累计金额；无记录时返回零
     */
    @DS(DataSourceName.MASTER)
    @Select("""
            <script>
            SELECT COALESCE(SUM(period_amount), 0)
            FROM (
                <foreach collection="tableNames" item="tableName" separator=" UNION ALL ">
                    SELECT COALESCE(SUM(transaction_amount), 0) AS period_amount
                    FROM ${tableName}
                    WHERE deleted = 0
                      AND merchant_id = #{merchantId}
                      AND transaction_currency = #{currency}
                      AND transaction_type IN ('PAYMENT', 'AUTHORIZATION', 'PRE_AUTHORIZATION')
                      AND internal_risk_decision IN ('PASS', 'SKIP')
                      AND transaction_status IN ('SUCCESS', 'PROCESSING', 'PENDING')
                      AND transaction_date_time &gt;= #{beginTime}
                      AND transaction_date_time &lt; #{endTime}
                      AND COALESCE(root_transaction_id, '') &lt;&gt; #{excludeTransactionId}
                      AND COALESCE(latest_transaction_id, '') &lt;&gt; #{excludeTransactionId}
                </foreach>
            ) period_totals
            </script>
            """)
    BigDecimal sumRiskApprovedTransactionAmount(@Param("tableNames") List<String> tableNames,
                                                @Param("merchantId") String merchantId,
                                                @Param("currency") String currency,
                                                @Param("beginTime") LocalDateTime beginTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("excludeTransactionId") String excludeTransactionId);

    /**
     * 从已校验的交易物理表查询指定交易的当前状态。
     *
     * @param physicalTableName 已由分表模板解析并校验的物理表名
     * @param transactionId 平台交易号
     * @return 交易状态；记录不存在时返回 {@code null}
     */
    @Select("""
            SELECT transaction_status
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            LIMIT 1
            """)
    String selectPaymentTransactionStatus(@Param("physicalTableName") String physicalTableName,
                                          @Param("transactionId") String transactionId);

    /**
     * 汇总同商户、规则、币种和周期桶内其他交易的有效预占金额。
     *
     * @return 六位小数定标后的整数金额单位合计；无记录时返回零
     */
    @Select("""
            SELECT COALESCE(SUM(amount_units), 0)
            FROM risk_merchant_limit_reservation
            WHERE deleted = 0
              AND merchant_id = #{merchantId}
              AND rule_id = #{ruleId}
              AND currency = #{currency}
              AND period_bucket = #{periodBucket}
              AND reservation_status IN ('RESERVED', 'CONFIRMED')
              AND transaction_id &lt;&gt; #{excludeTransactionId}
            """)
    Long sumLifecycleReservationAmountUnits(@Param("merchantId") String merchantId,
                                            @Param("ruleId") Long ruleId,
                                            @Param("currency") String currency,
                                            @Param("periodBucket") String periodBucket,
                                            @Param("excludeTransactionId") String excludeTransactionId);

    /**
     * 查询当前商户全部可执行的交易频率规则。
     *
     * @param merchantId 当前商户号
     * @return 按阈值和时间窗口从严排序的有效规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'frequency' AS functionCode,
                   '交易频率限定' AS functionName,
                   'frequency' AS hitElement,
                   CONCAT(COALESCE(time_window_seconds, 0), 's/', COALESCE(threshold_count, 0)) AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason,
                   time_window_seconds AS timeWindowSeconds,
                   threshold_count AS thresholdCount,
                   CAST(elements_json AS CHAR) AS elementsJson
            FROM risk_rule_frequency
            WHERE deleted = 0
              AND status = 1
              AND COALESCE(time_window_seconds, 0) > 0
              AND COALESCE(threshold_count, 0) > 0
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     threshold_count ASC,
                     time_window_seconds ASC,
                     update_time DESC,
                     id DESC
            </script>
            """)
    List<RiskListMatch> selectActiveFrequencyRules(@Param("merchantId") String merchantId);

    /**
     * 为常驻快照有界加载当前商户全部可执行频率规则。
     *
     * @param merchantId 当前商户号
     * @param maxRows    查询硬上限
     * @return 按阈值和窗口从严排序的有效规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'frequency' AS functionCode,
                   '交易频率限定' AS functionName,
                   'frequency' AS hitElement,
                   CONCAT(COALESCE(time_window_seconds, 0), 's/', COALESCE(threshold_count, 0))
                       AS hitValueMasked,
                   risk_level AS riskLevel,
                   decision_action AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason,
                   time_window_seconds AS timeWindowSeconds,
                   threshold_count AS thresholdCount,
                   CAST(elements_json AS CHAR) AS elementsJson
            FROM risk_rule_frequency
            WHERE deleted = 0
              AND status = 1
              AND COALESCE(time_window_seconds, 0) > 0
              AND COALESCE(threshold_count, 0) > 0
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     threshold_count ASC,
                     time_window_seconds ASC,
                     update_time DESC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskListMatch> selectActiveFrequencyRuleSnapshot(
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

    /**
     * 按规范化卡 BIN 数值查询最具体的发卡行国家。
     *
     * @param numericValue 右侧补零后的卡 BIN 比较值
     * @return BIN 长度和数据源优先级最高的记录；未命中时返回 {@code null}
     */
    @Select("""
            SELECT id AS ruleId,
                   'SYSTEM' AS moduleType,
                   'issuerCountry' AS functionCode,
                   '发卡行国家/地区解析' AS functionName,
                   'issuerCountry' AS hitElement,
                   issuer_country_alpha3 AS hitValueMasked,
                   'LOW' AS riskLevel,
                   'PASS' AS decisionAction,
                   'issuer country resolved by card bin' AS decisionReason
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND issuer_country_alpha3 IS NOT NULL
              AND issuer_country_alpha3 <> ''
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
              AND card_bin_start <= #{numericValue}
              AND card_bin_end >= #{numericValue}
            ORDER BY bin_length DESC, source_priority DESC, update_time DESC, id DESC
            LIMIT 1
            """)
    RiskListMatch selectIssuerCountryByCardBin(@Param("numericValue") BigDecimal numericValue);

    /**
     * 加载全部有效 BIN 发卡国家区间。
     *
     * @param maxRows 查询硬上限
     * @return BIN 长度和数据源优先级从高到低的区间行
     */
    @Select("""
            SELECT id AS ruleId,
                   'SYSTEM' AS moduleType,
                   'issuerCountry' AS functionCode,
                   '发卡行国家/地区解析' AS functionName,
                   'issuerCountry' AS hitElement,
                   issuer_country_alpha3 AS hitValueMasked,
                   'LOW' AS riskLevel,
                   'PASS' AS decisionAction,
                   'issuer country resolved by card bin' AS decisionReason,
                   card_bin_start AS matchValueStartNumber,
                   card_bin_end AS matchValueEndNumber,
                   bin_length AS binLength,
                   source_priority AS sourcePriority,
                   issuer_country_alpha3 AS issuerCountryAlpha3
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND issuer_country_alpha3 IS NOT NULL
              AND issuer_country_alpha3 <> ''
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY bin_length DESC, source_priority DESC, update_time DESC, id DESC
            LIMIT #{maxRows}
            """)
    List<RiskRuleSnapshotRow> selectActiveIssuerCountryBinSnapshotRows(@Param("maxRows") int maxRows);

    /**
     * 查询适用于交易维度、金额区间和当前风险等级的最高优先级 3DS 规则。
     *
     * @return 商户级优先的强制或跳过 3DS 规则；无适用规则时返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'threeDs' AS functionCode,
                   '3DS规则管理' AS functionName,
                   trigger_action AS hitElement,
                   CONCAT(trigger_action, ':', amount_match_type) AS hitValueMasked,
                   risk_condition AS riskLevel,
                   CASE WHEN trigger_action = 'FORCE_3DS' THEN 'REQUIRE_3DS'
                        WHEN trigger_action = 'SKIP_3DS' THEN 'PASS'
                        ELSE 'PASS'
                   END AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason
            FROM risk_rule_3ds
            WHERE deleted = 0
              AND status = 1
              AND trigger_action IN ('FORCE_3DS', 'SKIP_3DS')
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
              AND (payment_method = 'ALL' OR payment_method = #{paymentMethod})
              AND (card_brand = 'ALL' OR card_brand = #{cardBrand})
              AND currency = #{currency}
              AND (
                    amount_match_type = 'ALL'
                    OR (amount_match_type = 'GE' AND amount_min IS NOT NULL AND #{amount} &gt;= amount_min)
                    OR (amount_match_type = 'LE' AND amount_max IS NOT NULL AND #{amount} &lt;= amount_max)
                    OR (amount_match_type = 'BETWEEN' AND amount_min IS NOT NULL AND amount_max IS NOT NULL
                        AND #{amount} &gt;= amount_min AND #{amount} &lt;= amount_max)
                  )
              AND (
                    risk_condition = 'ANY'
                    OR (risk_condition = 'LOW_AND_ABOVE' AND #{currentRiskWeight} &gt;= 1)
                    OR (risk_condition = 'MEDIUM_AND_ABOVE' AND #{currentRiskWeight} &gt;= 2)
                    OR (risk_condition = 'HIGH_AND_ABOVE' AND #{currentRiskWeight} &gt;= 3)
                    OR (risk_condition = 'CRITICAL_ONLY' AND #{currentRiskWeight} &gt;= 4)
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     priority ASC,
                     id DESC
            LIMIT 1
            </script>
            """)
    RiskListMatch selectThreeDsRule(@Param("merchantId") String merchantId,
                                    @Param("paymentMethod") String paymentMethod,
                                    @Param("cardBrand") String cardBrand,
                                    @Param("amount") BigDecimal amount,
                                    @Param("currency") String currency,
                                    @Param("currentRiskWeight") int currentRiskWeight);

    /**
     * 加载当前商户全部有效 3DS 规则。
     *
     * @param merchantId 当前商户号
     * @param maxRows    查询硬上限
     * @return 商户级优先、优先级升序的完整 3DS 规则
     */
    @Select("""
            <script>
            SELECT id AS ruleId,
                   'RULE' AS moduleType,
                   'threeDs' AS functionCode,
                   '3DS规则管理' AS functionName,
                   trigger_action AS hitElement,
                   CONCAT(trigger_action, ':', amount_match_type) AS hitValueMasked,
                   risk_condition AS riskLevel,
                   CASE WHEN trigger_action = 'FORCE_3DS' THEN 'REQUIRE_3DS'
                        WHEN trigger_action = 'SKIP_3DS' THEN 'PASS'
                        ELSE 'PASS'
                   END AS decisionAction,
                   COALESCE(remark, rule_name) AS decisionReason,
                   merchant_scope AS merchantScope,
                   merchant_id AS merchantId,
                   payment_method AS paymentMethod,
                   card_brand AS cardBrand,
                   amount_match_type AS amountMatchType,
                   amount_min AS amountMin,
                   amount_max AS amountMax,
                   currency AS currency,
                   risk_condition AS riskCondition,
                   trigger_action AS triggerAction,
                   priority AS priority
            FROM risk_rule_3ds
            WHERE deleted = 0
              AND status = 1
              AND trigger_action IN ('FORCE_3DS', 'SKIP_3DS')
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time &gt; CURRENT_TIMESTAMP(3))
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (merchant_scope = 'MERCHANT' AND merchant_id = #{merchantId})
                  )
            ORDER BY CASE WHEN merchant_scope = 'MERCHANT' THEN 0 ELSE 1 END,
                     priority ASC,
                     id DESC
            LIMIT #{maxRows}
            </script>
            """)
    List<RiskRuleSnapshotRow> selectActiveThreeDsSnapshotRows(
            @Param("merchantId") String merchantId,
            @Param("maxRows") int maxRows);

}

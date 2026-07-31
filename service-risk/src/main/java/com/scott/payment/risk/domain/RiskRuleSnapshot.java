package com.scott.payment.risk.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskRuleSnapshot
 * @date : 2026-07-31 15:40
 * @email : scott_x@163.com
 * @description : 风控常驻快照信封，使用 generation 和 loaded 显式区分有效空集合、旧快照与缓存缺失
 * @status : create
 */
@Data
public class RiskRuleSnapshot {

    /** 构建快照时读取的风控规则 generation；不能为空。 */
    private String generation;

    /** 是否已完成数据库全量读取；有效空集合也必须为 true。 */
    private boolean loaded;

    /** 快照行数，单位条；必须与 rows 或 matches 的实际数量一致。 */
    private int count;

    /** 名单、来源网址、限额、3DS 或 BIN 区间的运行时快照行。 */
    private List<RiskRuleSnapshotRow> rows = new ArrayList<>();

    /** 频率和累计限额等已可直接执行的统一规则集合。 */
    private List<RiskListMatch> matches = new ArrayList<>();

    /**
     * 创建名单或条件规则快照。
     *
     * @param generation 构建时的规则代际
     * @param rows       数据库返回的完整有效行，允许为空
     * @return 已加载且可表达空集合的快照
     */
    public static RiskRuleSnapshot rows(String generation, List<RiskRuleSnapshotRow> rows) {
        RiskRuleSnapshot snapshot = new RiskRuleSnapshot();
        List<RiskRuleSnapshotRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
        snapshot.setGeneration(generation);
        snapshot.setLoaded(true);
        snapshot.setCount(safeRows.size());
        snapshot.setRows(safeRows);
        return snapshot;
    }

    /**
     * 创建可直接执行的统一规则集合快照。
     *
     * @param generation 构建时的规则代际
     * @param matches    数据库返回的完整有效规则，允许为空
     * @return 已加载且可表达空集合的快照
     */
    public static RiskRuleSnapshot matches(String generation, List<RiskListMatch> matches) {
        RiskRuleSnapshot snapshot = new RiskRuleSnapshot();
        List<RiskListMatch> safeMatches = matches == null ? List.of() : List.copyOf(matches);
        snapshot.setGeneration(generation);
        snapshot.setLoaded(true);
        snapshot.setCount(safeMatches.size());
        snapshot.setMatches(safeMatches);
        return snapshot;
    }
}

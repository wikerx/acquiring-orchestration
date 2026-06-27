package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantFormOptionsDTO
 * @date : 2026-06-27 20:00
 * @email : scott_x@163.com
 * @description : 管理后台商户新增和编辑表单选项响应
 * @status : create
 *
 * <p>承载商户资料表单选择器所需的 MCC、国家/地区和结算币种基础数据，避免管理端接口直接暴露
 * 数据库实体或让前端依赖数据库字段结构。</p>
 */
@Data
public class AdminMerchantFormOptionsDTO {

    /**
     * MCC 三级级联选项：一级分类、二级分类、MCC 编码；叶子节点 value 为最终保存的 MCC code。
     */
    private List<OptionNode> mccOptions = new ArrayList<>();

    /**
     * 国家/地区下拉选项，value 使用 ISO 3166-1 alpha-3 代码，例如 USA。
     */
    private List<OptionItem> countries = new ArrayList<>();

    /**
     * 结算币种下拉选项，value 使用 ISO 4217 alpha-3 代码，例如 USD。
     */
    private List<OptionItem> currencies = new ArrayList<>();

    /**
     * MCC 级联选择节点。
     *
     * <p>父级节点的 value 带层级前缀，只用于前端级联定位；叶子节点的 value 使用 MCC code，
     * 用于商户资料保存。</p>
     */
    @Data
    public static class OptionNode {

        /**
         * 节点值；一级、二级分类为内部级联定位值，MCC 叶子节点为四位 MCC code。
         */
        private String value;

        /**
         * 兼容展示标签；前端可根据 nameCn/nameEn 按当前语言重新生成展示文案。
         */
        private String label;

        /**
         * 节点中文名称，允许为空。
         */
        private String nameCn;

        /**
         * 节点英文名称，允许为空。
         */
        private String nameEn;

        /**
         * 下级节点；MCC 叶子节点为空集合。
         */
        private List<OptionNode> children = new ArrayList<>();
    }

    /**
     * 普通下拉选项。
     *
     * <p>用于国家/地区和结算币种选择器，value 始终使用标准三位字母代码。</p>
     */
    @Data
    public static class OptionItem {

        /**
         * 选项值；国家/地区使用 ISO alpha-3，币种使用 ISO 4217 alpha-3。
         */
        private String value;

        /**
         * 兼容展示标签；前端可根据 nameCn/nameEn 和币种精度字段重新生成展示文案。
         */
        private String label;

        /**
         * 中文名称，允许为空。
         */
        private String nameCn;

        /**
         * 英文名称，允许为空。
         */
        private String nameEn;

        /**
         * 币种默认辅币位；仅币种选项有值，小于 0 表示无可靠辅币位定义。
         */
        private Integer fractionDigits;

        /**
         * 币种最小金额单位；仅币种选项有值，例如 USD 为 0.01。
         */
        private BigDecimal minimumAmount;
    }
}

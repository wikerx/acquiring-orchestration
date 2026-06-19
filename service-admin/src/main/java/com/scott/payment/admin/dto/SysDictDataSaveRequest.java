package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataSaveRequest
 * @date : 2026-06-19 22:18
 * @email : scott_x@163.com
 * @description : 管理后台字典项保存请求 DTO
 * @status : create
 *
 * <p>用于字典项新增和更新，字典项必须绑定已有的 dictType，
 * 同一个页面上下文中不应随意切换所属字典类型。</p>
 */
@Data
public class SysDictDataSaveRequest {

    /**
     * 字典类型编码。
     */
    @NotBlank(message = "dictType不能为空")
    private String dictType;

    /**
     * 字典标签。
     */
    @NotBlank(message = "dictLabel不能为空")
    private String dictLabel;

    /**
     * 字典键值。
     */
    @NotBlank(message = "dictValue不能为空")
    private String dictValue;

    /**
     * 父级字典值。
     */
    private String parentValue;

    /**
     * 语言区域。
     */
    private String locale;

    /**
     * 排序。
     */
    private Integer dictSort;

    /**
     * 展示样式，例如 tag、primary 等前端样式标记，可为空。
     */
    private String listClass;

    /**
     * 扩展属性 JSON，用于承载字典项额外配置，格式由前后端协商定义。
     */
    private String extraJson;

    /**
     * 是否默认：0否，1是。
     */
    private Integer isDefault;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 当前操作人。
     */
    private String operator;
}

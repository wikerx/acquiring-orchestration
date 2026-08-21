package com.scott.payment.component.db.email.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SharedEmailTemplateDO
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共组件数据层的邮件模板只读实体，仅用于构建不含 SMTP 凭据的已启用模板快照
 * @status : create
 */
@Data
@TableName("msg_email_template")
public class SharedEmailTemplateDO {

    /** 模板主键，不允许为空。 */
    @TableId
    private Long id;

    /** 模板业务编码，不允许为空。 */
    private String templateCode;

    /** 模板展示名称，不允许为空。 */
    private String templateName;

    /** 适用应用编码，不允许为空。 */
    private String appCode;

    /** 邮件场景编码，不允许为空。 */
    private String sceneCode;

    /** 语言区域，格式如 zh-CN 或 en-US，不允许为空。 */
    private String locale;

    /** 邮件主题模板，可包含受控变量，不允许为空。 */
    private String subjectTemplate;

    /** 正文类型，如 HTML 或 TEXT，不允许为空。 */
    private String contentType;

    /** 邮件正文模板，可包含受控变量，不允许为空。 */
    private String contentTemplate;

    /** 允许使用的模板变量结构 JSON，不允许为空。 */
    private String variableSchema;

    /** 需要脱敏的变量名称 JSON，允许为空数组。 */
    private String sensitiveVariableNames;

    /** 启用状态，1 表示启用，0 表示停用，不允许为空。 */
    private Integer status;

    /** 逻辑删除标识，0 表示未删除，不允许为空。 */
    private Long deleted;
}

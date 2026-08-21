package com.scott.payment.component.db.email.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EnabledEmailTemplateSnapshot
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 跨系统共享的已启用邮件模板快照，不包含 SMTP 账号和密码
 * @status : create
 */
@Data
public class EnabledEmailTemplateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板主键。 */
    private Long id;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 适用应用编码。 */
    private String appCode;

    /** 邮件场景编码。 */
    private String sceneCode;

    /** 语言区域。 */
    private String locale;

    /** 主题模板。 */
    private String subjectTemplate;

    /** 正文类型。 */
    private String contentType;

    /** 正文模板。 */
    private String contentTemplate;

    /** 变量结构 JSON。 */
    private String variableSchema;

    /** 敏感变量名称 JSON。 */
    private String sensitiveVariableNames;
}

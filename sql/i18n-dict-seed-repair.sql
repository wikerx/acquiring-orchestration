-- =============================================================================
-- 国际化字典种子数据修复
-- 补充 sys_dict_type 和 sys_dict_data 中缺失的国际化字典条目
-- 所有 INSERT 均使用 IGNORE 保证幂等，可重复执行
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ===================== 字典类型定义 =====================
INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted) VALUES
(1,  '系统开关',      'sys_normal_disable',    'system', 1, 0, 1, 0),
(2,  '显示状态',      'sys_show_hide',          'system', 1, 0, 1, 0),
(3,  '是否',          'sys_yes_no',             'system', 1, 0, 1, 0),
(4,  '通知类型',      'sys_notice_type',        'system', 1, 0, 1, 0),
(5,  '菜单类型',      'sys_menu_type',          'system', 1, 0, 1, 0),
(6,  '权限类型',      'sys_permission_type',    'system', 1, 0, 1, 0),
(7,  '操作类型',      'sys_operation_type',     'system', 1, 0, 1, 0),
(8,  '操作状态',      'sys_oper_status',        'system', 1, 0, 1, 0),
(9,  '登录状态',      'sys_login_status',       'system', 1, 0, 1, 0),
(10, '角色类型',      'sys_role_type',          'system', 1, 0, 1, 0),
(11, '数据范围',      'sys_data_scope',         'system', 1, 0, 1, 0),
(12, '商户状态',      'sys_merchant_status',    'merchant', 1, 0, 1, 0),
(13, '风险等级',      'sys_risk_level',         'merchant', 1, 0, 1, 0),
(14, '配置值类型',    'sys_config_value_type',  'system', 1, 0, 1, 0),
(15, '用户状态',      'sys_user_status',        'system', 1, 0, 1, 0),
(16, '账号状态',      'sys_account_status',     'system', 1, 0, 1, 0),
(17, '岗位状态',      'sys_post_status',        'system', 1, 0, 1, 0);

-- ===================== 字典数据 — zh-CN =====================
INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted) VALUES
(100, 'sys_normal_disable', '启用', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(101, 'sys_normal_disable', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(110, 'sys_show_hide', '显示', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(111, 'sys_show_hide', '隐藏', '0', 'zh-CN', 2, 'warning', 0, 1, 0),
(120, 'sys_yes_no', '是', 'Y', 'zh-CN', 1, 'success', 1, 1, 0),
(121, 'sys_yes_no', '否', 'N', 'zh-CN', 2, 'danger',  0, 1, 0),
(130, 'sys_notice_type', '通知', '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(131, 'sys_notice_type', '公告', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(140, 'sys_menu_type', '目录', 'CATALOG', 'zh-CN', 1, 'primary', 1, 1, 0),
(141, 'sys_menu_type', '菜单', 'MENU',    'zh-CN', 2, 'success', 0, 1, 0),
(142, 'sys_menu_type', '按钮', 'BUTTON',  'zh-CN', 3, 'warning', 0, 1, 0),
(143, 'sys_menu_type', '外链', 'LINK',    'zh-CN', 4, 'info',    0, 1, 0),
(150, 'sys_permission_type', '菜单权限', 'MENU',   'zh-CN', 1, 'primary', 1, 1, 0),
(151, 'sys_permission_type', '按钮权限', 'BUTTON', 'zh-CN', 2, 'warning', 0, 1, 0),
(152, 'sys_permission_type', '接口权限', 'API',    'zh-CN', 3, 'success', 0, 1, 0),
(153, 'sys_permission_type', '数据权限', 'DATA',   'zh-CN', 4, 'info',    0, 1, 0),
(160, 'sys_operation_type', '新增', '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(161, 'sys_operation_type', '修改', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(162, 'sys_operation_type', '删除', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
(163, 'sys_operation_type', '查询', '4', 'zh-CN', 4, 'info',    0, 1, 0),
(164, 'sys_operation_type', '导出', '5', 'zh-CN', 5, 'success', 0, 1, 0),
(165, 'sys_operation_type', '审核', '6', 'zh-CN', 6, 'warning', 0, 1, 0),
(166, 'sys_operation_type', '冻结', '7', 'zh-CN', 7, 'danger',  0, 1, 0),
(167, 'sys_operation_type', '解冻', '8', 'zh-CN', 8, 'success', 0, 1, 0),
(170, 'sys_oper_status', '成功', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(171, 'sys_oper_status', '失败', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(180, 'sys_login_status', '成功', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(181, 'sys_login_status', '失败', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(190, 'sys_role_type', '系统角色',   'SYSTEM', 'zh-CN', 1, 'primary', 1, 1, 0),
(191, 'sys_role_type', '自定义角色', 'CUSTOM', 'zh-CN', 2, 'info',    0, 1, 0),
(200, 'sys_data_scope', '全部数据权限', 'ALL',      'zh-CN', 1, 'primary', 1, 1, 0),
(201, 'sys_data_scope', '自身数据权限', 'SELF',     'zh-CN', 2, 'success', 0, 1, 0),
(202, 'sys_data_scope', '自定义数据权限', 'CUSTOM', 'zh-CN', 3, 'warning', 0, 1, 0),
(203, 'sys_data_scope', '组织数据权限', 'ORG',      'zh-CN', 4, 'info',    0, 1, 0),
(204, 'sys_data_scope', '商户数据权限', 'MERCHANT', 'zh-CN', 5, 'info',    0, 1, 0),
(205, 'sys_data_scope', '店铺数据权限', 'STORE',    'zh-CN', 6, 'info',    0, 1, 0),
(206, 'sys_data_scope', '渠道数据权限', 'CHANNEL',  'zh-CN', 7, 'info',    0, 1, 0),
(210, 'sys_merchant_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(211, 'sys_merchant_status', '冻结', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(212, 'sys_merchant_status', '关闭', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
(220, 'sys_risk_level', '低', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(221, 'sys_risk_level', '中', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(222, 'sys_risk_level', '高', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
(230, 'sys_config_value_type', '字符串', '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(231, 'sys_config_value_type', '数字',   '2', 'zh-CN', 2, 'success', 0, 1, 0),
(232, 'sys_config_value_type', '布尔',   '3', 'zh-CN', 3, 'warning', 0, 1, 0),
(233, 'sys_config_value_type', 'JSON',   '4', 'zh-CN', 4, 'info',    0, 1, 0),
(240, 'sys_user_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(241, 'sys_user_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(250, 'sys_account_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(251, 'sys_account_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(260, 'sys_post_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(261, 'sys_post_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0);

-- ===================== 字典数据 — en-US =====================
INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted) VALUES
(1100, 'sys_normal_disable', 'Enabled',  '1', 'en-US', 1, 'success', 1, 1, 0),
(1101, 'sys_normal_disable', 'Disabled', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1110, 'sys_show_hide', 'Show', '1', 'en-US', 1, 'success', 1, 1, 0),
(1111, 'sys_show_hide', 'Hide', '0', 'en-US', 2, 'warning', 0, 1, 0),
(1120, 'sys_yes_no', 'Yes', 'Y', 'en-US', 1, 'success', 1, 1, 0),
(1121, 'sys_yes_no', 'No',  'N', 'en-US', 2, 'danger',  0, 1, 0),
(1130, 'sys_notice_type', 'Notice',       '1', 'en-US', 1, 'primary', 1, 1, 0),
(1131, 'sys_notice_type', 'Announcement', '2', 'en-US', 2, 'warning', 0, 1, 0),
(1140, 'sys_menu_type', 'Catalog', 'CATALOG', 'en-US', 1, 'primary', 1, 1, 0),
(1141, 'sys_menu_type', 'Menu',    'MENU',    'en-US', 2, 'success', 0, 1, 0),
(1142, 'sys_menu_type', 'Button',  'BUTTON',  'en-US', 3, 'warning', 0, 1, 0),
(1143, 'sys_menu_type', 'Link',    'LINK',    'en-US', 4, 'info',    0, 1, 0),
(1150, 'sys_permission_type', 'Menu Permission',       'MENU',   'en-US', 1, 'primary', 1, 1, 0),
(1151, 'sys_permission_type', 'Button Permission',     'BUTTON', 'en-US', 2, 'warning', 0, 1, 0),
(1152, 'sys_permission_type', 'API Permission',        'API',    'en-US', 3, 'success', 0, 1, 0),
(1153, 'sys_permission_type', 'Data Scope Permission', 'DATA',   'en-US', 4, 'info',    0, 1, 0),
(1160, 'sys_operation_type', 'Create',   '1', 'en-US', 1, 'primary', 1, 1, 0),
(1161, 'sys_operation_type', 'Update',   '2', 'en-US', 2, 'warning', 0, 1, 0),
(1162, 'sys_operation_type', 'Delete',   '3', 'en-US', 3, 'danger',  0, 1, 0),
(1163, 'sys_operation_type', 'Query',    '4', 'en-US', 4, 'info',    0, 1, 0),
(1164, 'sys_operation_type', 'Export',   '5', 'en-US', 5, 'success', 0, 1, 0),
(1165, 'sys_operation_type', 'Audit',    '6', 'en-US', 6, 'warning', 0, 1, 0),
(1166, 'sys_operation_type', 'Freeze',   '7', 'en-US', 7, 'danger',  0, 1, 0),
(1167, 'sys_operation_type', 'Unfreeze', '8', 'en-US', 8, 'success', 0, 1, 0),
(1170, 'sys_oper_status', 'Success', '1', 'en-US', 1, 'success', 1, 1, 0),
(1171, 'sys_oper_status', 'Failure', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1180, 'sys_login_status', 'Success', '1', 'en-US', 1, 'success', 1, 1, 0),
(1181, 'sys_login_status', 'Failure', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1190, 'sys_role_type', 'System',  'SYSTEM', 'en-US', 1, 'primary', 1, 1, 0),
(1191, 'sys_role_type', 'Custom',  'CUSTOM', 'en-US', 2, 'info',    0, 1, 0),
(1200, 'sys_data_scope', 'All Data',          'ALL',      'en-US', 1, 'primary', 1, 1, 0),
(1201, 'sys_data_scope', 'Self Data',         'SELF',     'en-US', 2, 'success', 0, 1, 0),
(1202, 'sys_data_scope', 'Custom Data',       'CUSTOM',   'en-US', 3, 'warning', 0, 1, 0),
(1203, 'sys_data_scope', 'Organization Data', 'ORG',      'en-US', 4, 'info',    0, 1, 0),
(1204, 'sys_data_scope', 'Merchant Data',     'MERCHANT', 'en-US', 5, 'info',    0, 1, 0),
(1205, 'sys_data_scope', 'Store Data',        'STORE',    'en-US', 6, 'info',    0, 1, 0),
(1206, 'sys_data_scope', 'Channel Data',      'CHANNEL',  'en-US', 7, 'info',    0, 1, 0),
(1210, 'sys_merchant_status', 'Active', '1', 'en-US', 1, 'success', 1, 1, 0),
(1211, 'sys_merchant_status', 'Frozen', '2', 'en-US', 2, 'warning', 0, 1, 0),
(1212, 'sys_merchant_status', 'Closed', '3', 'en-US', 3, 'danger',  0, 1, 0),
(1220, 'sys_risk_level', 'Low',    '1', 'en-US', 1, 'success', 1, 1, 0),
(1221, 'sys_risk_level', 'Medium', '2', 'en-US', 2, 'warning', 0, 1, 0),
(1222, 'sys_risk_level', 'High',   '3', 'en-US', 3, 'danger',  0, 1, 0),
(1230, 'sys_config_value_type', 'String',  '1', 'en-US', 1, 'primary', 1, 1, 0),
(1231, 'sys_config_value_type', 'Number',  '2', 'en-US', 2, 'success', 0, 1, 0),
(1232, 'sys_config_value_type', 'Boolean', '3', 'en-US', 3, 'warning', 0, 1, 0),
(1233, 'sys_config_value_type', 'JSON',    '4', 'en-US', 4, 'info',    0, 1, 0),
(1240, 'sys_user_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1241, 'sys_user_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1250, 'sys_account_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1251, 'sys_account_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1260, 'sys_post_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1261, 'sys_post_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 验证查询（取消注释以验证）
-- =============================================================================
-- SELECT dt.dict_name, dt.dict_type,
--        COUNT(CASE WHEN dd.locale = 'zh-CN' THEN 1 END) AS zh_CN_count,
--        COUNT(CASE WHEN dd.locale = 'en-US' THEN 1 END) AS en_US_count
-- FROM sys_dict_type dt
-- LEFT JOIN sys_dict_data dd ON dd.dict_type = dt.dict_type AND dd.deleted = 0 AND dd.status = 1
-- WHERE dt.deleted = 0
-- GROUP BY dt.dict_type
-- ORDER BY dt.id;

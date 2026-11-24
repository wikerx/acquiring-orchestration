-- REQ-001 修复旧连接把 UTF-8 字节按 latin1 读入后产生的乱码。
-- 修复只匹配原始种子文本对应的乱码字节，保留正常名称、自定义内容和原更新时间。
-- 使用 mysql --default-character-set=utf8mb4 执行。文本使用 UTF-8 十六进制值，避免终端编码干扰。
SET NAMES utf8mb4;

SET @boundary = CONVERT(UNHEX('E694AFE4BB98E696B9E5BC8FE58786E585A5E58F8AE9858DE7BDAEE5A48DE6A0B8') USING utf8mb4);
SET @delivery = CONVERT(UNHEX('E694AFE4BB98E696B9E5BC8FE9809AE79FA5E681A2E5A48D') USING utf8mb4);
SET @risk = CONVERT(UNHEX('E7B4AFE8AEA1E99990E9A29DE9A284E58DA0E5A48DE6A0B8') USING utf8mb4);
SET @job_description = CONVERT(UNHEX('E58F91E5B883E5898DE4BF9DE68C81E7A681E794A8EFBC8CE68C89E5B7B2E6A0B8E58786E88C83E59BB4E590AFE794A8') USING utf8mb4);
SET @scene_name = CONVERT(UNHEX('E694AFE4BB98E696B9E5BC8FE8BF9BE5BAA6') USING utf8mb4);
SET @scene_remark = CONVERT(UNHEX('E694AFE4BB98E696B9E5BC8FE68E88E69D83E5928CE9858DE7BDAEE8BF9BE5BAA6') USING utf8mb4);
SET @template_subject = CONVERT(UNHEX('E694AFE4BB98E696B9E5BC8FE8BF9BE5BAA6E69BB4E696B0') USING utf8mb4);
SET @template_content = CONVERT(UNHEX('E59586E688B720247B6D65726368616E7449647D20E79A84E694AFE4BB98E696B9E5BC8F20247B6974656D4B65797D20E78AB6E68081E5B7B2E69BB4E696B0E38082E8AFB7E799BBE5BD95E59586E688B7E5B9B3E58FB0E69FA5E79C8BE38082') USING utf8mb4);
SET @template_remark = CONVERT(UNHEX('E9809AE8BF87E78EB0E69C89E58FAFE99DA0E9809AE79FA5E6B581E7A88BE68A95E98092') USING utf8mb4);

START TRANSACTION;

-- 修复任务定义及运行日志内复制的任务名称。运行日志的状态、时间和结果不受此次修复影响。
UPDATE sys_job_task t
JOIN (SELECT 'MERCHANT_PAYMENT_CAPABILITY_BOUNDARY' AS job_code, @boundary AS job_name
           UNION ALL SELECT 'MERCHANT_PAYMENT_CAPABILITY_DELIVERY_RECOVERY', @delivery
           UNION ALL SELECT 'MERCHANT_PAYMENT_RISK_RESERVATION_RECOVERY', @risk) seed ON seed.job_code = t.job_code
SET t.job_name = IF(HEX(t.job_name) = HEX(CONVERT(CONVERT(CAST(seed.job_name AS BINARY) USING latin1) USING utf8mb4)), seed.job_name, t.job_name),
    t.description = IF(HEX(t.description) = HEX(CONVERT(CONVERT(CAST(@job_description AS BINARY) USING latin1) USING utf8mb4)), @job_description, t.description),
    t.update_time = t.update_time
WHERE t.deleted = 0
  AND (HEX(t.job_name) = HEX(CONVERT(CONVERT(CAST(seed.job_name AS BINARY) USING latin1) USING utf8mb4))
       OR HEX(t.description) = HEX(CONVERT(CONVERT(CAST(@job_description AS BINARY) USING latin1) USING utf8mb4)));

UPDATE sys_job_run_log t
JOIN sys_job_task definition ON definition.id = t.job_id AND definition.job_code = t.job_code
JOIN (SELECT 'MERCHANT_PAYMENT_CAPABILITY_BOUNDARY' AS job_code, @boundary AS job_name
           UNION ALL SELECT 'MERCHANT_PAYMENT_CAPABILITY_DELIVERY_RECOVERY', @delivery
           UNION ALL SELECT 'MERCHANT_PAYMENT_RISK_RESERVATION_RECOVERY', @risk) seed ON seed.job_code = t.job_code
SET t.job_name = seed.job_name,
    t.update_time = t.update_time
WHERE HEX(t.job_name) = HEX(CONVERT(CONVERT(CAST(seed.job_name AS BINARY) USING latin1) USING utf8mb4));

-- 修复同批录入的邮件场景及内置初始模板。英文正文和运维自定义模板保留原值。
UPDATE sys_dict_data d
SET d.dict_label = IF(HEX(d.dict_label) = HEX(CONVERT(CONVERT(CAST(@scene_name AS BINARY) USING latin1) USING utf8mb4)), @scene_name, d.dict_label),
    d.remark = IF(HEX(d.remark) = HEX(CONVERT(CONVERT(CAST(@scene_remark AS BINARY) USING latin1) USING utf8mb4)), @scene_remark, d.remark),
    d.updated_at = d.updated_at
WHERE d.dict_type = 'email_scene_code'
  AND d.dict_value = 'MERCHANT_PAYMENT_CAPABILITY' AND d.locale IN ('zh-CN', 'en-US') AND d.deleted = 0
  AND (HEX(d.dict_label) = HEX(CONVERT(CONVERT(CAST(@scene_name AS BINARY) USING latin1) USING utf8mb4))
       OR HEX(d.remark) = HEX(CONVERT(CONVERT(CAST(@scene_remark AS BINARY) USING latin1) USING utf8mb4)));

UPDATE msg_email_template m
SET m.template_name = IF(HEX(m.template_name) = HEX(CONVERT(CONVERT(CAST(@scene_name AS BINARY) USING latin1) USING utf8mb4)), @scene_name, m.template_name),
    m.subject_template = IF(HEX(m.subject_template) = HEX(CONVERT(CONVERT(CAST(@template_subject AS BINARY) USING latin1) USING utf8mb4)), @template_subject, m.subject_template),
    m.content_template = IF(HEX(m.content_template) = HEX(CONVERT(CONVERT(CAST(@template_content AS BINARY) USING latin1) USING utf8mb4)), @template_content, m.content_template),
    m.remark = IF(HEX(m.remark) = HEX(CONVERT(CONVERT(CAST(@template_remark AS BINARY) USING latin1) USING utf8mb4)), @template_remark, m.remark),
    m.update_time = m.update_time
WHERE m.template_code = 'MERCHANT_PAYMENT_CAPABILITY'
  AND m.app_code = 'ADMIN' AND m.locale IN ('zh-CN', 'en-US')
  AND m.version_no = 1 AND m.system_builtin = 1 AND m.deleted = 0
  AND (HEX(m.template_name) = HEX(CONVERT(CONVERT(CAST(@scene_name AS BINARY) USING latin1) USING utf8mb4))
       OR HEX(m.subject_template) = HEX(CONVERT(CONVERT(CAST(@template_subject AS BINARY) USING latin1) USING utf8mb4))
       OR HEX(m.content_template) = HEX(CONVERT(CONVERT(CAST(@template_content AS BINARY) USING latin1) USING utf8mb4))
       OR HEX(m.remark) = HEX(CONVERT(CONVERT(CAST(@template_remark AS BINARY) USING latin1) USING utf8mb4)));

COMMIT;

-- 只回退本次内容与版本均未再改变的模板；版本递增，保留审计和后续编辑。

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE msg_email_template t JOIN msg_email_template_pbl_ui_backup_20261118 b ON t.id=b.id SET t.content_template=b.content_template, t.version_no=t.version_no+1, t.update_by='payment-link-ui-rollback', t.update_time=CURRENT_TIMESTAMP(3) WHERE t.template_code='PAYMENT_LINK_INVITATION' AND t.app_code='COMMON' AND t.locale='zh-CN' AND t.system_builtin=1 AND t.deleted=0 AND t.version_no=b.version_no+1 AND SHA2(t.content_template,256)='ef8c73212121ff5c86beff89b4aa44092226575669beabce753f5260f83f209a';

UPDATE msg_email_template t JOIN msg_email_template_pbl_ui_backup_20261118 b ON t.id=b.id SET t.content_template=b.content_template, t.version_no=t.version_no+1, t.update_by='payment-link-ui-rollback', t.update_time=CURRENT_TIMESTAMP(3) WHERE t.template_code='PAYMENT_LINK_INVITATION' AND t.app_code='COMMON' AND t.locale='en-US' AND t.system_builtin=1 AND t.deleted=0 AND t.version_no=b.version_no+1 AND SHA2(t.content_template,256)='6a29cdc534563f388a9a0f793b79039fa0e5dc212b037d2333d8599c4cd25f0e';

COMMIT;

#!/usr/bin/env python3
"""在本地 MySQL8 的连接级临时表中验证路由指针修复，禁止写入业务表。"""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]/'docs/sql'
setup='''
CREATE TEMPORARY TABLE settlement_candidate (id BIGINT PRIMARY KEY,candidate_no VARCHAR(64),source_type VARCHAR(24),source_business_id VARCHAR(64),source_revision INT,source_transaction_id VARCHAR(64),source_transaction_date_time DATETIME(3),merchant_id VARCHAR(64),candidate_status VARCHAR(24),settlement_batch_no VARCHAR(19),version BIGINT);
CREATE TEMPORARY TABLE settlement_batch (id BIGINT PRIMARY KEY,settlement_batch_no VARCHAR(19),batch_type VARCHAR(24),batch_status VARCHAR(24),last_failure_stage VARCHAR(32),last_failure_code VARCHAR(64),result_fingerprint CHAR(64),rate_locked_time DATETIME(3),posted_time DATETIME(3),version BIGINT);
CREATE TEMPORARY TABLE settlement_batch_candidate (candidate_id BIGINT,settlement_batch_no VARCHAR(19),source_type VARCHAR(24),source_business_id VARCHAR(64),source_revision INT,relation_status VARCHAR(24));
CREATE TEMPORARY TABLE settlement_result_item (settlement_batch_no VARCHAR(19));
CREATE TEMPORARY TABLE merchant_fund_ledger (settlement_batch_no VARCHAR(19));
CREATE TEMPORARY TABLE transaction_reserve_clearing_detail_202603 (transaction_id VARCHAR(64),clearing_revision INT,finance_state_id VARCHAR(64),merchant_id VARCHAR(64),transaction_date_time DATETIME(3),record_status VARCHAR(16),reserve_action_type VARCHAR(16));
CREATE TEMPORARY TABLE transaction_reserve_clearing_detail_202604 LIKE transaction_reserve_clearing_detail_202603;
CREATE TEMPORARY TABLE transaction_reserve_clearing_detail_202701 LIKE transaction_reserve_clearing_detail_202603;
INSERT INTO settlement_batch VALUES (1,'B1','RESERVE_RELEASE','MANUAL_REVIEW','FACT_LOADING','SETTLEMENT_CLEARING_FACT_MISSING',NULL,NULL,NULL,4),(2,'B2','RESERVE_RELEASE','MANUAL_REVIEW','FACT_LOADING','SETTLEMENT_CLEARING_FACT_MISSING',NULL,NULL,NULL,4);
INSERT INTO settlement_candidate VALUES (1,'C1','RESERVE_RELEASE','FS1',1,'T1','2026-11-19 11:08:08.200','M1','MANUAL_REVIEW','B1',3),(2,'C2','RESERVE_RELEASE','FS2',1,'T2','2026-11-19 11:08:08.200','M1','MANUAL_REVIEW','B1',3),(3,'C3','RESERVE_RELEASE','FS3',1,'T3','2026-11-19 11:08:08.200','M1','MANUAL_REVIEW','B2',3),(4,'C4','RESERVE_RELEASE','FS4',1,'T4','2026-11-19 11:08:08.200','M1','MANUAL_REVIEW','B1',3),(5,'C5','RESERVE_RELEASE','FS5',1,'T5','2026-11-19 11:08:08.200','M1','MANUAL_REVIEW','B1',3);
INSERT INTO settlement_batch_candidate SELECT id,settlement_batch_no,source_type,source_business_id,source_revision,IF(id=4,'POSTED','MANUAL_REVIEW') FROM settlement_candidate;
INSERT INTO merchant_fund_ledger VALUES ('B2');
INSERT INTO transaction_reserve_clearing_detail_202604 VALUES ('T1',1,'FS1','M1','2026-11-19 11:08:08.201','ACTIVE','RELEASE'),('T2',1,'FS2','M1','2026-11-19 11:08:08.201','ACTIVE','RELEASE'),('T2',1,'FS2','M1','2026-11-19 11:08:08.201','ACTIVE','RELEASE'),('T3',1,'FS3','M1','2026-11-19 11:08:08.201','ACTIVE','RELEASE'),('T4',1,'FS4','M1','2026-11-19 11:08:08.201','ACTIVE','RELEASE'),('T5',1,'FS5','M1','2026-11-19 11:08:08.202','ACTIVE','RELEASE');
'''
repair=(root/'20261120_02_reserve_release_route_time_repair_draft.sql').read_text().replace('CREATE TABLE IF NOT EXISTS','CREATE TEMPORARY TABLE IF NOT EXISTS')
repair=repair.replace('SET @expected_rows = 27793;','SET @expected_rows = 1;').replace('SET @expected_batches = 70;','SET @expected_batches = 1;')
checks="SELECT 'VERIFY', id, DATE_FORMAT(source_transaction_date_time,'%f'), version FROM settlement_candidate ORDER BY id;\n"
def run(sql):
 r=subprocess.run(['docker','exec','-i','MySQL8','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N payment_acquiring'],input=setup+sql,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout
out=run(repair.replace('ROLLBACK;',checks+'ROLLBACK;'))
assert 'VERIFY\t1\t200000\t3' in out
approved=repair.replace('SET @repair_approved = 0;','SET @repair_approved = 1;')
out=run(approved.replace('ROLLBACK;',checks+'ROLLBACK;'))
assert 'VERIFY\t1\t201000\t4' in out
for i in range(2,6):assert f'VERIFY\t{i}\t200000\t3' in out
wrong_count=approved.replace('SET @expected_rows = 1;','SET @expected_rows = 2;')
out=run(wrong_count.replace('ROLLBACK;',checks+'ROLLBACK;'))
assert 'VERIFY\t1\t200000\t3' in out
rollback=(root/'20261120_03_reserve_release_route_time_rollback_draft.sql').read_text()
out=run(approved.replace('ROLLBACK;','COMMIT;')+rollback.replace('ROLLBACK;',checks+'ROLLBACK;'))
assert 'VERIFY\t1\t200000\t5' in out
# 冻结后被其他流程更新的候选不得被回退。
out=run(approved.replace('ROLLBACK;','COMMIT;')+'UPDATE settlement_candidate SET version=5 WHERE id=1;\n'+rollback.replace('ROLLBACK;',checks+'ROLLBACK;'))
assert 'VERIFY\t1\t201000\t5' in out
print('PASS: unapproved no-op, exact 1ms repair, duplicate/posted/wrong-relation/2ms exclusion, count drift no-op, version-preserving rollback, stale rollback rejection; all writes used connection-local temporary tables')

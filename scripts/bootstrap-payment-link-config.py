#!/usr/bin/env python3
"""为本地支付链接生成独立签名密钥；文件权限 0600，不在终端输出密钥。"""
import argparse
import os
import secrets
from pathlib import Path
from urllib.parse import urlparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', required=True, help='受控配置目录内的新 properties 文件，拒绝覆盖')
parser.add_argument('--origin', default='http://localhost:5175', help='托管收银台 origin，默认复用本机既有收银台端口')
args = parser.parse_args()
url = urlparse(args.origin)
if url.username or url.password or url.query or url.fragment or url.path not in ('', '/') or not (
    url.scheme == 'https' and url.hostname or url.scheme == 'http' and url.hostname in ('localhost', '127.0.0.1')
):
    parser.error('origin 必须为 HTTPS origin；仅本机回环允许 HTTP')
path = Path(args.output).expanduser().resolve()
path.parent.mkdir(parents=True, exist_ok=True)
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write('# 先执行迁移和后置检查，再将 storage-ready 和 enabled 改为 true。\n')
    stream.write('payment.link.enabled=false\npayment.link.storage-ready=false\n')
    stream.write(f'payment.link.frontend-base-url={args.origin.rstrip("/")}\n')
    stream.write('payment.link.active-key-version=v1\n')
    stream.write('payment.link.signing-keys.v1=' + secrets.token_hex(32) + '\n')
print(f'已生成独立支付链接配置：{path}；权限 0600；尚未启用入口。')

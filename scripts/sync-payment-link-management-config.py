#!/usr/bin/env python3
"""将 Payment 已有链接配置原样同步到双端服务 DataId；不生成或轮换签名密钥。依赖 PyYAML。"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from copy import deepcopy
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import yaml


class ConfigurationError(Exception):
    """错误文本只包含配置位置，不包含服务响应或密钥。"""


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise ConfigurationError('配置包含重复 YAML 键，停止同步')
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def parsed(content):
    try:
        value = yaml.load(content, Loader=UniqueLoader)
    except yaml.YAMLError:
        raise ConfigurationError('配置不是可解析 YAML，停止同步') from None
    if not isinstance(value, dict):
        raise ConfigurationError('配置必须是 YAML 对象')
    return value


def link_config(content):
    config = parsed(content)
    payment = config.get('payment', {})
    if not isinstance(payment, dict) or not isinstance(payment.get('link'), dict):
        raise ConfigurationError('源服务缺少 payment.link 配置，禁止生成替代密钥')
    link = payment['link']
    keys = link.get('signing-keys')
    active = link.get('active-key-version')
    if not isinstance(keys, dict) or active not in keys or any(not isinstance(v, str) or len(v.encode()) < 32 for v in keys.values()):
        raise ConfigurationError('源链接签名版本或密钥不完整，禁止同步')
    if any('${' in str(value) for value in [active, *keys.values()]):
        raise ConfigurationError('源配置仍含 Secret 占位符，请先由受控 Secret 注入工具解析，禁止将占位符当作密钥')
    return deepcopy(link)


def merge_target(content, link):
    config = parsed(content)
    payment = config.get('payment', {})
    if not isinstance(payment, dict):
        raise ConfigurationError('目标 payment 节点不是对象')
    previous = payment.get('link')
    if previous is not None:
        if previous == link:
            return content
        raise ConfigurationError('目标已有不同 payment.link，需先核实版本；禁止覆盖或轮换既有凭据')
    # 只插入缺失的子树，原文其他内容和注释保持不变。
    fragment = yaml.safe_dump({'link': link}, allow_unicode=True, sort_keys=False)
    indented = ''.join('  ' + line if line.strip() else line for line in fragment.splitlines(keepends=True))
    match = re.search(r'^payment:\s*(?:#.*)?\n', content, re.MULTILINE)
    if match:
        result = content[:match.end()] + indented + content[match.end():]
    elif 'payment' not in config:
        result = content.rstrip() + '\n\npayment:\n' + indented
    else:
        raise ConfigurationError('目标 payment 使用非标准 YAML 布局，停止自动插入')
    expected = deepcopy(config)
    expected.setdefault('payment', {})['link'] = link
    if parsed(result) != expected:
        raise ConfigurationError('配置合并改变了无关节点，停止同步')
    return result


class Nacos:
    def __init__(self, endpoint, group, namespace):
        self.endpoint = endpoint.rstrip('/')
        self.group, self.namespace, self.token = group, namespace, None
        user, password = os.environ.get('NACOS_USERNAME'), os.environ.get('NACOS_PASSWORD')
        if bool(user) != bool(password):
            raise ConfigurationError('NACOS_USERNAME 和 NACOS_PASSWORD 必须同时提供')
        if user:
            try:
                response = json.loads(self.request('/v1/auth/users/login', {'username': user, 'password': password}, True))
                self.token = response['accessToken']
            except (ValueError, KeyError):
                raise ConfigurationError('Nacos 登录失败，未输出凭据或响应正文') from None

    def request(self, path, values, post=False):
        values = dict(values)
        if self.token:
            values['accessToken'] = self.token
        data = urlencode(values).encode()
        url = self.endpoint + path + ('' if post else '?' + data.decode())
        try:
            with urlopen(Request(url, data=data if post else None), timeout=15) as response:
                return response.read().decode()
        except HTTPError as ex:
            raise ConfigurationError('Nacos 请求失败，HTTP ' + str(ex.code)) from None
        except URLError:
            raise ConfigurationError('Nacos 连接失败') from None

    def get(self, data_id):
        return self.request('/v1/cs/configs', {'dataId': data_id, 'group': self.group, 'tenant': self.namespace})

    def publish(self, data_id, before, after):
        # CAS 拒绝覆盖读取之后被其他操作者修改的服务配置。
        result = self.request('/v1/cs/configs', {'dataId': data_id, 'group': self.group, 'tenant': self.namespace,
            'type': 'yaml', 'casMd5': hashlib.md5(before.encode()).hexdigest(), 'content': after}, True)
        if result.strip() != 'true':
            raise ConfigurationError('Nacos 拒绝 CAS 发布，停止并保留备份')


def wait_for_config(nacos, data_id, expected):
    # Nacos 发布成功后的配置读取缓存可能短暂返回旧版；有界核对避免把延迟误判为发布失败。
    deadline = time.monotonic() + 10
    while True:
        if nacos.get(data_id) == expected:
            return
        if time.monotonic() >= deadline:
            raise ConfigurationError('发布后配置在核对期限内未收敛，停止并保留备份')
        time.sleep(0.25)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['plan', 'apply', 'verify'])
    parser.add_argument('--environment', choices=['dev', 'test', 'uat', 'prod', 'sample'], required=True)
    parser.add_argument('--namespace', required=True)
    parser.add_argument('--endpoint', default=os.environ.get('NACOS_ENDPOINT', 'http://127.0.0.1:8848/nacos'))
    parser.add_argument('--group', default=os.environ.get('NACOS_GROUP', 'DEFAULT_GROUP'))
    parser.add_argument('--backup-dir', help='apply 必须指定新的受控目录，写入 0600 配置备份，禁止提交 Git')
    args = parser.parse_args()
    if not args.namespace or args.namespace == 'public':
        parser.error('必须指定非 public 命名空间')
    nacos = Nacos(args.endpoint, args.group, args.namespace)
    source = nacos.get('service-payment-' + args.environment + '.yaml')
    link = link_config(source)
    plans = []
    for service in ['service-admin', 'service-merchant']:
        data_id = service + '-' + args.environment + '.yaml'
        before = nacos.get(data_id)
        after = merge_target(before, link)
        plans.append((data_id, before, after))
        print(data_id + ': ' + ('链接配置一致' if before == after else '仅补入缺失的 payment.link'))
    if args.action == 'verify':
        if any(before != after for _, before, after in plans):
            raise ConfigurationError('双端配置尚未同步')
        return
    if args.action != 'apply':
        return
    if not args.backup_dir:
        parser.error('apply 需要 --backup-dir')
    backup = Path(args.backup_dir).expanduser().resolve()
    backup.mkdir(mode=0o700, parents=True, exist_ok=False)
    manifest = {'environment': args.environment, 'namespace': args.namespace, 'group': args.group, 'files': []}
    for data_id, before, after in plans:
        for suffix, content in [('before', before), ('after', after)]:
            fd = os.open(backup / (data_id + '.' + suffix), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as stream:
                stream.write(content)
        manifest['files'].append({'dataId': data_id, 'changed': before != after})
    with open(backup / 'manifest.json', 'x') as stream:
        json.dump(manifest, stream, ensure_ascii=False)
    completed = []
    try:
        # 发布前重新核实源配置；付款签名配置变更后不能继续传播旧值。
        if nacos.get('service-payment-' + args.environment + '.yaml') != source:
            raise ConfigurationError('源服务配置已变更，停止发布')
        for data_id, before, after in plans:
            if before != after:
                nacos.publish(data_id, before, after)
                completed.append((data_id, before, after))
            wait_for_config(nacos, data_id, after)
    except ConfigurationError:
        rollback_failed = False
        for data_id, before, after in reversed(completed):
            try:
                nacos.publish(data_id, after, before)
                wait_for_config(nacos, data_id, before)
            except ConfigurationError:
                rollback_failed = True
        if rollback_failed:
            print('部分配置回退未完成，请使用受控备份核查；禁止覆盖并发修改。', file=sys.stderr)
        raise
    print('双端配置同步并核对完成；未修改 Payment 配置、数据库或密钥版本。')


if __name__ == '__main__':
    try:
        main()
    except ConfigurationError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env python3
"""Kiểm tra file ngôn ngữ app/src/main/assets/i18n/<mã>.json so với các câu tiếng Việt trong mã nguồn.

Dùng:  python3 tools/i18n/check.py <mã> [--keys keys.json]
Lỗi (thoát mã 1): không đọc được JSON, thiếu _meta, thiếu câu, sai chỗ chèn {0}, {1}..., bản dịch rỗng.
Cảnh báo: câu còn nguyên tiếng Việt, khóa thừa.
"""
import json, os, re, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
VI = re.compile(r'[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ]', re.I)
PH = re.compile(r'\{(\d+)\}')

def keys_from_source():
    out = os.path.join(tempfile.gettempdir(), 'i18n_keys_check.json')
    subprocess.check_call([sys.executable, os.path.join(ROOT, 'tools/i18n/extract.py'),
                           os.path.join(ROOT, 'app/src/main/java'), out], stdout=subprocess.DEVNULL)
    return json.load(open(out, encoding='utf-8'))

def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__); sys.exit(2)
    code = args[0]
    keys = json.load(open(args[args.index('--keys') + 1], encoding='utf-8')) if '--keys' in args else keys_from_source()
    path = os.path.join(ROOT, 'app/src/main/assets/i18n', code + '.json')
    try:
        data = json.load(open(path, encoding='utf-8'))
    except Exception as e:
        print('LỖI: không đọc được', path, e); sys.exit(1)
    errors, warns = [], []
    meta = data.get('_meta')
    if not isinstance(meta, dict) or meta.get('code') != code or not meta.get('native') or not meta.get('english'):
        errors.append('thiếu hoặc sai _meta (cần code=%s, native, english)' % code)
    missing = [k for k in keys if k not in data]
    if missing: errors.append('thiếu %d câu, ví dụ: %r' % (len(missing), missing[:3]))
    extra = [k for k in data if not k.startswith('_') and k not in keys]
    if extra: warns.append('thừa %d khóa không còn trong mã, ví dụ: %r' % (len(extra), extra[:3]))
    same = 0
    for k, v in data.items():
        if k.startswith('_'): continue
        if not isinstance(v, str) or not v.strip():
            errors.append('bản dịch rỗng/không phải chuỗi: %r' % k[:60]); continue
        if sorted(PH.findall(k)) != sorted(PH.findall(v)):
            errors.append('sai chỗ chèn: %r -> %r' % (k[:60], v[:60]))
        if code != 'vi' and VI.search(v) and v == k: same += 1
    if same: warns.append('%d câu còn nguyên tiếng Việt (chưa dịch)' % same)
    for w in warns: print('cảnh báo:', w)
    for e in errors[:40]: print('LỖI:', e)
    print('%s: %d/%d câu, %d lỗi, %d cảnh báo' % (code, len(keys) - len(missing), len(keys), len(errors), len(warns)))
    sys.exit(1 if errors else 0)

main()

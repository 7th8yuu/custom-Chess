#!/usr/bin/env python3
"""Trích mọi chuỗi tiếng Việt trong mã Kotlin thành khóa mẫu ({0}, {1}... thay cho $x / ${...}).
Dùng: extract.py <thư mục nguồn> <file json ra>"""
import re, sys, json, os, glob

VI = re.compile(r'[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ]', re.I)

def scan_literals(src):
    """Trả về danh sách (start, end, raw_flag, body) cho mọi chuỗi trong file, bỏ qua chú thích."""
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); i = n if j < 0 else j; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            out.append((i, j + 3, True, src[i + 3:j])); i = j + 3; continue
        if c == "'":
            # ký tự: 'x' hoặc '\n' hoặc 'ሴ'
            j = i + 1
            if src[j] == '\\': j += 2
            else: j += 1
            while j < n and src[j] != "'": j += 1
            i = j + 1; continue
        if c == '"':
            j = i + 1; depth = 0
            while j < n:
                d = src[j]
                if d == '\\': j += 2; continue
                if src.startswith('${', j): depth += 1; j += 2; continue
                if d == '{' and depth > 0: depth += 1
                elif d == '}' and depth > 0: depth -= 1
                elif d == '"' and depth == 0: break
                j += 1
            out.append((i, j + 1, False, src[i + 1:j])); i = j + 1; continue
        i += 1
    return out

ESC = {'n': '\n', 't': '\t', '"': '"', '\\': '\\', '$': '$', "'": "'", 'r': '\r'}

def to_template(body, raw):
    """Đổi thân chuỗi Kotlin thành mẫu: bỏ escape, thay mọi $x/${..} bằng {k}."""
    out = []; k = 0; i = 0; n = len(body)
    while i < n:
        c = body[i]
        if not raw and c == '\\' and i + 1 < n:
            nx = body[i + 1]
            if nx == 'u' and i + 5 < n:
                out.append(chr(int(body[i + 2:i + 6], 16))); i += 6; continue
            out.append(ESC.get(nx, nx)); i += 2; continue
        if c == '$' and i + 1 < n:
            if body[i + 1] == '{':
                depth = 1; j = i + 2
                while j < n and depth:
                    if body[j] == '{': depth += 1
                    elif body[j] == '}': depth -= 1
                    j += 1
                out.append('{%d}' % k); k += 1; i = j; continue
            m = re.match(r'[A-Za-z_]\w*', body[i + 1:])
            if m:
                out.append('{%d}' % k); k += 1; i += 1 + m.end(); continue
        out.append(c); i += 1
    return ''.join(out)

def main():
    root, outp = sys.argv[1], sys.argv[2]
    keys = {}
    for f in sorted(glob.glob(os.path.join(root, '**', '*.kt'), recursive=True)):
        rel = os.path.relpath(f, root)
        if rel.endswith('core/Translator.kt'): continue  # bảng chữ cái tiếng Việt của bộ dịch, không phải câu hiển thị
        src = open(f, encoding='utf-8').read()
        lits = scan_literals(src)
        # chuỗi ghép bằng '+' (nối nhiều chuỗi ở nhiều dòng) -> thêm cả khóa ghép
        i = 0
        while i < len(lits):
            chain = [lits[i]]
            j = i
            while j + 1 < len(lits):
                gap = src[lits[j][1]:lits[j + 1][0]]
                if re.fullmatch(r'\s*\+\s*', gap): chain.append(lits[j + 1]); j += 1
                else: break
            for lit in chain:
                t = to_template(lit[3], lit[2])
                if VI.search(t): keys.setdefault(t, set()).add(rel)
            if len(chain) > 1:
                t = ''.join(to_template(l[3], l[2]) for l in chain)
                # chỉ số {k} khi ghép: đánh số lại theo thứ tự
                cnt = [0]
                t = re.sub(r'\{\d+\}', lambda m: '{%d}' % (cnt.__setitem__(0, cnt[0] + 1) or cnt[0] - 1), t)
                if VI.search(t): keys.setdefault(t, set()).add(rel)
            i = j + 1
    data = {k: sorted(v) for k, v in sorted(keys.items())}
    json.dump(data, open(outp, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    total_chars = sum(len(k) for k in data)
    print('khóa:', len(data), 'tổng ký tự:', total_chars)

main()

#!/usr/bin/env python3
"""Y11: удалить ВСЕ комментарии из Kotlin-кода (app/src), сохранив строки/символы/URL.

Правила:
- Снимаем `// ...` до конца строки и `/* ... */` (многострочные), НО только вне
  строковых литералов ("...", "...", triple-quoted) и символьных литералов ('...').
- В тройных строках (raw) комментарии не снимаем (это данные).
- Пустые строки, оставшиеся после удаления, схлопываются (не более одной подряд).
- KDoc (/** ... */) тоже удаляется (заказчик: «убери все комментарии»).
- Файлы тестов чистим так же.
Запуск: python tools/strip_comments.py [--check]
"""
import io
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src')


def strip(src: str) -> str:
    out = []
    i = 0
    n = len(src)
    state = 'code'  # code | line | block | str | raw | char
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nxt == '/':
                state = 'line'
                i += 2
                continue
            if c == '/' and nxt == '*':
                state = 'block'
                i += 2
                continue
            if c == '"':
                if src[i:i + 3] == '"""':
                    state = 'raw'
                    out.append('"""')
                    i += 3
                else:
                    state = 'str'
                    out.append(c)
                    i += 1
                continue
            if c == "'":
                state = 'char'
                out.append(c)
                i += 1
                continue
            out.append(c)
            i += 1
        elif state == 'line':
            if c == '\n':
                out.append('\n')
                state = 'code'
            i += 1
        elif state == 'block':
            if c == '*' and nxt == '/':
                state = 'code'
                i += 2
                # не оставляем висячий пробел перед концом строки
                continue
            i += 1
        elif state == 'str':
            out.append(c)
            if c == '\\':
                if nxt:
                    out.append(nxt)
                i += 2
                continue
            if c == '"':
                state = 'code'
            i += 1
        elif state == 'char':
            out.append(c)
            if c == '\\':
                if nxt:
                    out.append(nxt)
                i += 2
                continue
            if c == "'":
                state = 'code'
            i += 1
        elif state == 'raw':
            if src[i:i + 3] == '"""':
                out.append('"""')
                state = 'code'
                i += 3
            else:
                out.append(c)
                i += 1
    text = ''.join(out)
    # схлопнуть пустые строки и trailing-пробелы
    lines = []
    prev_blank = False
    for ln in text.split('\n'):
        s = ln.rstrip()
        blank = (s.strip() == '')
        if blank and prev_blank:
            continue
        lines.append(s)
        prev_blank = blank
    return '\n'.join(lines)


def main():
    check = '--check' in sys.argv
    changed = 0
    for dirpath, _, files in os.walk(ROOT):
        for f in files:
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dirpath, f)
            s = io.open(p, encoding='utf-8').read()
            r = strip(s)
            if r != s:
                changed += 1
                if not check:
                    io.open(p, 'w', encoding='utf-8', newline='').write(r)
    print(('would change ' if check else 'changed ') + str(changed) + ' files')


if __name__ == '__main__':
    main()

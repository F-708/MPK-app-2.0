#!/usr/bin/env python3
"""Kotlin comment stripper — tokenizer-based, not regex.

Handles: line comments (//), nested block comments (/* */), single-quoted and
triple-quoted string literals, char literals, and ${} interpolation (which may
itself contain strings/quotes/comments). Comments inside strings are preserved.

Usage: strip_comments.py FILE [FILE...]  (rewrites in place)
"""
import sys
import io


def strip(src: str) -> str:
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]

        # line comment
        if two == '//':
            j = src.find('\n', i)
            if j == -1:
                # comment to EOF: drop trailing whitespace already emitted on line
                _rstrip_pending(out)
                i = n
            else:
                _rstrip_pending(out)
                i = j  # keep the newline (handled by normal path)
            continue

        # block comment (nested)
        if two == '/*':
            depth = 1
            j = i + 2
            while j < n and depth:
                if src[j:j + 2] == '/*':
                    depth += 1
                    j += 2
                elif src[j:j + 2] == '*/':
                    depth -= 1
                    j += 2
                else:
                    j += 1
            had_newline = '\n' in src[i:j]
            _rstrip_pending(out)
            if had_newline and out and out[-1] != '\n':
                out.append('\n')
            i = j
            continue

        # triple-quoted string
        if src[i:i + 3] == '"""':
            j = _consume_string(src, i, triple=True)
            out.append(src[i:j])
            i = j
            continue

        # single-quoted string
        if c == '"':
            j = _consume_string(src, i, triple=False)
            out.append(src[i:j])
            i = j
            continue

        # char literal
        if c == "'":
            j = _consume_char(src, i)
            out.append(src[i:j])
            i = j
            continue

        out.append(c)
        i += 1

    return ''.join(out)


def _rstrip_pending(out):
    while out and out[-1] in ' \t':
        out.pop()


def _consume_string(src, i, triple):
    """Return index just past the string literal starting at i. Handles ${}."""
    n = len(src)
    if triple:
        i += 3
        while i < n:
            if src[i:i + 3] == '"""':
                return i + 3
            if src[i:i + 2] == '${':
                i = _consume_interp(src, i + 1)  # at '{'
                continue
            if src[i] == '$' and i + 1 < n and (src[i + 1].isalnum() or src[i + 1] == '_'):
                i += 2
                continue
            i += 1
        return n
    else:
        i += 1
        while i < n:
            ch = src[i]
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                return i + 1
            if ch == '\n':
                return i  # unterminated; bail
            if src[i:i + 2] == '${':
                i = _consume_interp(src, i + 1)
                continue
            i += 1
        return n


def _consume_interp(src, i):
    """i points at '{' of ${...}. Return index just past matching '}'."""
    n = len(src)
    depth = 0
    while i < n:
        ch = src[i]
        if ch == '{':
            depth += 1
            i += 1
        elif ch == '}':
            depth -= 1
            i += 1
            if depth == 0:
                return i
        elif ch == '"':
            triple = src[i:i + 3] == '"""'
            i = _consume_string(src, i, triple)
        elif src[i:i + 3] == '"""':
            i = _consume_string(src, i, True)
        elif ch == "'":
            i = _consume_char(src, i)
        elif src[i:i + 2] == '//':
            j = src.find('\n', i)
            i = n if j == -1 else j
        elif src[i:i + 2] == '/*':
            d = 1
            i += 2
            while i < n and d:
                if src[i:i + 2] == '/*':
                    d += 1; i += 2
                elif src[i:i + 2] == '*/':
                    d -= 1; i += 2
                else:
                    i += 1
        else:
            i += 1
    return n


def _consume_char(src, i):
    n = len(src)
    i += 1
    while i < n:
        if src[i] == '\\':
            i += 2
            continue
        if src[i] == "'":
            return i + 1
        if src[i] == '\n':
            return i
        i += 1
    return n


def collapse_blank_lines(src: str) -> str:
    lines = src.split('\n')
    out = []
    blank_run = 0
    for ln in lines:
        if ln.strip() == '':
            blank_run += 1
            if blank_run <= 1:
                out.append('')
        else:
            blank_run = 0
            out.append(ln)
    return '\n'.join(out)


def main():
    for path in sys.argv[1:]:
        raw = io.open(path, encoding='utf-8').read()
        stripped = collapse_blank_lines(strip(raw))
        if stripped != raw:
            io.open(path, 'w', encoding='utf-8', newline='').write(stripped)
            print(f"stripped {path}")


if __name__ == '__main__':
    main()

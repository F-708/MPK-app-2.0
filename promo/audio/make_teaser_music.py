#!/usr/bin/env python3
"""Беспокойный минимал-электроник cue под раскадровку трейлера (30.0 c, 100 BPM).

Структура синхронизирована со SCRIPT_LOCKED_v1.0.md:
  0.0-2.0   пэд без ударных (хук-вле титра «Мой Политех»)
  2.0-26.0  грув: kick каждый бит, hat офф-бит, бас по тактам, arp 8-ми
  26.0-28.0 low-pass «приседание» под блок-загадку «???» (ударные/arp гаснут)
  28.0-29.0 короткий noise-riser
  29.0-30.0 финальный аккорд + kick, фейд 0.4 c
Лицензия: оригинал, создан для проекта — CC0 (promo/licenses/teaser_v1_LICENSE.txt).
"""
import math
import wave

import numpy as np

SR = 44100
BPM = 100.0
BEAT = 60.0 / BPM          # 0.6 s
BAR = BEAT * 4             # 2.4 s
DUR = 30.0
N = int(SR * DUR)
t_all = np.arange(N) / SR

buf = np.zeros(N, dtype=np.float64)


def add(sig, t0):
    i0 = int(t0 * SR)
    i1 = min(N, i0 + len(sig))
    if i0 < N:
        buf[i0:i1] += sig[: i1 - i0]


def env(n, a, d, sustain=1.0):
    t = np.arange(n) / SR
    e = np.minimum(t / max(a, 1e-4), 1.0)
    tail = np.exp(-np.maximum(t - a, 0) / max(d, 1e-4))
    return e * (sustain + (1 - sustain) * 1.0) * tail


def kick(t0, gain=1.0):
    n = int(0.16 * SR)
    t = np.arange(n) / SR
    f = 55 * np.exp(-t / 0.045) + 40
    ph = 2 * np.pi * np.cumsum(f) / SR
    sig = np.sin(ph) * np.exp(-t / 0.09) * gain
    add(sig, t0)


def hat(t0, gain=0.22):
    n = int(0.05 * SR)
    noise = np.random.default_rng(int(t0 * 1000)).standard_normal(n)
    hp = noise - np.convolve(noise, np.ones(8) / 8, mode="same")
    sig = hp * np.exp(-np.arange(n) / SR / 0.018) * gain
    add(sig, t0)


def tone(freq, dur, gain, a=0.01, d=0.3, detune=0.0, kind="sine"):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = freq * (1 + detune)
    if kind == "sine":
        w = np.sin(2 * np.pi * f * t)
    elif kind == "tri":
        w = 2 / np.pi * np.arcsin(np.sin(2 * np.pi * f * t))
    else:
        w = np.sign(np.sin(2 * np.pi * f * t)) * 0.4
    e = np.minimum(t / a, 1.0) * np.exp(-np.maximum(t - a, 0) / d)
    return w * e * gain


def pad_chord(freqs, t0, dur, gain=0.16):
    for i, f in enumerate(freqs):
        add(tone(f, dur, gain, a=0.35, d=dur * 0.9, detune=0.0015 * (i - 1.5)), t0)
        add(tone(f, dur, gain * 0.6, a=0.35, d=dur * 0.9, detune=-0.0015 * (i - 1.5)), t0)


def bass_note(freq, t0, dur, gain=0.30):
    add(tone(freq, dur, gain, a=0.01, d=0.5, kind="tri"), t0)
    add(tone(freq / 2, dur, gain * 0.4, a=0.01, d=0.5), t0)


def arp_note(freq, t0, gain=0.10):
    add(tone(freq, 0.22, gain, a=0.005, d=0.12, kind="tri"), t0)


def noise_riser(t0, dur, gain=0.12):
    n = int(dur * SR)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n)
    lp = np.convolve(noise, np.ones(40) / 40, mode="same")
    ramp = np.linspace(0.1, 1.0, n) ** 2
    add(lp * ramp * gain, t0)


# --- гармония: Am | F | C | G (2 такта каждый круг) ---
PROG = [
    (220.0, [220.0, 261.63, 329.63, 392.0], 110.0, [440.0, 523.25, 659.25]),   # Am
    (174.61, [174.61, 220.0, 261.63, 349.23], 87.31, [349.23, 440.0, 523.25]),  # F
    (261.63, [261.63, 329.63, 392.0, 493.88], 130.81, [523.25, 659.25, 783.99]),  # C
    (196.0, [196.0, 246.94, 293.66, 392.0], 98.0, [392.0, 493.88, 587.33]),    # G
]

# 0.0-2.0: пэд-вступление (Am)
pad_chord(PROG[0][1], 0.0, 2.0, gain=0.14)

# 2.0-26.0: грув
t = 2.0
bar = 0
while t < 26.0 - 1e-6:
    root, chord, bfreq, arp = PROG[bar % 4]
    pad_chord(chord, t, BAR, gain=0.13)
    bass_note(bfreq, t, BAR * 0.9)
    for b in range(4):
        kick(t + b * BEAT)
        hat(t + b * BEAT + BEAT / 2)
    for e8 in range(8):
        arp_note(arp[e8 % len(arp)] * (2 if e8 % 4 == 3 else 1), t + e8 * BEAT / 2, gain=0.07)
    t += BAR
    bar += 1

# 26.0-28.0: приседание (пэд только, без ударных/arp/баса)
pad_chord(PROG[0][1], 26.0, 2.0, gain=0.15)

# 28.0-29.0: riser
noise_riser(28.0, 1.0)

# 29.0-30.0: финальный аккорд + kick
kick(29.0, gain=1.1)
pad_chord([220.0, 277.18, 329.63, 493.88], 29.0, 1.0, gain=0.18)  # A add9-ish ярко
bass_note(110.0, 29.0, 0.9)

# фейд хвоста
fade = np.ones(N)
f0 = int(29.6 * SR)
fade[f0:] = np.linspace(1, 0, N - f0) ** 1.5
buf *= fade

# нормализация до -1.0 dBFS peak, лёгкий soft-clip
peak = np.max(np.abs(buf))
buf = buf / peak * 0.89
buf = np.tanh(buf * 1.1) / np.tanh(1.1)

# stereo: лёгкое расширение пэда (Haas 8ms на правом канале только для низких? проще: mid/side)
side = np.roll(buf, int(0.006 * SR)) - buf
left = buf + 0.12 * side
right = buf - 0.12 * side
st = np.stack([left, right], axis=1)
st = st / np.max(np.abs(st)) * 0.89
pcm = (st * 32767).astype("<i2")

with wave.open("promo/audio/teaser_v1.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("written promo/audio/teaser_v1.wav", DUR, "s", BPM, "BPM")

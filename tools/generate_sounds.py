#!/usr/bin/env python3
"""
ROADIUM 경기장 효과음 생성 스크립트 (개발용, 앱 실행 중에는 쓰지 않음)

모든 효과음을 코드로 합성해 app/src/main/res/raw 에 넣는다. 외부 음원·중계방송 음성을 쓰지 않으므로
사용권 문제가 없다 (기획서: 효과음은 자체 제작하거나 사용권을 확보한 음원만 사용).

  sfx_homerun        타격음 + 큰 관중 함성 (4.0s)
  sfx_score          타격음 + 관중 함성 (3.0s)
  sfx_hit            타격음 + 짧은 환호 (1.6s)
  sfx_mitt           포수 미트 소리 (0.9s)
  sfx_siren          경기 종료 사이렌 (3.0s)
  sfx_goal           골 함성 (4.0s)
  sfx_whistle_short  주심 휘슬 한 번 (1.0s)
  sfx_whistle_half   주심 휘슬 두 번 (전·후반) (1.8s)
  sfx_whistle_end    종료 휘슬 세 번 (3.0s)

음원 기준: 2~4초 이내, 앞에 60ms 선행 묵음(첫소리 잘림 방지), 끝 페이드아웃, 피크 정규화,
22.05kHz 16bit 모노 WAV. 길이는 앱의 SoundPlayer.durations와 같게 맞춘다.

필요 도구: Python 3 표준 라이브러리만
사용법: python3 tools/generate_sounds.py
"""
import math
import os
import random
import struct
import wave

SR = 22050
LEAD_MS = 60
PEAK = 0.89
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")

# 앱 SoundPlayer.durations 와 같은 값 (ms)
LENGTH_MS = {
    "sfx_homerun": 4000,
    "sfx_score": 3000,
    "sfx_hit": 1600,
    "sfx_mitt": 900,
    "sfx_siren": 3000,
    "sfx_goal": 4000,
    "sfx_whistle_short": 1000,
    "sfx_whistle_half": 1800,
    "sfx_whistle_end": 3000,
}

rng = random.Random(20261114)


def n(sec):
    return int(SR * sec)


def zeros(sec):
    return [0.0] * n(sec)


def mix(dst, src, at_sec=0.0, gain=1.0):
    o = n(at_sec)
    need = o + len(src)
    if need > len(dst):
        dst.extend([0.0] * (need - len(dst)))
    for i, s in enumerate(src):
        dst[o + i] += s * gain
    return dst


def lowpass(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / SR)
    y, out = 0.0, []
    for s in x:
        y += a * (s - y)
        out.append(y)
    return out


def highpass(x, cutoff):
    lp = lowpass(x, cutoff)
    return [a - b for a, b in zip(x, lp)]


def bandpass(x, lo, hi):
    return lowpass(highpass(x, lo), hi)


def noise(sec):
    return [rng.uniform(-1, 1) for _ in range(n(sec))]


def envelope(x, attack, hold, release):
    """attack/hold/release in seconds (release = 남은 구간 동안 지수 감쇠)"""
    a, h = n(attack), n(hold)
    out = []
    for i, s in enumerate(x):
        if i < a:
            g = i / max(a, 1)
        elif i < a + h:
            g = 1.0
        else:
            t = (i - a - h) / SR
            g = math.exp(-t / max(release, 1e-3))
        out.append(s * g)
    return out


def reverb(x, delays=(0.067, 0.113, 0.171), fb=0.32):
    """경기장 울림: 짧은 피드백 딜레이 몇 개"""
    out = list(x) + [0.0] * n(0.6)
    for d in delays:
        k = n(d)
        for i in range(k, len(out)):
            out[i] += out[i - k] * fb * 0.5
    return out


# ── 소리 재료 ────────────────────────────────────────────────
def bat_crack():
    """알루미늄이 아닌 나무 배트의 '딱!': 짧은 노이즈 임펄스 + 공명 두 개"""
    sec = 0.25
    x = []
    for i in range(n(sec)):
        t = i / SR
        click = rng.uniform(-1, 1) * math.exp(-t / 0.004)
        ring = 0.6 * math.sin(2 * math.pi * 1750 * t) * math.exp(-t / 0.025)
        ring += 0.35 * math.sin(2 * math.pi * 3150 * t) * math.exp(-t / 0.015)
        body = 0.5 * math.sin(2 * math.pi * 420 * t) * math.exp(-t / 0.04)
        x.append(click + ring + body)
    return reverb(x, fb=0.25)


def crowd(sec, intensity=1.0, voices=26, swell=0.35):
    """관중 함성: 분홍빛 노이즈 + 여러 '와아' 목소리(포먼트 흉내)를 겹친다"""
    base = bandpass(noise(sec), 250, 2600)
    base = [s * 0.55 for s in base]
    total = len(base)
    out = base[:]
    for _ in range(voices):
        f0 = rng.uniform(180, 520)
        vib = rng.uniform(4, 7)
        start = rng.uniform(0, swell)
        dur = rng.uniform(sec * 0.5, sec)
        amp = rng.uniform(0.15, 0.35)
        ph = rng.uniform(0, 6.28)
        s0, s1 = n(start), min(total, n(start + dur))
        for i in range(s0, s1):
            t = (i - s0) / SR
            f = f0 * (1 + 0.012 * math.sin(2 * math.pi * vib * t)) * (1 + 0.08 * min(t / 0.4, 1))
            # 성대음(톱니파 근사)의 하모닉 3개
            v = (math.sin(ph + 2 * math.pi * f * t) + 0.5 * math.sin(2 * ph + 4 * math.pi * f * t)
                 + 0.25 * math.sin(3 * ph + 6 * math.pi * f * t))
            g = min(t / 0.12, 1) * min((s1 - i) / SR / 0.3, 1)
            out[i] += v * amp * g
    # 함성이 커졌다가 잦아드는 모양
    out = envelope(out, swell, sec * 0.35, sec * 0.35)
    out = lowpass(out, 3800)
    return [s * intensity for s in out]


def mitt_pop():
    """포수 미트 '퍽': 낮은 쿵 + 가죽 마찰음"""
    x = []
    for i in range(n(0.35)):
        t = i / SR
        thump = math.sin(2 * math.pi * (160 - 80 * t) * t) * math.exp(-t / 0.05)
        slap = rng.uniform(-1, 1) * math.exp(-t / 0.012) * 0.6
        x.append(thump + slap)
    return reverb(lowpass(x, 2500), fb=0.2)


def whistle(sec):
    """심판 휘슬: 2.9/3.25kHz 두 음의 맥놀이 + 구슬이 굴러가는 떨림(약 28Hz)"""
    x = []
    for i in range(n(sec)):
        t = i / SR
        trill = 0.65 + 0.35 * math.sin(2 * math.pi * 28 * t)
        v = math.sin(2 * math.pi * 2900 * t) + 0.7 * math.sin(2 * math.pi * 3250 * t)
        v += 0.08 * rng.uniform(-1, 1)  # 숨소리
        a = min(t / 0.02, 1) * min((sec - t) / 0.04, 1)
        x.append(v * trill * a)
    return x


def siren(sec):
    """경기 종료 사이렌: 오르내리는 경적 (홀수 하모닉)"""
    x, ph = [], 0.0
    for i in range(n(sec)):
        t = i / SR
        f = 420 + 260 * (0.5 - 0.5 * math.cos(2 * math.pi * t / (sec * 0.9)))
        ph += 2 * math.pi * f / SR
        v = math.sin(ph) + 0.33 * math.sin(3 * ph) + 0.2 * math.sin(5 * ph)
        a = min(t / 0.08, 1) * min((sec - t) / 0.25, 1)
        x.append(v * a)
    return reverb(lowpass(x, 3000), fb=0.3)


# ── 합성 ────────────────────────────────────────────────────
def build():
    s = {}
    hr = zeros(0.1)
    mix(hr, bat_crack(), 0.0, 1.0)
    mix(hr, crowd(3.6, 0.9, voices=34, swell=0.5), 0.12)
    s["sfx_homerun"] = hr

    sc = zeros(0.1)
    mix(sc, bat_crack(), 0.0, 1.0)
    mix(sc, crowd(2.6, 0.7, voices=24, swell=0.35), 0.1)
    s["sfx_score"] = sc

    hit = zeros(0.1)
    mix(hit, bat_crack(), 0.0, 1.0)
    mix(hit, crowd(1.3, 0.35, voices=12, swell=0.2), 0.12)
    s["sfx_hit"] = hit

    s["sfx_mitt"] = mitt_pop()
    s["sfx_siren"] = siren(2.7)

    goal = crowd(3.8, 1.0, voices=40, swell=0.25)
    s["sfx_goal"] = goal

    s["sfx_whistle_short"] = whistle(0.55)
    wh = zeros(0.1)
    mix(wh, whistle(0.35), 0.0)
    mix(wh, whistle(0.7), 0.6)
    s["sfx_whistle_half"] = wh
    we = zeros(0.1)
    mix(we, whistle(0.4), 0.0)
    mix(we, whistle(0.4), 0.65)
    mix(we, whistle(1.2), 1.3)
    s["sfx_whistle_end"] = we
    return s


def finish(name, samples):
    total = n(LENGTH_MS[name] / 1000)
    lead = n(LEAD_MS / 1000)
    body_len = total - lead
    body = samples[:body_len] + [0.0] * max(0, body_len - len(samples))
    # 끝 150ms 페이드아웃 (잘린 소리가 '툭' 끊기지 않게)
    fade = n(0.15)
    for i in range(fade):
        body[body_len - fade + i] *= 1 - i / fade
    peak = max(abs(v) for v in body) or 1.0
    return [0.0] * lead + [v / peak * PEAK for v in body]


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in samples))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, samples in build().items():
        out = finish(name, samples)
        path = os.path.join(OUT, name + ".wav")
        write_wav(path, out)
        print(f"{name:20s} {len(out) / SR:.2f}s → {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()

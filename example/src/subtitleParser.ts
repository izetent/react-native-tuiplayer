export type SubtitleCue = {
  start: number;
  end: number;
  text: string;
};

const TIME_RE = /(\d+):(\d+):(\d+)[,.](\d{1,3})/;

function timeToSeconds(raw: string): number | null {
  const match = TIME_RE.exec(raw.trim());
  if (!match) return null;
  const [, h, m, s, ms] = match;
  if (!h || !m || !s || !ms) return null;
  return (
    Number(h) * 3600 +
    Number(m) * 60 +
    Number(s) +
    Number(ms.padEnd(3, '0')) / 1000
  );
}

function parseBlock(block: string): SubtitleCue | null {
  const lines = block
    .split(/\r?\n/)
    .filter((line): line is string => !!line && line.length > 0);
  if (lines.length < 2) {
    return null;
  }
  const first = lines[0];
  const second = lines[1];
  const timeLine =
    first && first.includes('-->')
      ? first
      : second && second.includes('-->')
        ? second
        : null;
  if (!timeLine) return null;
  const split = timeLine.split('-->').map((t) => t.trim());
  if (split.length < 2 || !split[0] || !split[1]) {
    return null;
  }
  const [startRaw, endRaw] = split;
  const start = timeToSeconds(startRaw);
  const end = timeToSeconds(endRaw);
  if (
    start == null ||
    end == null ||
    !Number.isFinite(start) ||
    !Number.isFinite(end)
  ) {
    return null;
  }
  const textLines = lines.slice(timeLine === lines[0] ? 1 : 2);
  return {
    start,
    end,
    text: textLines.join('\n'),
  };
}

export function parseSubtitles(raw: string): SubtitleCue[] {
  if (!raw || typeof raw !== 'string') return [];
  const trimmed = raw.replace(/\uFEFF/g, '').trim();
  const body = trimmed.startsWith('WEBVTT')
    ? trimmed.replace(/^WEBVTT.*\r?\n/, '')
    : trimmed;
  return body
    .split(/\r?\n\r?\n/)
    .map((block) => parseBlock(block))
    .filter((cue): cue is SubtitleCue => !!cue);
}

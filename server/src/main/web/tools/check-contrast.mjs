#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const tokensFile = join(webRoot, 'projects/core/src/theme-tokens.scss');
const indexFile = join(webRoot, 'projects/core/src/index.html');

const TEXT = 4.5;
const GRAPHIC = 3;

// Material system colours the panel puts our tokens on, as emitted by mat.theme() for the
// cyan/orange palettes in custom-theme.scss (light-dark() pairs in the built styles.css).
const MATERIAL = {
  light: {
    'mat-surface': '#f7faf9',
    'mat-surface-container': '#ebefed',
    'mat-surface-container-high': '#e6e9e7',
    'mat-error': '#ba1a1a',
    'mat-on-surface-variant': '#3f4948',
  },
  dark: {
    'mat-surface': '#101414',
    'mat-surface-container': '#1c2020',
    'mat-surface-container-high': '#272b2a',
    'mat-error': '#ffb4ab',
    'mat-on-surface-variant': '#dae5e4',
  },
};

const textBackgrounds = [
  'page-bg',
  'surface',
  'surface-alt',
  'surface-hover',
  'field-bg',
  'dialog-bg',
  'hover@surface',
];
const textColours = [
  'text',
  'text-muted',
  'primary',
  'link-hover',
  'accent',
  'danger',
  'success',
  'warning',
  'mat-error',
  'mat-on-surface-variant',
];
const statusColours = ['status-ok', 'status-error', 'status-warn', 'status-unknown'];
const rowBackgrounds = ['page-bg', 'surface', 'surface-alt', 'hover@surface'];
const chartFills = ['chart-blue', 'chart-blue-light', 'chart-red', 'chart-yellow'];
const snackbarStripes = ['danger', 'success', 'primary', 'status-warn'];

// Fleet health plugin: badge and notice text sits on a tint of its own colour (name*percent;
// badges over the field background), red table rows are tinted with danger, hovered rows with
// primary. Chart lines (primary, status-warn, status-error) are covered by the pairs above.
const badgeColours = ['success', 'warning', 'danger', 'primary', 'text-muted'];
const tintedRows = ['danger*5@surface', 'primary*6@surface'];
const noticeColours = ['warning', 'danger', 'primary'];

const checks = [
  ...textColours.flatMap((fg) => textBackgrounds.map((bg) => [fg, bg, TEXT])),
  ['danger', 'danger-bg', TEXT],
  ['success', 'success-bg', TEXT],
  ['text', 'danger-bg', TEXT],
  ['on-primary', 'primary', TEXT],
  ['on-primary', 'accent', TEXT],
  ['on-primary', 'danger', TEXT],
  ['on-primary-fill', 'primary-fill', TEXT],
  ['on-warning-fill', 'warning-fill', TEXT],
  ['sidenav-text', 'sidenav-bg', TEXT],
  ['sidenav-muted', 'sidenav-bg', TEXT],
  ['brand', 'sidenav-bg', GRAPHIC],
  ...['chart-blue-text', 'chart-red-text', 'chart-yellow-text', 'chart-text'].flatMap((fg) =>
    ['page-bg', 'surface'].map((bg) => [fg, bg, TEXT]),
  ),
  ['border-strong', 'field-bg', GRAPHIC],
  ...statusColours.flatMap((fg) => rowBackgrounds.map((bg) => [fg, bg, GRAPHIC])),
  ...chartFills.flatMap((fg) => ['page-bg', 'surface'].map((bg) => [fg, bg, GRAPHIC])),
  ...snackbarStripes.map((fg) => [fg, 'dialog-bg', GRAPHIC]),
  ['text', 'dialog-bg', TEXT],
  ['primary', 'mat-surface', TEXT],
  ['text-muted', 'mat-surface', TEXT],
  ['link-hover', 'mat-surface', TEXT],
  ['primary', 'mat-surface-container', GRAPHIC],
  ['primary', 'mat-surface-container-high', GRAPHIC],
  ...badgeColours.map((fg) => [fg, `${fg}*10@field-bg`, TEXT]),
  ...['text', 'text-muted'].flatMap((fg) => tintedRows.map((bg) => [fg, bg, TEXT])),
  ...noticeColours.flatMap((c) => [
    ['text', `${c}*8@surface`, TEXT],
    [c, `${c}*8@surface`, GRAPHIC],
  ]),
  ['status-warn', 'danger*5@surface', GRAPHIC],
];

const informational = [
  ['brand', 'surface'],
  ['status-error*15', 'surface'],
];

function parseBlocks(source) {
  const blocks = {};
  for (const match of source.matchAll(/@mixin\s+([\w-]+)\s*\{([^}]*)\}/g)) {
    const tokens = {};
    for (const line of match[2].matchAll(/--l24-([\w-]+)\s*:\s*([^;]+);/g)) {
      tokens[line[1]] = line[2].trim();
    }
    blocks[match[1]] = tokens;
  }
  return blocks;
}

function parseColour(value) {
  const hex = value.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hex) {
    const h = hex[1].length === 3 ? [...hex[1]].map((c) => c + c).join('') : hex[1];
    return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)).concat(1);
  }
  const rgb = value.match(/^rgba?\(([^)]+)\)$/i);
  if (rgb) {
    const parts = rgb[1].split(',').map((p) => parseFloat(p));
    return [parts[0], parts[1], parts[2], parts[3] ?? 1];
  }
  throw new Error(`unsupported colour value: ${value}`);
}

function over(fg, bg) {
  const a = fg[3];
  return [0, 1, 2].map((i) => fg[i] * a + bg[i] * (1 - a)).concat(1);
}

function luminance([r, g, b]) {
  const lin = (v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

function resolver(tokens) {
  const colour = (name) => {
    const at = name.indexOf('@');
    if (at >= 0) {
      return over(colour(name.slice(0, at)), colour(name.slice(at + 1)));
    }
    const tint = name.match(/^([\w-]+)\*(\d+)$/);
    if (tint) {
      const [r, g, b, a] = colour(tint[1]);
      return [r, g, b, (a * Number(tint[2])) / 100];
    }
    const value = tokens[name];
    if (value === undefined) {
      throw new Error(`unknown token --l24-${name}`);
    }
    const ref = value.match(/^var\(--l24-([\w-]+)\)$/);
    return ref ? colour(ref[1]) : parseColour(value);
  };
  return colour;
}

const blocks = parseBlocks(readFileSync(tokensFile, 'utf8'));
let failures = 0;

for (const theme of ['light', 'dark']) {
  if (!blocks[theme]) {
    throw new Error(`theme block @mixin ${theme} missing`);
  }
  const colour = resolver({ ...MATERIAL[theme], ...blocks.brand, ...blocks[theme] });
  console.log(`\n${theme}`);
  for (const [fg, bg, min] of checks) {
    const background = colour(bg);
    const value = ratio(over(colour(fg), background), background);
    const ok = value >= min;
    failures += ok ? 0 : 1;
    console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${fg} on ${bg}: ${value.toFixed(2)} (min ${min})`);
  }
  for (const [fg, bg] of informational) {
    const background = colour(bg);
    const value = ratio(over(colour(fg), background), background);
    console.log(`  info ${fg} on ${bg}: ${value.toFixed(2)}`);
  }
}

const bootBlock = readFileSync(indexFile, 'utf8').match(
  /l24-allow-color-start[\s\S]*?html\[data-theme='dark'\]\s*\{([^}]*)\}/,
);
const bootTokens = Object.fromEntries(
  [...(bootBlock?.[1] ?? '').matchAll(/--l24-([\w-]+)\s*:\s*([^;]+);/g)].map((m) => [
    m[1],
    m[2].trim(),
  ]),
);
const bootDiff = [...new Set([...Object.keys(blocks.dark), ...Object.keys(bootTokens)])].filter(
  (name) => blocks.dark[name] !== bootTokens[name],
);
console.log(
  `\nindex.html boot dark tokens ${bootDiff.length ? 'differ from theme-tokens.scss: ' + bootDiff.join(', ') : 'match theme-tokens.scss'}`,
);
failures += bootDiff.length;

if (failures > 0) {
  console.log(`\n${failures} check(s) failed`);
  process.exit(1);
}
console.log('\ncontrast check passed');

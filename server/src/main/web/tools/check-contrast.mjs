#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const tokensFile = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  'projects/core/src/theme-tokens.scss',
);

const TEXT = 4.5;
const GRAPHIC = 3;

const textBackgrounds = [
  'page-bg',
  'surface',
  'surface-alt',
  'surface-hover',
  'field-bg',
  'dialog-bg',
  'hover@surface',
];
const textColours = ['text', 'text-muted', 'primary', 'accent', 'danger', 'success', 'warning'];

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
];

const informational = [
  ...['status-ok', 'status-error', 'status-warn', 'status-unknown', 'status-alert'].map((fg) => [
    fg,
    'surface',
  ]),
  ...['chart-blue', 'chart-blue-light', 'chart-red', 'chart-yellow'].map((fg) => [fg, 'page-bg']),
  ['brand', 'surface'],
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
    if (name.includes('@')) {
      const [top, base] = name.split('@');
      return over(colour(top), colour(base));
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
  const colour = resolver({ ...blocks.brand, ...blocks[theme] });
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

if (failures > 0) {
  console.log(`\n${failures} contrast check(s) below WCAG AA`);
  process.exit(1);
}
console.log('\ncontrast check passed');

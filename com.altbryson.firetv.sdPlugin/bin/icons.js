// Key images, drawn as SVG (144x144) and sent to Stream Deck as data URLs.
'use strict';

const BG = '#14161b';
const FG = '#f3f4f6';
const DIM = '#8b92a0';

const GLYPHS = {
  up: '<path d="M72 34 L104 78 H40 Z" fill="FG"/>',
  down: '<path d="M72 98 L104 54 H40 Z" fill="FG"/>',
  left: '<path d="M38 66 L82 34 V98 Z" fill="FG"/>',
  right: '<path d="M106 66 L62 34 V98 Z" fill="FG"/>',
  select: '<circle cx="72" cy="66" r="30" fill="none" stroke="FG" stroke-width="7"/><text x="72" y="76" font-size="28" font-weight="800" text-anchor="middle" fill="FG" font-family="Segoe UI, sans-serif">OK</text>',
  back: '<path d="M58 30 L34 54 L58 78" fill="none" stroke="FG" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/><path d="M38 54 H82 a20 20 0 0 1 0 40 H66" fill="none" stroke="FG" stroke-width="9" stroke-linecap="round"/>',
  home: '<path d="M72 32 L108 62 V100 H84 V78 H60 V100 H36 V62 Z" fill="FG"/>',
  menu: '<g fill="FG"><rect x="40" y="42" width="64" height="9" rx="4"/><rect x="40" y="62" width="64" height="9" rx="4"/><rect x="40" y="82" width="64" height="9" rx="4"/></g>',
  playpause: '<path d="M34 40 L70 66 L34 92 Z" fill="FG"/><g fill="FG"><rect x="80" y="40" width="11" height="52" rx="2"/><rect x="97" y="40" width="11" height="52" rx="2"/></g>',
  rewind: '<path d="M70 40 L34 66 L70 92 Z M108 40 L72 66 L108 92 Z" fill="FG"/>',
  ff: '<path d="M36 40 L72 66 L36 92 Z M74 40 L110 66 L74 92 Z" fill="FG"/>',
  volup: '<path d="M30 54 H48 L72 34 V98 L48 78 H30 Z" fill="FG"/><path d="M88 66 H116 M102 52 V80" stroke="FG" stroke-width="8" stroke-linecap="round"/>',
  voldown: '<path d="M30 54 H48 L72 34 V98 L48 78 H30 Z" fill="FG"/><path d="M88 66 H116" stroke="FG" stroke-width="8" stroke-linecap="round"/>',
  mute: '<path d="M30 54 H48 L72 34 V98 L48 78 H30 Z" fill="FG"/><path d="M88 52 L114 80 M114 52 L88 80" stroke="FG" stroke-width="8" stroke-linecap="round"/>',
  guide: '<rect x="30" y="38" width="84" height="58" rx="6" fill="none" stroke="FG" stroke-width="7"/><path d="M30 56 H114 M58 56 V96" stroke="FG" stroke-width="6"/><path d="M54 30 L64 38 M90 30 L80 38" stroke="FG" stroke-width="6" stroke-linecap="round"/>',
  search: '<circle cx="64" cy="60" r="24" fill="none" stroke="FG" stroke-width="9"/><path d="M82 78 L106 102" stroke="FG" stroke-width="10" stroke-linecap="round"/>',
  sleep: '<path d="M86 34 a34 34 0 1 0 22 56 a28 28 0 0 1 -22 -56 Z" fill="FG"/>',
  apps: '<g fill="FG"><rect x="34" y="28" width="20" height="20" rx="4"/><rect x="62" y="28" width="20" height="20" rx="4"/><rect x="90" y="28" width="20" height="20" rx="4"/><rect x="34" y="56" width="20" height="20" rx="4"/><rect x="62" y="56" width="20" height="20" rx="4"/><rect x="90" y="56" width="20" height="20" rx="4"/><rect x="34" y="84" width="20" height="20" rx="4"/><rect x="62" y="84" width="20" height="20" rx="4"/><rect x="90" y="84" width="20" height="20" rx="4"/></g>',
  remote: '<rect x="54" y="20" width="36" height="86" rx="16" fill="none" stroke="FG" stroke-width="7"/><circle cx="72" cy="48" r="11" fill="none" stroke="FG" stroke-width="6"/><circle cx="65" cy="76" r="4" fill="FG"/><circle cx="79" cy="76" r="4" fill="FG"/><circle cx="65" cy="91" r="4" fill="FG"/><circle cx="79" cy="91" r="4" fill="FG"/>',
  wake: '<circle cx="72" cy="66" r="18" fill="FG"/><g stroke="FG" stroke-width="7" stroke-linecap="round"><path d="M72 28 V38 M72 94 V104 M34 66 H44 M100 66 H110 M45 39 L52 46 M92 86 L99 93 M45 93 L52 86 M92 46 L99 39"/></g>',
};

const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const url = svg => 'data:image/svg+xml;charset=utf8,' + encodeURIComponent(svg);

function keyImage(key, deviceLabel, state) {
  const fg = state === 'error' ? '#f87171' : FG;
  const glyph = (GLYPHS[key.glyph] || '').replace(/FG/g, fg);
  const label = key.name.replace(/ \(.*\)$/, '');
  return url(`<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
<rect width="144" height="144" fill="${BG}"/>${glyph}
<text x="72" y="126" font-size="17" font-weight="700" text-anchor="middle" fill="${fg}" font-family="Segoe UI, sans-serif">${esc(label)}</text>
${deviceLabel ? `<text x="72" y="17" font-size="12" text-anchor="middle" fill="${DIM}" font-family="Segoe UI, sans-serif">${esc(deviceLabel)}</text>` : ''}</svg>`);
}

function appImage(app, name, deviceLabel, state) {
  const words = String(name).split(/\s+/);
  const lines = words.length > 1 && name.length > 9 ? [words.slice(0, Math.ceil(words.length / 2)).join(' '), words.slice(Math.ceil(words.length / 2)).join(' ')] : [name];
  const size = Math.min(30, Math.floor(230 / Math.max(...lines.map(l => l.length), 4)));
  const y0 = 76 - (lines.length - 1) * size * 0.55;
  const text = lines.map((l, i) => `<text x="72" y="${y0 + i * size * 1.1}" font-size="${size}" font-weight="800" text-anchor="middle" fill="${app.fg}" font-family="Segoe UI, sans-serif">${esc(l)}</text>`).join('');
  const ring = state === 'error' ? '<rect x="4" y="4" width="136" height="136" rx="10" fill="none" stroke="#f87171" stroke-width="8"/>' : '';
  return url(`<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
<rect width="144" height="144" fill="${app.bg}"/>${text}${ring}
${deviceLabel ? `<text x="72" y="130" font-size="12" text-anchor="middle" fill="${app.fg}" opacity=".7" font-family="Segoe UI, sans-serif">${esc(deviceLabel)}</text>` : ''}</svg>`);
}

module.exports = { keyImage, appImage, GLYPHS };

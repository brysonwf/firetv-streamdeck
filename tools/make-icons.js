// Renders the static PNG icons Stream Deck wants (plugin, category, action list, default key images) from the same
// SVG drawings the plugin uses at runtime. Needs puppeteer: node tools/make-icons.js [path-to-puppeteer]
'use strict';
const path = require('path');
const fs = require('fs');
const puppeteer = require(process.argv[2] || 'puppeteer');
const { keyImage, appImage, GLYPHS } = require('../com.altbryson.firetv.sdPlugin/bin/icons');
const { KEYS, APPS } = require('../com.altbryson.firetv.sdPlugin/bin/catalog');

const OUT = path.join(__dirname, '..', 'com.altbryson.firetv.sdPlugin', 'imgs');
fs.mkdirSync(OUT, { recursive: true });
const svgOf = dataUrl => decodeURIComponent(dataUrl.replace(/^data:image\/svg\+xml;charset=utf8,/, ''));

// Plugin / category / action-list icon: a white remote outline on transparent (Stream Deck tints list icons).
const remote = fg => `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
<rect x="46" y="10" width="52" height="124" rx="22" fill="none" stroke="${fg}" stroke-width="8"/>
<circle cx="72" cy="50" r="16" fill="none" stroke="${fg}" stroke-width="7"/><circle cx="72" cy="50" r="5" fill="${fg}"/>
<circle cx="62" cy="88" r="5" fill="${fg}"/><circle cx="82" cy="88" r="5" fill="${fg}"/><circle cx="62" cy="108" r="5" fill="${fg}"/><circle cx="82" cy="108" r="5" fill="${fg}"/></svg>`;
const appGlyph = fg => `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
<g fill="none" stroke="${fg}" stroke-width="8"><rect x="18" y="18" width="46" height="46" rx="10"/><rect x="80" y="18" width="46" height="46" rx="10"/><rect x="18" y="80" width="46" height="46" rx="10"/></g>
<path d="M103 84 V122 M84 103 H122" stroke="${fg}" stroke-width="9" stroke-linecap="round"/></svg>`;
const pluginIcon = `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144"><rect width="144" height="144" rx="28" fill="#ff9900"/>
<g transform="translate(14 14) scale(.8)">${remote('#14161b').replace(/<\/?svg[^>]*>/g, '')}</g></svg>`;

(async () => {
  const b = await puppeteer.launch({ headless: 'new' });
  const p = await b.newPage();
  const shot = async (svg, name, size) => {
    await p.setViewport({ width: size, height: size });
    await p.setContent(`<html><body style="margin:0;background:transparent">${svg.replace('<svg ', `<svg style="display:block;width:${size}px;height:${size}px" `)}</body></html>`);
    await p.screenshot({ path: path.join(OUT, name + '.png'), omitBackground: true });
  };
  for (const [name, svg, size] of [
    ['plugin', pluginIcon, 256], ['plugin@2x', pluginIcon, 512],
    ['category', remote('#ffffff'), 28], ['category@2x', remote('#ffffff'), 56],
    ['action-key', remote('#ffffff'), 20], ['action-key@2x', remote('#ffffff'), 40],
    ['action-app', appGlyph('#ffffff'), 20], ['action-app@2x', appGlyph('#ffffff'), 40],
    ['key', svgOf(keyImage(KEYS.find(k => k.id === 'home'), '')), 72], ['key@2x', svgOf(keyImage(KEYS.find(k => k.id === 'home'), '')), 144],
    ['app', svgOf(appImage(APPS[0], APPS[0].name, '')), 72], ['app@2x', svgOf(appImage(APPS[0], APPS[0].name, '')), 144],
  ]) await shot(svg, name, size);
  // a contact sheet of every key and app image, for checking the drawings
  const all = [...KEYS.map(k => keyImage(k, '')), keyImage(KEYS[0], 'Basement'), keyImage(KEYS[4], '', 'error'), ...APPS.map(a => appImage(a, a.id === 'custom' ? 'My app' : a.name, ''))];
  await p.setViewport({ width: 6 * 150, height: Math.ceil(all.length / 6) * 150 });
  await p.setContent(`<body style="margin:0;background:#000;display:flex;flex-wrap:wrap;gap:6px">${all.map(u => `<img src="${u}" width="144" height="144" style="border-radius:16px">`).join('')}</body>`);
  await p.screenshot({ path: path.join(__dirname, 'contact-sheet.png') });
  await b.close();
  console.log('icons written to', OUT, '; glyphs:', Object.keys(GLYPHS).length);
})();

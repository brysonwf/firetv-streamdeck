// Renders the app's images with puppeteer: assets/keys/<id>.jpg (72x72, rotated 180 degrees as the Stream Deck
// Original V2 expects), res/drawable/icon.png and the Fire TV banner res/drawable/banner.png (320x180).
// Usage: node android/tools/make-keys.js <path-to-puppeteer>
'use strict';
const path = require('path');
const fs = require('fs');
const puppeteer = require(process.argv[2] || 'puppeteer');
const { keyImage, appImage } = require('../../com.altbryson.firetv.sdPlugin/bin/icons');
const { KEYS, APPS } = require('../../com.altbryson.firetv.sdPlugin/bin/catalog');

const ROOT = path.join(__dirname, '..');
const KEYDIR = path.join(ROOT, 'assets', 'keys');
const DRAW = path.join(ROOT, 'res', 'drawable');
fs.mkdirSync(KEYDIR, { recursive: true });
fs.mkdirSync(DRAW, { recursive: true });
// read the layout ids straight from Layout.java so the two never drift
const layout = [...fs.readFileSync(path.join(ROOT, 'src/com/altbryson/deckremote/Layout.java'), 'utf8').matchAll(/(?:nav|media|vol|folder)\("(\w+)"/g)].map(m => m[1]);
layout.push('remote'); // the apps page's "back to the remote" key
// keys that exist only on the deck app (not in the PC plugin's catalog)
const EXTRA = { apps: { id: 'apps', name: 'Apps', glyph: 'apps' }, remote: { id: 'remote', name: 'Remote', glyph: 'remote' } };

const remote = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 144 144"><rect width="144" height="144" rx="28" fill="#ff9900"/>
<g transform="translate(14 14) scale(.8)" fill="none" stroke="#14161b" stroke-width="8"><rect x="46" y="10" width="52" height="124" rx="22"/><circle cx="72" cy="50" r="16" stroke-width="7"/></g>
<g fill="#14161b" transform="translate(14 14) scale(.8)"><circle cx="72" cy="50" r="5"/><circle cx="62" cy="88" r="5"/><circle cx="82" cy="88" r="5"/><circle cx="62" cy="108" r="5"/><circle cx="82" cy="108" r="5"/></g></svg>`;
const banner = `<div style="width:320px;height:180px;background:#14161b;display:flex;align-items:center;gap:18px;padding:0 22px;box-sizing:border-box;font-family:Segoe UI,sans-serif">
<div style="width:96px;height:96px;flex:none">${remote}</div><div style="color:#fff;font-weight:800;font-size:30px;line-height:1.05">Stream Deck<br><span style="color:#ff9900">Remote</span></div></div>`;

(async () => {
  const b = await puppeteer.launch({ headless: 'new' });
  const p = await b.newPage();
  for (const id of layout) {
    const k = EXTRA[id] || KEYS.find(x => x.id === id), a = APPS.find(x => x.id === id);
    const url = k ? keyImage(k, '') : appImage(a, a.name, '');
    await p.setViewport({ width: 72, height: 72 });
    await p.setContent(`<body style="margin:0;background:#000"><img src="${url}" width="72" height="72" style="display:block;transform:rotate(180deg)"></body>`);
    await p.screenshot({ path: path.join(KEYDIR, id + '.jpg'), type: 'jpeg', quality: 92 });
  }
  await p.setViewport({ width: 192, height: 192 });
  await p.setContent(`<body style="margin:0;background:transparent"><div style="width:192px;height:192px">${remote}</div></body>`);
  await p.screenshot({ path: path.join(DRAW, 'icon.png'), omitBackground: true });
  await p.setViewport({ width: 320, height: 180 });
  await p.setContent(`<body style="margin:0">${banner}</body>`);
  await p.screenshot({ path: path.join(DRAW, 'banner.png') });
  // preview of the deck as laid out (un-rotated) for checking
  await p.setViewport({ width: 5 * 80 + 10, height: 3 * 80 + 10 });
  await p.setContent(`<body style="margin:0;padding:5px;background:#222;display:grid;grid-template-columns:repeat(5,72px);gap:8px">${layout.map(id => `<img src="file:///${path.join(KEYDIR, id + '.jpg').replace(/\\/g, '/')}" style="transform:rotate(180deg);border-radius:10px">`).join('')}</body>`);
  await new Promise(r => setTimeout(r, 300));
  await p.screenshot({ path: path.join(__dirname, 'deck-preview.png') });
  await b.close();
  console.log('keys:', layout.join(' '));
})();

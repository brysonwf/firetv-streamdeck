// Fire TV Remote for Stream Deck. Stream Deck starts this with -port -pluginUUID -registerEvent -info and talks to it
// over a local WebSocket (Stream Deck SDK v2 protocol).
'use strict';
const path = require('path');
const fs = require('fs');
const WebSocket = require('ws');
const { DEVICES, KEYS, APPS } = require('./catalog');
const { keyImage, appImage } = require('./icons');
const adb = require('./adb');

const LOG = path.join(__dirname, '..', 'logs', 'plugin.log');
try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); } catch (e) { /* read-only */ }
const log = msg => { try { fs.appendFileSync(LOG, `${new Date().toISOString()} ${msg}\n`); } catch (e) { /* ignore */ } };

const arg = name => { const i = process.argv.indexOf('-' + name); return i >= 0 ? process.argv[i + 1] : null; };
const port = arg('port'), uuid = arg('pluginUUID'), registerEvent = arg('registerEvent');
if (!port) { console.error('Start this from Stream Deck.'); process.exit(1); }

const ws = new WebSocket('ws://127.0.0.1:' + port);
const send = (event, context, payload) => ws.readyState === 1 && ws.send(JSON.stringify(payload === undefined ? { event, context } : { event, context, payload }));
ws.on('open', () => { ws.send(JSON.stringify({ event: registerEvent, uuid })); log(`registered; adb = ${adb.ADB}`); });
ws.on('close', () => { adb.closeAll(); process.exit(0); });
ws.on('error', e => log('ws error ' + e.message));

const contexts = new Map(); // context -> { action, settings }
const repeats = new Map();  // context -> timer

const hostOf = s => (s.device === 'custom' ? (s.host || '').trim() : (DEVICES.find(d => d.id === (s.device || 'living')) || DEVICES[0]).host);
const deviceLabel = s => (s.device === 'custom' ? (s.host || 'set IP') : (s.device === 'basement' ? 'Basement' : (s.showDevice ? 'Living room' : '')));

function draw(context, state) {
  const c = contexts.get(context); if (!c) return;
  const s = c.settings;
  const label = s.device === 'basement' || s.device === 'custom' || s.showDevice ? deviceLabel(s) : '';
  let image;
  if (c.action.endsWith('.key')) image = keyImage(KEYS.find(k => k.id === s.key) || KEYS.find(k => k.id === 'home'), label, state);
  else {
    const app = APPS.find(a => a.id === s.app) || APPS[0];
    const name = app.id === 'custom' ? (s.label || s.pkg || 'App') : app.name;
    image = appImage(app, name, label, state);
  }
  send('setImage', context, { image, target: 0 });
}

async function fire(context) {
  const c = contexts.get(context); if (!c) return;
  const s = c.settings, host = hostOf(s);
  if (!host) throw new Error('no Fire TV address set');
  let line;
  if (c.action.endsWith('.key')) line = 'input keyevent ' + (KEYS.find(k => k.id === s.key) || KEYS.find(k => k.id === 'home')).code;
  else {
    const app = APPS.find(a => a.id === s.app) || APPS[0];
    line = adb.launchLine(app.id === 'custom' ? (s.pkg || '').trim() : app.pkg);
  }
  await adb.session(host, log).send(line);
}

function press(context) {
  fire(context).catch(e => {
    log('press failed: ' + e.message);
    send('showAlert', context);
    draw(context, 'error'); setTimeout(() => draw(context), 1500);
  });
}

ws.on('message', raw => {
  let m; try { m = JSON.parse(raw); } catch (e) { return; }
  const { event, context, action, payload } = m;
  switch (event) {
    case 'willAppear':
    case 'didReceiveSettings': {
      const settings = Object.assign(action.endsWith('.key') ? { key: 'home', device: 'living' } : { app: 'peacock', device: 'living' }, (payload && payload.settings) || {});
      contexts.set(context, { action, settings });
      draw(context);
      // warm the connection so the first press is fast
      const h = hostOf(settings); if (h) adb.session(h, log).open().catch(e => log('warm-up: ' + e.message));
      break;
    }
    case 'willDisappear': contexts.delete(context); clearInterval(repeats.get(context)); clearTimeout(repeats.get(context)); repeats.delete(context); break;
    case 'keyDown': {
      press(context);
      const c = contexts.get(context);
      const key = c && c.action.endsWith('.key') && KEYS.find(k => k.id === c.settings.key);
      if (key && key.repeat) repeats.set(context, setTimeout(() => repeats.set(context, setInterval(() => press(context), 140)), 420));
      break;
    }
    case 'keyUp': clearTimeout(repeats.get(context)); clearInterval(repeats.get(context)); repeats.delete(context); break;
    case 'sendToPlugin':
      if (payload && payload.test) press(context);
      break;
  }
});

process.on('uncaughtException', e => log('uncaught ' + (e && e.stack || e)));

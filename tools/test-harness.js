// Pretends to be Stream Deck: starts the plugin like Stream Deck does, shows it keys, presses them, and checks the
// Fire TV reacted. Usage: node tools/test-harness.js [host]   (default: the living-room Cube)
// It WAKES the TV (Home), opens Peacock, then puts it back to sleep.
'use strict';
const path = require('path');
const { spawn, execFileSync } = require('child_process');
const WebSocket = require('../com.altbryson.firetv.sdPlugin/node_modules/ws');
const { ADB } = require('../com.altbryson.firetv.sdPlugin/bin/adb');

const host = process.argv[2] || '192.168.1.50';
const serial = host + ':5555';
const tv = cmd => execFileSync(ADB, ['-s', serial, 'shell', cmd], { encoding: 'utf8', timeout: 10000 });
const wakeState = () => (tv('dumpsys power').match(/mWakefulness=(\w+)/) || [])[1];
const focus = () => (tv('dumpsys window').match(/mCurrentFocus=Window\{\S+ \S+ ([^}]+)\}/) || [])[1];
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const wss = new WebSocket.Server({ port: 0, host: '127.0.0.1' });
  await new Promise(r => wss.on('listening', r));
  const port = wss.address().port;
  const got = [];
  let plugin;
  const ready = new Promise(res => wss.on('connection', s => { plugin = s; s.on('message', m => { const j = JSON.parse(m); got.push(j); if (j.event === 'registerPlugin') res(); }); }));
  const child = spawn(process.execPath, [path.join(__dirname, '..', 'com.altbryson.firetv.sdPlugin', 'bin', 'plugin.js'), '-port', String(port), '-pluginUUID', 'TESTUUID', '-registerEvent', 'registerPlugin', '-info', '{}'], { stdio: 'inherit' });
  await ready;
  const say = o => plugin.send(JSON.stringify(o));
  const key = (ctx, action, settings) => say({ event: 'willAppear', action, context: ctx, device: 'D', payload: { settings, coordinates: { column: 0, row: 0 } } });
  const press = async ctx => { say({ event: 'keyDown', context: ctx, action: '', payload: {} }); await sleep(80); say({ event: 'keyUp', context: ctx, action: '', payload: {} }); };

  key('home', 'com.altbryson.firetv.key', { key: 'home', device: 'living' });
  key('peacock', 'com.altbryson.firetv.app', { app: 'peacock', device: 'living' });
  key('sleep', 'com.altbryson.firetv.key', { key: 'sleep', device: 'living' });
  await sleep(2500);
  const images = got.filter(g => g.event === 'setImage').length;
  console.log('registered; key images drawn:', images, '; TV before:', wakeState());

  let t = Date.now(); await press('home'); await sleep(2500);
  console.log(`Home -> TV ${wakeState()}, focus ${focus()} (press sent ${Date.now() - t - 2500 + 80} ms)`);
  await press('peacock'); await sleep(5000);
  console.log('Peacock ->', focus());
  await press('home'); await sleep(1500);
  await press('sleep'); await sleep(2500);
  console.log('Sleep -> TV', wakeState());
  const alerts = got.filter(g => g.event === 'showAlert').length;
  console.log('alerts:', alerts);
  plugin.close(); wss.close();
  setTimeout(() => { try { child.kill(); } catch (e) { /* done */ } process.exit(0); }, 500);
})();

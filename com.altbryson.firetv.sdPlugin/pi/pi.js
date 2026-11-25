// Settings screen (property inspector) shared by both actions. Stream Deck calls connectElgatoStreamDeckSocket.
/* global FIRETV */
let socket, piUUID, settings = {};

function save() { socket && socket.send(JSON.stringify({ event: 'setSettings', context: piUUID, payload: settings })); }
function fill(select, items, value) {
  select.innerHTML = items.map(i => `<option value="${i.id}">${i.name}</option>`).join('');
  select.value = value;
}
function bind() {
  const kind = document.body.dataset.kind; // key | app
  const dev = document.getElementById('device');
  fill(dev, [...FIRETV.DEVICES, { id: 'custom', name: 'Other Fire TV (type its IP)' }], settings.device || 'living');
  const host = document.getElementById('host');
  host.value = settings.host || '';
  const sync = () => {
    document.getElementById('host-row').hidden = dev.value !== 'custom';
    if (kind === 'app') document.getElementById('pkg-rows').hidden = document.getElementById('app').value !== 'custom';
  };
  dev.onchange = () => { settings.device = dev.value; sync(); save(); };
  host.oninput = () => { settings.host = host.value.trim(); save(); };
  if (kind === 'key') {
    const key = document.getElementById('key');
    fill(key, FIRETV.KEYS, settings.key || 'home');
    key.onchange = () => { settings.key = key.value; save(); };
  } else {
    const app = document.getElementById('app'), pkg = document.getElementById('pkg'), label = document.getElementById('label');
    fill(app, FIRETV.APPS, settings.app || 'peacock');
    pkg.value = settings.pkg || ''; label.value = settings.label || '';
    app.onchange = () => { settings.app = app.value; sync(); save(); };
    pkg.oninput = () => { settings.pkg = pkg.value.trim(); save(); };
    label.oninput = () => { settings.label = label.value; save(); };
  }
  document.getElementById('test').onclick = () => socket.send(JSON.stringify({ event: 'sendToPlugin', action: document.body.dataset.action, context: piUUID, payload: { test: true } }));
  sync();
}

// eslint-disable-next-line no-unused-vars
function connectElgatoStreamDeckSocket(port, uuid, registerEvent, info, actionInfo) {
  piUUID = uuid;
  try { settings = JSON.parse(actionInfo).payload.settings || {}; } catch (e) { settings = {}; }
  try { document.body.dataset.action = JSON.parse(actionInfo).action; } catch (e) { /* ignore */ }
  socket = new WebSocket('ws://127.0.0.1:' + port);
  socket.onopen = () => socket.send(JSON.stringify({ event: registerEvent, uuid }));
  bind();
}

// ADB to Fire TVs on the home network. One long-lived `adb shell` per Fire TV so a key press is just a line written
// to its stdin (near-instant); the shell is reopened (with `adb connect`) whenever it has closed.
'use strict';
const { spawn, execFile } = require('child_process');
const fs = require('fs');
const path = require('path');

function findAdb() {
  if (process.env.FIRETV_ADB && fs.existsSync(process.env.FIRETV_ADB)) return process.env.FIRETV_ADB;
  const winget = path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'WinGet', 'Packages');
  try {
    for (const d of fs.readdirSync(winget)) {
      if (!/^Google\.PlatformTools/i.test(d)) continue;
      const p = path.join(winget, d, 'platform-tools', 'adb.exe');
      if (fs.existsSync(p)) return p;
    }
  } catch (e) { /* no WinGet folder */ }
  for (const p of [path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk', 'platform-tools', 'adb.exe'), 'C:\\platform-tools\\adb.exe']) if (fs.existsSync(p)) return p;
  return 'adb'; // on PATH
}

const ADB = findAdb();
const run = (args, timeout = 8000) => new Promise(res => execFile(ADB, args, { timeout, windowsHide: true }, (err, out, errOut) => res({ err, out: String(out || '') + String(errOut || '') })));

class Session {
  constructor(host, log) { this.host = host; this.serial = host.includes(':') ? host : host + ':5555'; this.log = log; this.proc = null; this.opening = null; }

  async open() {
    if (this.proc) return this.proc;
    if (this.opening) return this.opening;
    this.opening = (async () => {
      const c = await run(['connect', this.serial]);
      if (!/connected/.test(c.out)) throw new Error(`can't reach ${this.serial}: ${c.out.trim() || c.err}`);
      if (/unauthorized/.test((await run(['-s', this.serial, 'get-state'])).out)) throw new Error(`${this.serial}: allow USB debugging on the TV`);
      const p = spawn(ADB, ['-s', this.serial, 'shell'], { windowsHide: true, stdio: ['pipe', 'ignore', 'pipe'] });
      p.on('exit', () => { if (this.proc === p) this.proc = null; });
      p.on('error', e => { this.log('adb shell error ' + e.message); if (this.proc === p) this.proc = null; });
      p.stderr.on('data', d => this.log(`[${this.serial}] ${String(d).trim()}`));
      this.proc = p;
      return p;
    })();
    try { return await this.opening; } finally { this.opening = null; }
  }

  async send(line) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const p = await this.open();
        await new Promise((res, rej) => p.stdin.write(line + '\n', e => e ? rej(e) : res()));
        return;
      } catch (e) {
        if (this.proc) { try { this.proc.kill(); } catch (k) { /* gone */ } this.proc = null; }
        if (attempt) throw e;
      }
    }
  }

  close() { if (this.proc) { try { this.proc.stdin.end('exit\n'); this.proc.kill(); } catch (e) { /* gone */ } this.proc = null; } }
}

const sessions = new Map();
function session(host, log) { if (!sessions.has(host)) sessions.set(host, new Session(host, log)); return sessions.get(host); }
function closeAll() { for (const s of sessions.values()) s.close(); }

// Open an app by package name: its TV launcher activity first, then the phone launcher, then monkey as a last resort.
const launchLine = pkg => {
  if (!/^[A-Za-z0-9_.]+$/.test(pkg)) throw new Error('bad package name');
  return `c=$(cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER ${pkg} | tail -1); case "$c" in */*) ;; *) c=$(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER ${pkg} | tail -1);; esac; case "$c" in */*) am start -n "$c" >/dev/null 2>&1;; *) monkey -p ${pkg} 1 >/dev/null 2>&1;; esac`;
};

module.exports = { ADB, session, closeAll, launchLine, run };

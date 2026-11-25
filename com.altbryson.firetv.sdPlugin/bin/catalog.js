// What the keys can do. Shared by the plugin (bin/plugin.js) and the settings screens (pi/*.html load it too).
(function (root) {
  const DEVICES = [
    { id: 'living', name: 'Living room', host: '192.168.1.50' },
    { id: 'basement', name: 'Basement', host: '192.168.1.51' },
  ];

  // Android key codes. `repeat`: keeps firing while the Stream Deck key is held.
  const KEYS = [
    { id: 'up', name: 'Up', code: 19, glyph: 'up', repeat: true },
    { id: 'down', name: 'Down', code: 20, glyph: 'down', repeat: true },
    { id: 'left', name: 'Left', code: 21, glyph: 'left', repeat: true },
    { id: 'right', name: 'Right', code: 22, glyph: 'right', repeat: true },
    { id: 'select', name: 'Select (OK)', code: 23, glyph: 'select' },
    { id: 'back', name: 'Back', code: 4, glyph: 'back' },
    { id: 'home', name: 'Home', code: 3, glyph: 'home' },
    { id: 'menu', name: 'Menu (options)', code: 82, glyph: 'menu' },
    { id: 'playpause', name: 'Play / Pause', code: 85, glyph: 'playpause' },
    { id: 'rewind', name: 'Rewind', code: 89, glyph: 'rewind', repeat: true },
    { id: 'ff', name: 'Fast forward', code: 90, glyph: 'ff', repeat: true },
    { id: 'volup', name: 'Volume up', code: 24, glyph: 'volup', repeat: true },
    { id: 'voldown', name: 'Volume down', code: 25, glyph: 'voldown', repeat: true },
    { id: 'mute', name: 'Mute', code: 164, glyph: 'mute' },
    { id: 'guide', name: 'Live TV guide', code: 172, glyph: 'guide' },
    { id: 'search', name: 'Search', code: 84, glyph: 'search' },
    { id: 'sleep', name: 'Sleep (TV off)', code: 223, glyph: 'sleep' },
    { id: 'wake', name: 'Wake (TV on)', code: 224, glyph: 'wake' },
  ];

  // Package names read from the living-room Cube (pm list packages, 2026-11-21).
  const APPS = [
    { id: 'peacock', name: 'Peacock', pkg: 'com.peacock.peacockfiretv', bg: '#000000', fg: '#ffffff' },
    { id: 'paramount', name: 'Paramount+', pkg: 'com.cbs.ott', bg: '#0064ff', fg: '#ffffff' },
    { id: 'prime', name: 'Prime Video', pkg: 'com.amazon.firebat', bg: '#00a8e1', fg: '#ffffff' },
    { id: 'youtubetv', name: 'YouTube TV', pkg: 'com.amazon.firetv.youtube.tv', bg: '#ffffff', fg: '#ff0000' },
    { id: 'youtube', name: 'YouTube', pkg: 'com.amazon.firetv.youtube', bg: '#ff0000', fg: '#ffffff' },
    { id: 'netflix', name: 'Netflix', pkg: 'com.netflix.ninja', bg: '#000000', fg: '#e50914' },
    { id: 'disney', name: 'Disney+', pkg: 'com.disney.disneyplus', bg: '#0b1d4f', fg: '#ffffff' },
    { id: 'max', name: 'Max', pkg: 'com.hbo.hbonow', bg: '#002be7', fg: '#ffffff' },
    { id: 'hulu', name: 'Hulu', pkg: 'com.hulu.plus', bg: '#1ce783', fg: '#0b0c0f' },
    { id: 'espn', name: 'ESPN', pkg: 'com.espn.gtv', bg: '#cc0000', fg: '#ffffff' },
    { id: 'foxsports', name: 'FOX Sports', pkg: 'com.foxsports.videogo', bg: '#002244', fg: '#ffffff' },
    { id: 'jellyfin', name: 'Jellyfin', pkg: 'org.jellyfin.androidtv', bg: '#101322', fg: '#aa5cc3' },
    { id: 'kodi', name: 'Kodi', pkg: 'org.xbmc.kodi', bg: '#17b2e7', fg: '#ffffff' },
    { id: 'pluto', name: 'Pluto TV', pkg: 'tv.pluto.android', bg: '#000000', fg: '#fff200' },
    { id: 'tubi', name: 'Tubi', pkg: 'com.tubitv.ott', bg: '#7408ff', fg: '#ffff13' },
    { id: 'philo', name: 'Philo', pkg: 'com.philo.philo', bg: '#000000', fg: '#ffffff' },
    { id: 'iamlocal', name: 'iamlocal', pkg: 'online.iamlocal.tv', bg: '#1f2937', fg: '#ffffff' },
    { id: 'custom', name: 'Other (type a package name)', pkg: '', bg: '#333842', fg: '#ffffff' },
  ];

  const api = { DEVICES, KEYS, APPS };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.FIRETV = api;
})(this);

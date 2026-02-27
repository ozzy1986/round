/**
 * Creates one NEW training (profile) on VPS with 6 rounds: Russian names,
 * varied duration and warn10. Does not remove or replace existing trainings.
 * Run: node scripts/create-training-ru.cjs
 */
const http = require('http');

const HOST = 'round.ozzy1986.com';
const PORT = 8080;

const profileBody = JSON.stringify({
  name: 'Тест AI: 6 раундов',
  emoji: '🏋️'
});

const rounds = [
  { name: 'Разминка', duration_seconds: 30, warn10sec: true, position: 0 },
  { name: 'Спринт', duration_seconds: 10, warn10sec: false, position: 1 },
  { name: 'Отдых', duration_seconds: 15, warn10sec: true, position: 2 },
  { name: 'Силовая', duration_seconds: 45, warn10sec: true, position: 3 },
  { name: 'Ходьба', duration_seconds: 20, warn10sec: false, position: 4 },
  { name: 'Заминка', duration_seconds: 25, warn10sec: true, position: 5 }
];

function request(options, body) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { hostname: HOST, port: PORT, ...options },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => {
          if (res.statusCode >= 400) reject(new Error(`${res.statusCode}: ${data}`));
          else resolve(data ? JSON.parse(data) : null);
        });
      }
    );
    req.on('error', reject);
    if (body) {
      req.setHeader('Content-Type', 'application/json');
      req.setHeader('Content-Length', Buffer.byteLength(body));
      req.write(body);
    }
    req.end();
  });
}

(async () => {
  try {
    const profile = await request(
      { path: '/profiles', method: 'POST' },
      profileBody
    );
    if (!profile?.id) {
      console.error('Failed to create profile:', profile);
      process.exit(1);
    }
    console.log('Profile created:', profile.id, profile.name);

    for (const r of rounds) {
      await request(
        { path: `/profiles/${profile.id}/rounds`, method: 'POST' },
        JSON.stringify(r)
      );
      console.log('  Round:', r.name, r.duration_seconds + 's', r.warn10sec ? 'warn10' : '');
    }
    console.log('Done. Sync in app to see the new training (your existing ones are unchanged).');
  } catch (e) {
    console.error(e.message || e);
    process.exit(1);
  }
})();

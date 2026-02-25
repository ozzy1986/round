const { Client } = require('pg');

const client = new Client({
  host: '127.0.0.1',
  port: 5432,
  user: 'postgres',
  password: '',
  database: 'postgres',
});

async function main() {
  try {
    await client.connect();
    const r = await client.query('SELECT current_database(), current_user');
    console.log('PostgreSQL OK:', r.rows[0]);
    const dbName = 'raund';
    const exists = await client.query(
      "SELECT 1 FROM pg_database WHERE datname = $1",
      [dbName]
    );
    if (exists.rows.length === 0) {
      await client.query(`CREATE DATABASE ${dbName}`);
      console.log('Database created:', dbName);
    } else {
      console.log('Database already exists:', dbName);
    }
  } catch (e) {
    console.error('PostgreSQL error:', e.message);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();

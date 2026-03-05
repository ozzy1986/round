import type { FastifyInstance } from 'fastify';

const PRIVACY_HTML = `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Round — Privacy Policy / Политика конфиденциальности</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 640px; margin: 0 auto; padding: 1rem; line-height: 1.5; color: #333; }
    h1 { font-size: 1.5rem; }
    h2 { font-size: 1.1rem; margin-top: 1.5rem; }
    p { margin: 0.5rem 0; }
    a { color: #7B1FA2; }
    .lang { margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid #ddd; }
  </style>
</head>
<body>
  <h1>Round — Политика конфиденциальности</h1>
  <p>Приложение Round («Раунд») — интервальный таймер для тренировок.</p>
  <h2>Какие данные мы обрабатываем</h2>
  <p>При первом использовании синхронизации приложение создаёт анонимный идентификатор пользователя (UUID). На сервер передаются только ваши тренировки (названия, эмодзи, раунды с длительностями и настройками). Эти данные нужны для синхронизации между вашими устройствами.</p>
  <h2>Чего мы не собираем</h2>
  <p>Мы не собираем имя, адрес электронной почты, номер телефона, геолокацию, рекламные идентификаторы и не ведём аналитику поведения.</p>
  <h2>Цель обработки</h2>
  <p>Синхронизация ваших тренировок между устройствами при включённой синхронизации.</p>
  <h2>Хранение</h2>
  <p>Данные хранятся на сервере round.ozzy1986.com в базе данных PostgreSQL. Мы не передаём их третьим лицам.</p>
  <h2>Удаление данных</h2>
  <p>При удалении приложения локальные данные удаляются с устройства. Анонимный аккаунт на сервере остаётся (привязки к личности нет). По запросу можно удалить данные, связанные с вашим аккаунтом — напишите на контакт ниже.</p>
  <h2>Контакт</h2>
  <p>Вопросы по политике конфиденциальности: через репозиторий проекта на GitHub (см. информацию о приложении) или по обращению на домен round.ozzy1986.com.</p>

  <div class="lang">
    <h1>Round — Privacy Policy</h1>
    <p>Round is an interval timer app for workouts.</p>
    <h2>Data we process</h2>
    <p>When you first use sync, the app creates an anonymous user identifier (UUID). Only your trainings (names, emoji, rounds with durations and settings) are sent to the server for syncing across your devices.</p>
    <h2>What we do not collect</h2>
    <p>We do not collect name, email, phone number, location, advertising identifiers, or behavioural analytics.</p>
    <h2>Purpose</h2>
    <p>To sync your trainings across devices when sync is enabled.</p>
    <h2>Storage</h2>
    <p>Data is stored on the server round.ozzy1986.com in a PostgreSQL database. We do not share it with third parties.</p>
    <h2>Data deletion</h2>
    <p>When you uninstall the app, local data is removed from the device. The anonymous server account remains (not linked to identity). You can request deletion of data tied to your account — use the contact below.</p>
    <h2>Contact</h2>
    <p>Privacy-related questions: via the project repository on GitHub or via the domain round.ozzy1986.com.</p>
  </div>
</body>
</html>
`;

export async function privacyRoutes(app: FastifyInstance): Promise<void> {
  app.get('/privacy', async (_request, reply) => {
    return reply
      .type('text/html; charset=utf-8')
      .send(PRIVACY_HTML);
  });
}

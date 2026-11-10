# Ревью кода MPK — 2026-11-10

Волна ревью: 3 параллельных агента (данные/сеть/БД, Compose/перфоманс, безопасность/фон).
Ветка `v2.4-stable`. Статус: находки разобраны, критичное исправлено.

## Что исправлено в этой волне

### Критично (перфоманс / корректность)
1. **CollegeMapScreen pinch-zoom фризы** — `unit = 1f/scale.value` читался в композиции
   → весь `MapWallsLayer` (1490 стен) рекомпозировался на каждый кадр зума.
   Фикс: `unit: () -> Float`, чтение внутри Canvas (фаза отрисовки).
2. **ScheduleScreen:827 focusAlpha** — `highlightAlpha.value` в композиции → рекомпозиция
   всей карточки урока на каждый кадр подсветки. Фикс: `focusAlpha: () -> Float` по всей цепочке.
3. **MpkNetworkClient allLessons** — список копился между retry-попытками → дубли уроков.
   Фикс: перенесён внутрь цикла попыток.
4. **MpkNetworkClient isSuccess** — «успех» при HTTP 200 без уроков («Успешно: 0»).
   Фикс: `isSuccess = distinctLessons.isNotEmpty()`.
5. **TaskRepository.deleteTask** — два delete не в транзакции → сиротские напоминания.
   Фикс: `db.withTransaction { deleteTaskById + deleteForTask }`.
6. **ContentPackWorker http://** — контент-пакет мог идти по открытому http.
   Фикс: принудительный подъём до https.

## Проверено и опровергнуто (не баги)
- **Room миграции** — агент сообщил «v11 без тестов»; на деле миграции V1→V11 покрыты
  тестами `MigrationV1ToV2Test…MigrationV10ToV11Test`. Инвариант соблюдён.
- **Backup-утечка ФИО** — агент сообщил утечку ФИО преподавателя через `mpk_widget_prefs`;
  на деле там только код группы, ФИО в `mpk_ui_prefs` (исключён из backup include-only).
  Backup-правила минимальны (include-only) — корректны.

## Оставлено осознанно (не трогаем без необходимости)
- **Частичный документ ≥50% пишется в кэш** — осознанный компромисс отзывчивости
  (см. комментарий ScheduleRepository.kt:42-46). Порог 50% для записи, 85% для вердикта
  «группы нет». Ломать = регресс UX.
- **exported компоненты** (MainActivity, FileViewAlias) — осознанный приём share/view
  интентов с whitelist форматов (ExternalMaterialIntake). Не уязвимость, а фича.
- Один статический OkHttpClient, USER_AGENT=Chrome (обход защиты сайта МГПК) — по ТЗ.

## Техдолг (низкий приоритет)
- Аллокации в drawMap вынесены в `RoomNumberScaleCache` (remember) — сделано.
- Двойная подписка Flow в ScheduleScreen (groupLessonsFromVm vs прямой collectAsState) — дубль Room-подписки, не критично.
- Regex с вложенными квантификаторами в MpkScheduleParser — потенциальный бэктрекинг, но вызовы на IO.

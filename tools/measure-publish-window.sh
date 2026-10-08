#!/bin/sh
# Долг чести №9 (SPEC §4.2): промер фактического времени публикации документов
# расписания на guo-mpk.by. Запускать ВЕЧЕРОМ (18:00-23:00) 5-7 дней подряд:
#   sh tools/measure-publish-window.sh >> _reports/publish-window.log
# Фиксирует: время проверки, наличие дока преподавателей/учеников на завтра,
# время последнего изменения (Last-Modified).
NEXT_DATE=$(date -d "+1 day" +%d.%m.%Y 2>/dev/null || date -v+1d +%d.%m.%Y)
MONTH=$(date -d "+1 day" +%Y/%m 2>/dev/null || date -v+1d +%Y/%m)
for slug in raspisanie-prepodavatelej raspisanie-uchashhihsya; do
  for suffix in "" "-1" "-2"; do
    URL="https://guo-mpk.by/wp-content/uploads/$MONTH/$NEXT_DATE-$slug$suffix.doc"
    R=$(curl -s -I --max-time 10 -A "Mozilla/5.0" "$URL" | head -1 | awk '{print $2}')
    LM=$(curl -s -I --max-time 10 -A "Mozilla/5.0" "$URL" | grep -i last-modified | cut -d' ' -f2-)
    if [ "$R" = "200" ]; then
      echo "$(date '+%Y-%m-%d %H:%M:%S') | $NEXT_DATE $slug$suffix | 200 | Last-Modified: $LM"
    fi
  done
done
echo "$(date '+%Y-%m-%d %H:%M:%S') | --- end probe ---"

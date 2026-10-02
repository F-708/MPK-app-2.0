@echo off
rem W4c: редактор карты открывать ТОЛЬКО в современном браузере (Яндекс/Vivaldi).
rem Internet Explorer не поддерживает ES6 — страница будет "не отвечать".
start "" "C:\Program Files\Yandex\YandexBrowser\Application\browser.exe" "%~dp0map-editor.html"

# ZvykMusic — Apple Glass Flutter app

Это отдельный Flutter-интерфейс, не WebView. Каталог берётся с сайта ZvykMusic:
в `index.php` сайта уже есть `window.ZVYK_TRACKS`, `title`, `artist`, `cover` и `audio`.
Приложение запрашивает HTML главной страницы, извлекает именно этот каталог и играет
аудио-URL через `just_audio`. Фоновое воспроизведение и системные медиа-контролы
подключены через `just_audio_background`.

## Важно перед запуском

1. Открой `lib/config.dart`.
2. Установи `siteBaseUrl` в точный публичный HTTPS-адрес своего сайта (без `/` в конце).
   Сейчас стоит `https://web.zvykmusic.ru` как предварительное значение.
3. На сервере главная страница должна быть доступна без авторизации и отдавать
   `window.ZVYK_TRACKS` в HTML. Аудиофайлы должны быть доступны приложению по HTTPS.
4. Эта загрузка каталога не заменяет полноценный API. В присланном ZIP были только
   `index.php`, `assets/app.js` и `assets/style.css`; `app/bootstrap.php` и
   `api/music.php` не были включены. Поэтому авторизация, изменение избранного,
   плейлисты и история пока не подключены к нативным экранам. Для них нужен полный
   backend/API или добавление документированного endpoint каталога.

## Сборка

Установи Flutter SDK и Android SDK, затем:

```bash
flutter pub get
flutter create . --platforms=android
flutter run
```

Для APK:

```bash
flutter build apk --release
```

Если `flutter create .` перезапишет Android-файлы, проверь permissions и сервисы
медиа в `android/app/src/main/AndroidManifest.xml`.

## Что реализовано в этой версии

- Нативные Flutter-экраны: главная, поиск, медиатека и now-playing.
- Apple-inspired glass panels, ambient glow, градиенты и animated waveform.
- Каталог и ссылки на треки/обложки из сайта, а не hard-coded demo tracks.
- Поиск по названию, артисту и альбому.
- Очередь, play/pause, next/previous, seek bar.
- Поддержка фонового аудио и системных медиа-уведомлений.

Это исходный проект, а не собранный/проверенный APK: в текущей среде Flutter SDK не запускался.

## Автоматическая сборка APK через GitHub Actions

В проект добавлен workflow `.github/workflows/build.yml`. Чтобы получить APK:

1. Создай новый репозиторий на GitHub (например, `zvykmusic-android`).
2. Распакуй этот архив и загрузи **содержимое** папки проекта в корень репозитория, затем сделай commit в ветку `main`.
3. Открой вкладку **Actions** в репозитории. Разреши GitHub Actions, если GitHub попросит.
4. Выбери workflow **Build ZvykMusic Android APK** и нажми **Run workflow**. Также сборка автоматически запускается при push в `main` или `master`.
5. Когда запуск завершится успешно, открой конкретный запуск workflow, найди раздел **Artifacts** и скачай `zvykmusic-android-apk`. Внутри будет `app-release.apk`.

Если сборка завершится ошибкой, открой run в Actions и пришли лог ошибки — по нему можно исправить проект. Этот workflow создаёт недостающие стандартные Android-файлы при сборке; он не заменяет проверку API/адреса сайта и реальное тестирование приложения на устройстве.

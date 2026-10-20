# Zvykmusic Android

Android app shell for https://web.zvykmusic.ru/ with a native app frame, configurable accent/theme, native bottom navigation and GitHub Actions APK builds.

## Build APK with GitHub Actions
1. Create a new GitHub repository and upload the contents of this folder.
2. Open **Actions** and enable workflows if GitHub asks.
3. Run **Build Zvykmusic APK** using **Run workflow**, or push to `main`.
4. Download `zvykmusic-debug-apk` from the successful workflow run's **Artifacts** section.
5. Unzip the artifact and install `app-debug.apk` on Android. Allow installation from your browser/files app if prompted.

## Site integration
The app loads the live site at `https://web.zvykmusic.ru/` in a WebView, so the existing catalog/account/site features remain served by your site. The bottom tabs load configurable URLs in `app/src/main/java/ru/zvykmusic/app/SiteConfig.kt`. Adjust those paths to match real routes on your site if needed.

## Customize
- App name: `app/src/main/res/values/strings.xml`
- Colors: `app/src/main/res/values/colors.xml`
- Tab destinations: `SiteConfig.kt`
- Native settings: tap the palette icon in the top bar; accent choice is saved on device.
- Package ID: `ru.zvykmusic.app` in Gradle and `MainActivity.kt`.

## Important
This is a debug APK workflow (not a Play Store release). For Play Store distribution, create a signed release with a keystore stored in GitHub Secrets. Never commit signing keys or passwords. This project has not been tested against private APIs; it only opens the public HTTPS site in an Android WebView. Android playback/background behavior depends on the site's player and browser-compatible media implementation.


## Исправления стабильности (версия проекта после белого экрана)

- WebView создаётся после установки основного layout.
- При сетевой/серверной ошибке показывается экран с кнопкой повтора вместо пустого экрана.
- Обрабатывается завершение процесса рендеринга WebView (`onRenderProcessGone`), чтобы оно не закрывало Activity.
- Ошибки загрузки и HTTP 5xx записываются в Logcat с тегом `Zvykmusic`.
- Внешние ссылки обрабатываются безопаснее: приложение не падает, если нет подходящего приложения для ссылки.

Это защитные исправления, а не подтверждение причины конкретного сбоя: для точного диагноза нужен отчёт Logcat или Play Console. Сборку следует проверить на реальном устройстве перед публикацией.


## Версия 1.1.0 — исправление конфигурации сборки

- Добавлен Kotlin Android Gradle Plugin: исходный `MainActivity.kt` был Kotlin, но плагин Kotlin в конфигурации сборки отсутствовал. Это могло приводить к ошибке сборки и означает, что прежнюю сборку нельзя считать подтверждённой.
- Обновлены Android Gradle Plugin и Gradle.
- `compileSdk` и `targetSdk` выставлены на API 36 с учётом требований Google Play, действующих с 31 августа 2026 года для новых приложений и обновлений.
- GitHub Actions собирает тестовый APK и AAB для релиза. AAB в этом workflow **не подписан**; перед загрузкой в Google Play необходимо настроить подпись своим ключом.

## Важно о Google Play

Проект всё ещё использует WebView для отображения сайта. Сам по себе технически исправный WebView не гарантирует одобрение: Google Play может отклонить приложения, которые лишь показывают сайт и не дают достаточной самостоятельной ценности или не имеют разрешения владельца сайта. Для полноценной публикации стоит добавить действительно полезные нативные функции и подтвердить право распространять сайт в приложении.

Сборка не была выполнена в этой среде и не проверялась на физическом устройстве. Запусти GitHub Actions и проверь, что workflow завершился успешно, прежде чем устанавливать артефакт.

## Обновлённый интерфейс приложения

Приложение продолжает загружать настоящий сайт `https://web.zvykmusic.ru/` внутри WebView — каталог, поиск, аккаунт и действия сайта не дублируются отдельными фейковыми экранами. После загрузки страницы приложение добавляет тёмное glass-оформление и декоративную интерактивную аудиоволну рядом с HTML `<audio>` элементами. Волна показывает прогресс и позволяет перематывать трек нажатием.

## Автоматическая публикация APK

GitHub Actions собирает debug APK при push в `main`/`master` и публикует его в GitHub Release с тегом `latest`, заменяя предыдущий `ZvykMusic.apk`. APK не загружается как Actions Artifact, поэтому не расходует квоту хранения Artifacts. Это debug-сборка; для публикации в Google Play нужно настроить подпись release-сборки собственным keystore.

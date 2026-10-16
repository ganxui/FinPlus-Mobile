# FinPlus Mobile

> **License:** source code is public for inspection and security review only.
> Copying, redistribution, hosting, resale, rebranding, and derivative works
> are prohibited without prior written permission. See [LICENSE](LICENSE).

Официальная Android-оболочка существующего FinPlus. Она не дублирует backend или бизнес-логику: защищённый `WebView` открывает `https://finplus.su`, поэтому авторизация, API, роли, AI, наставники, маркетплейс, заказы, чаты, кошелёк и админ-панель остаются едиными с сайтом.

## Что реализовано

- Android 10+ (`minSdk 29`, `targetSdk 36`), Java 17;
- adaptive icon и Android 12+ splash на основе логотипа FinPlus;
- сохранение HttpOnly session cookie в изолированном хранилище WebView;
- нативный выбор файлов для импорта выписок;
- нативный pull-to-refresh и системная кнопка «Назад»;
- экран отсутствия сети и автоматическое обновление после восстановления;
- синхронизация светлых/тёмных системных панелей;
- notification channel, разрешение Android 13+ и JS bridge для будущей FCM-регистрации;
- запрет cleartext HTTP, mixed content, сторонних cookies и открытия неизвестных доменов внутри приложения;
- debug APK, подписанные release APK и AAB.

## Структура

```text
mobile/
  app/src/main/
    java/ru/finplus/app/MainActivity.java  WebView, сеть, файлы, жесты, bridge
    res/                                   темы, splash, adaptive icon
    AndroidManifest.xml
  scripts/generate-release-key.ps1
  app/build.gradle
  build.gradle
  settings.gradle
```

## Сборка

Требуются JDK 17, Android SDK Platform 36 и Build Tools 36.1.0.

```powershell
cd mobile
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```


Release-ключ создаётся один раз командой `.\scripts\generate-release-key.ps1`. `finplus-release.jks` и `keystore.properties` не коммитятся. Сохраните оба файла и пароль в защищённой резервной копии: без исходного ключа обновить опубликованное приложение нельзя.

## Push-уведомления

Нативный канал и разрешения уже готовы. Для рабочего push нужно создать Android-приложение `ru.finplus.app` в Firebase, положить `google-services.json` в `mobile/app/`, подключить Google Services + Firebase Messaging и отправлять FCM token в отдельный защищённый endpoint backend. До добавления Firebase приложение не запрашивает и не имитирует push token.

## Публикация в Google Play

1. Создайте приложение в Play Console с package name `ru.finplus.app`.
2. Заполните карточку, политику конфиденциальности, Data safety и возрастной рейтинг.
3. Включите Play App Signing и загрузите release AAB во внутреннее тестирование.
4. Добавьте тестовые аккаунты для проверки закрытых разделов.
5. Проверьте pre-launch report, затем продвигайте сборку в closed/open testing и production.
6. Для каждой следующей версии увеличивайте `versionCode`, сохраняйте тот же release-ключ и собирайте новый AAB.

Перед production замените Maven-зеркало в `settings.gradle` на прямые `google()`/`mavenCentral()`, если инфраструктура имеет стабильный TLS-доступ к ним.

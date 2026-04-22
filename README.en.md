# Nono

[![中文](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-blue)](README.md)

Nono is an Android notification rule tool for filtering notifications by app, keyword, weekday, and working hours.

## Sponsor

If Nono is useful to you, you can support the project through Open Collective on GitHub Sponsors:
[https://opencollective.com/nonotification](https://opencollective.com/nonotification)

## Interface

- `Notifications`: View delayed and collected notifications
- `Rules`: View and edit existing rules
- `Config`: Search installed apps, automatically fill package names, and create rules
- `Settings`: Notification permission, import/export, and package name guidance

## Search Apps And Auto-Fill Package Names

1. Open the `Config` tab.
2. Enter part of an app name or package name in the search box.
3. If an app is still missing, it is usually related to a work profile, cloned-app space, or a manufacturer-specific private container.

## Rule Actions

- `Block notification`: Cancel the notification directly when a rule matches
- `Allow notification`: Let the notification pass through when a rule matches
- `Delay notification`: Cancel the original notification when a rule matches, then let Nono send a local reminder after the configured delay
- `System ringer`: When a rule matches, Nono can try to keep the current mode or switch the system to ring, vibrate, or silent

Notes:

- `Delay notification` requires Nono to send its own notifications, so Android 13 and above require notification permission
- `Vibrate/silent` may also require Do Not Disturb access on some systems, otherwise the system may reject the mode switch

## Import And Export Configuration

- Export: Tap `Export config` on the `Settings` page to generate a JSON file
- Import: Tap `Import config` on the `Settings` page and select a previously exported JSON file
- Current import strategy: Imported rules overwrite local rules

## How To Run

1. Open the project root directory in Android Studio or IntelliJ IDEA.
2. Wait for Gradle sync to finish.
3. Connect a physical device and enable USB debugging.
4. Run the `app` configuration.
5. After installation, go to the `Settings` page and enable notification access.
6. Return to the `Rules` or `Config` page to adjust rules.

## Sponsor

If Nono is useful to you, you can support the project through Open Collective on GitHub Sponsors:
[https://opencollective.com/nonotification](https://opencollective.com/nonotification)

## License

This project is open source under the [Apache License 2.0](LICENSE).

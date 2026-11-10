# Telegram Rust Client

Android client prototype with a Kotlin interface and a Rust MTProto core.

## Local AndroidIDE build

Open the project in AndroidIDE and run `:app:assembleDebug`.

## GitHub Actions build

Push to `main`, then download the `telegram-rust-client-debug` artifact from the successful workflow run.

The app stores the Telegram session only in its private app storage. API values and passwords are not sent to this repository.

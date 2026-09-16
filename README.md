# Veyra Messenger — v9 Functional UI Fix

Ushbu versiyada ko‘rinib turgan asosiy tugmalar qayta ulandi:

- Hamburger menyu haqiqiy DrawerState bilan ishlaydi.
- Search alohida sahifaga ochiladi.
- New chat / New group / New channel dialoglari ishlaydi va chatlar lokal saqlanadi.
- Saved Messages alohida ishlaydigan sahifaga ega.
- Contacts sahifasi va kontakt qo‘shish ishlaydi.
- Profile rasmi, ism va bio saqlanadi.
- Settings ichidan Profile, Privacy & Security va Terms of Use ochiladi.
- Test notification ishlaydi.
- Chatdagi uch nuqta menyusi: rasm, fayl, chat info va xabarlarni tozalash amallari ishlaydi.
- Xabar yuborish, rasm/fayl tanlash, uzun bosib tahrirlash/o‘chirish ishlaydi.
- PIN, biometrika, TOTP 2FA va screenshot protection saqlab qolingan.
- Lokal username/parol ro‘yxatdan o‘tishi va tekshiruvi qo‘shilgan.

## Build

Windows + Android Studio JDK 21:

```bat
gradlew.bat :app:assembleDebug --console=plain
```

APK:

```text
app\\build\\outputs\\apk\\debug\\app-debug.apk
```

Bu loyiha demo/local messenger hisoblanadi. Haqiqiy server, WebSocket, PostgreSQL, push notification va E2EE uchun Go backend hali alohida ulanadi.

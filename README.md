# Travesseiro Sensorial (Gitpod-ready)

Este repositório contém um projeto Android (Kotlin + Views/XML) chamado **Travesseiro Sensorial**.

## Como abrir no Gitpod (1-clique)
1. Crie um repositório no GitHub (ex: `travesseiro-sensorial`) e faça push de todo o conteúdo deste pacote.
2. Abra no Gitpod com este link (troque `USERNAME` e `REPO` pelo seu GitHub):
   `https://gitpod.io/#https://github.com/USERNAME/REPO`

## O que inclui
- Activities: DashboardActivity, SettingsActivity
- Bluetooth helper (BLE + SPP fallback)
- Models: UserSettings, PillowStatus, HealthData
- Layouts e recursos
- `app/build.gradle`, `settings.gradle`, `build.gradle`
- `.gitpod.yml` para preparar SDK e compilar

## Build
No Gitpod, após o ambiente iniciar, rode:
```
./gradlew assembleDebug
```
O APK estará em:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Notas
- Emuladores Android geralmente não funcionam bem em ambientes remotos — use um device real ou suba o APK no Appetize.io para testes.
- Ajuste UUIDs de BLE conforme seu firmware.

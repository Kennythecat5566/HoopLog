# HoopLog

離線 Android 籃球訓練紀錄 App。核心資料存在手機本機 SQLite，不需要登入或雲端服務。

## 功能

- 每日 checklist，可新增、修改、停用訓練項目
- 每個項目可設定組數與組間休息秒數
- 今日完成狀態會保存成當日紀錄
- 可回顧過去每日訓練完成狀況
- 可設定 GitHub repo，從 Release 頁面檢查新版 APK

## 建置

```powershell
.\gradlew.bat assembleDebug
```

產物會在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Release 更新

一般 Android App 無法像 Play Store 一樣從 GitHub 背景靜默自動更新。HoopLog 會檢查 GitHub 最新 Release，若版本較新，開啟 Release 頁面讓使用者下載 APK 並安裝。

到 App 的「設定」頁填入：

- Owner：GitHub 帳號或組織
- Repo：這個專案的 repo 名稱

GitHub Actions 會在推 tag 時自動產生 Release APK。

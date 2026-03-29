# 🏠 Home Expense & Budget Tracker — Android App

> A full-featured Android application for tracking household expenses and managing monthly budgets, with automated CI/CD via **GitHub Actions**.

---

## 📱 Features

| Feature | Description |
|---------|-------------|
| **Expense Tracking** | Log any expense with title, amount, category, date, payment method |
| **16 Default Categories** | Food, Groceries, Transport, Utilities, Healthcare, Entertainment… |
| **Monthly Budgets** | Set per-category spending limits with live progress indicators |
| **Dashboard** | Total spend, budget remaining, recent transactions at a glance |
| **Analytics & Charts** | Pie charts (by category) + Bar charts (daily trend) via MPAndroidChart |
| **Recurring Expenses** | Tag expenses as Daily / Weekly / Monthly / Yearly |
| **CSV Export** | One-tap export of all expenses to a `.csv` file |
| **Search & Filter** | Filter by category, date range, or search by title |
| **Over-Budget Alerts** | Progress bars turn red when you hit a configurable threshold |

---

## 🏗️ Architecture

```
MVVM + Repository Pattern + Hilt Dependency Injection
```

```
app/src/main/java/com/home/expensetracker/
├── data/
│   ├── database/       # Room DAOs, DB instance
│   ├── models/         # Expense, Budget, Category entities
│   └── repository/     # Single source of truth
├── viewmodel/          # ExpenseViewModel, BudgetViewModel
├── ui/
│   ├── activities/     # MainActivity, AddExpenseActivity, SplashActivity
│   ├── fragments/      # Dashboard, Transactions, Budget, Reports
│   └── adapters/       # RecyclerView adapters
├── utils/              # DateUtils, CurrencyUtils, CSVExporter
└── di/                 # Hilt AppModule
```

### Libraries
- **Room** — local SQLite database with coroutine support
- **Hilt** — dependency injection
- **Kotlin Coroutines + Flow** — reactive data streams
- **Navigation Component** — fragment navigation with bottom nav
- **MPAndroidChart** — pie & bar charts
- **Material Components** — UI widgets

---

## 🤖 GitHub Actions CI/CD Pipeline

```
push → Lint → Unit Tests → Debug APK → Release APK → GitHub Release
```

### Workflow Jobs

| Job | Trigger | Output |
|-----|---------|--------|
| 🔍 **Lint** | Every push / PR | `lint-results.html` artifact |
| 🧪 **Unit Tests** | After lint passes | Test report artifact |
| 🔨 **Debug Build** | After tests pass | `ExpenseTracker-debug-#.apk` |
| 🚀 **Release Build** | `main` branch only | `ExpenseTracker-release-v#.apk` |
| 📦 **GitHub Release** | `main` push only | Tagged release with APK |
| 📬 **Build Summary** | Always | Job status table in Actions summary |

### Workflow file
```
.github/workflows/android-build.yml
```

### Required Secrets (for signed release APK)

Go to **Settings → Secrets → Actions** and add:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -i your-keystore.jks` |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Keystore password |

> Without these secrets, the workflow still builds an **unsigned** release APK.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Clone & Build
```bash
git clone https://github.com/YOUR_USERNAME/ExpenseBudgetTracker.git
cd ExpenseBudgetTracker
./gradlew assembleDebug
```

### Install on device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run tests
```bash
./gradlew testDebugUnitTest
```

### Run lint
```bash
./gradlew lint
```

---

## 📲 Screenshots

| Dashboard | Add Expense | Budget | Reports |
|-----------|-------------|--------|---------|
| Month summary, recent txns | Category picker, amount | Progress bars per category | Pie & bar charts |

---

## 🗂️ Database Schema

### `expenses`
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| title | TEXT | Expense name |
| amount | REAL | Amount in ₹ |
| category | TEXT | Category name |
| categoryIcon | TEXT | Emoji icon |
| date | INTEGER | Unix timestamp |
| note | TEXT | Optional note |
| paymentMethod | TEXT | Cash/Card/UPI/etc |
| isRecurring | INTEGER | Boolean |
| recurringPeriod | TEXT | None/Daily/Weekly/Monthly |

### `budgets`
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| category | TEXT | Category name |
| monthlyLimit | REAL | Limit in ₹ |
| month | INTEGER | 1–12 |
| year | INTEGER | e.g. 2025 |
| alertThreshold | INTEGER | % to alert (default 80) |

### `categories`
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| name | TEXT | Category label |
| icon | TEXT | Emoji |
| colorHex | TEXT | Chart colour |
| isDefault | INTEGER | Boolean |

---

## 🔐 Generating a Keystore (for release signing)

```bash
keytool -genkey -v \
  -keystore expense-tracker.jks \
  -alias expense-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Encode for GitHub secret
base64 -i expense-tracker.jks | pbcopy   # macOS
base64 expense-tracker.jks               # Linux
```

---

## 📁 Project Structure

```
ExpenseBudgetTracker/
├── .github/
│   └── workflows/
│       └── android-build.yml        ← CI/CD pipeline
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/home/expensetracker/
│   │   │   │   ├── data/            ← Room DB + models
│   │   │   │   ├── viewmodel/       ← MVVM ViewModels
│   │   │   │   ├── ui/              ← Activities + Fragments
│   │   │   │   └── utils/           ← Helpers
│   │   │   ├── res/                 ← Layouts, drawables, values
│   │   │   └── AndroidManifest.xml
│   │   └── test/                    ← Unit tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml           ← Version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request → CI will run automatically

---

## 📄 License

```
MIT License — free to use, modify, and distribute.
```

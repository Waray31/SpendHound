# SpendHound 🐾

SpendHound is a comprehensive personal and group finance management application for Android. It helps users track their expenses, manage group budgets, and keep a clear record of borrowed and owed amounts.

## 🚀 Features

- **Transaction Management**: Easily record and categorize your daily expenses and income.
- **Group Expenses**: Create groups with friends or family to track shared costs and settle up easily.
- **Borrow & Owe Tracking**: Keep a dedicated log of money you've borrowed or lent to others.
- **Data Visualization**: Gain insights into your spending habits with interactive charts and graphs.
- **Secure Access**: Protect your financial data with biometric authentication (Fingerprint/Face Unlock).
- **Real-time Sync**: Stay updated across devices using Supabase and Firebase integration.
- **Archived Transactions**: Keep your main list clean by archiving old transactions while maintaining access to historical data.
- **Skeleton Loading**: Smooth user experience with shimmer effects during data fetching.

## 📈 Recent Updates

### Database Schema Alignment (March 2026)
- **Schema Synchronization**: All data models (User, Transaction, Group, Borrow) have been aligned with the PostgreSQL/Supabase schema.
- **Standardized Accessors**: Implementation of consistent getter and setter methods across all core data classes.
- **Integration**: Updated `BorrowFragment` and related components to support the new unified data structure.
- **Documentation**: Comprehensive internal documentation for method signatures and schema mappings.

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Hybrid approach with **Jetpack Compose** and **XML (ViewBinding/DataBinding)**.
- **Architecture**: MVVM (Model-View-ViewModel) with Coroutines and Flow.
- **Database**: 
    - **Local**: Room Persistence Library for offline capability.
    - **Remote**: Supabase (PostgreSQL) and Firebase (Realtime Database).
- **Networking**: Retrofit & Ktor.
- **Image Loading**: Coil & Glide.
- **Charts**: MPAndroidChart.
- **Security**: Biometric API & Security-Crypto.
- **Dependency Management**: Gradle with Version Catalog (BOM).

## 📁 Project Structure

```text
SpendHound/
├── app/
│   ├── src/main/java/com/waray/spendhound/      # Application logic
│   │   ├── ui/                                # Compose and View-based UI components
│   │   ├── model/                             # Data models (Transaction, User, etc.)
│   │   ├── utils/                             # Helper classes and extensions
│   │   └── ...
│   ├── src/main/res/                          # Resources (Layouts, Drawables, Values)
│   └── build.gradle                           # App-level build configuration
├── build.gradle                               # Project-level build configuration
└── settings.gradle                            # Project settings
```

## ⚙️ Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer.
- JDK 17.
- Android SDK 34.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/SpendHound.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Ensure you have the necessary `google-services.json` (for Firebase) and Supabase configuration in your environment or `local.properties`.
5. Build and run the app on an emulator or physical device (Min SDK 24).

## 📄 License

This project is licensed under the MIT License.

---
*Developed with ❤️ by the SpendHound Team.*

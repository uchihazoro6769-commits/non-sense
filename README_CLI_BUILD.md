# VeyraChat — CLI build

## Windows
Use JDK 21 (Android Studio's `jbr-21` is recommended).

Open Command Prompt in this folder:

```bat
gradlew.bat assembleDebug
```

The first run downloads the Gradle 8.9 distribution and, if needed, the wrapper JAR. Internet access is required on the first run.

APK:

`app\\build\\outputs\\apk\\debug\\app-debug.apk`

## If Java is not found
Set `JAVA_HOME` to Android Studio's JBR 21, for example:

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
```

Then run:

```bat
gradlew.bat assembleDebug
```

Do not use JBR 25 for this project; use JDK/JBR 21.

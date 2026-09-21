# Android Image Cropper

[![JitPack](https://jitpack.io/v/appuraja1/Android-Image-Cropper.svg)](https://jitpack.io/#appuraja1/Android-Image-Cropper)
[![Android Min SDK](https://img.shields.io/badge/minSdkVersion-21-blue.svg)](https://android-arsenal.com/api?level=21)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()

A lightweight, modern, and crash-proof image cropping library for Android. Upgraded for modern Android versions (Android 14+ support), pure pitch-black theme, Scoped Storage compatibility, and zero memory leaks.

---

## What’s New in This Version
- **Zero AsyncTask Deprecations:** Migrated completely to modern background threading (`ExecutorService` + main thread `Handler`).
- **Modern Concurrency & Memory Safety:** Integrated automatic memory recycling and `WeakReference` cleanup to prevent Out Of Memory (OOM) errors.
- **Android 14+ / API 34 Ready:** Fully updated Scoped Storage permissions (`READ_MEDIA_IMAGES`) and `FileProvider` camera intents.
- **Pure Black UI:** Pitch-black theme (`#000000`) for the cropping canvas, action bar, and system navigation bars.
- **Touch & Margin Fixes:** Edge-to-edge breathing room so corner resize handles and menu action text never get clipped.

---

## Installation

### 1. Add JitPack Repository
Root `settings.gradle` (or project root `build.gradle`):

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url '[https://jitpack.io](https://jitpack.io)' }
    }
}

```

### 2. Add Library Dependency

App-level `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.appuraja1:Android-Image-Cropper:1.0.0'
}

```

---

## Usage

### Option 1: Using the Built-in Activity

The `CropImageActivity` comes ready to use.

#### Launching the Cropper:

```java
// Option A: Open system picker (Camera/Gallery) directly into Cropper
CropImage.activity()
    .setGuidelines(CropImageView.Guidelines.ON)
    .setCropShape(CropImageView.CropShape.RECTANGLE)
    .start(this);

// Option B: Crop an existing Uri
CropImage.activity(imageUri)
    .setAspectRatio(1, 1)
    .setFixAspectRatio(true)
    .start(this);

// Option C: From a Fragment
CropImage.activity()
    .start(requireContext(), this);

```

#### Handling the Result:

```java
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
        CropImage.ActivityResult result = CropImage.getActivityResult(data);
        if (resultCode == RESULT_OK) {
            Uri resultUri = result.getUri();
            // Use your cropped image Uri
        } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
            Exception error = result.getError();
        }
    }
}

```

---

### Option 2: Using the Custom View

Embed `CropImageView` inside your layout XML:

```xml
<com.github.appuraja1.cropper.CropImageView
    android:id="@+id/cropImageView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:cropGuidelines="on"
    app:cropScaleType="fitCenter"
    app:cropShape="rectangle" />

```

Control from Java:

```java
CropImageView cropImageView = findViewById(R.id.cropImageView);

// Load image asynchronously
cropImageView.setImageUriAsync(imageUri);

// Perform async crop
cropImageView.setOnCropImageCompleteListener((view, result) -> {
    if (result.isSuccessful()) {
        Bitmap cropped = result.getBitmap();
    } else {
        Exception error = result.getError();
    }
});

cropImageView.getCroppedImageAsync();

```

---

## ProGuard Rules

If you are using R8/ProGuard in your application, add the following to `proguard-rules.pro`:

```proguard
-keep class com.github.appuraja1.cropper.** { *; }
-keepclassmembers class com.github.appuraja1.cropper.** { *; }
-dontwarn com.github.appuraja1.cropper.**

```

---

## License

```text
Copyright 2026 Appu Raja

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

```


```

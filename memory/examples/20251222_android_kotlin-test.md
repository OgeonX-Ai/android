# Android unit tests resolve kotlin.test

Issue Description:
Kotlin test assertions unresolved in unit tests.

State:
Kotlin compilation failed due to missing kotlin.test dependency.

Action:
Added testImplementation(kotlin("test")) to the app module.

Result:
Unit tests compile with kotlin.test assertions.

Rationale:
kotlin.test requires explicit dependency for JUnit-based tests.

Diff Patch:
```diff
commit b0d4cfdbfae7b31319a33f7223282dd312573a28
Author: Kim Harjamaki <ogeon@msn.com>
Date:   Mon Dec 22 23:21:21 2025 +0200

    Add kotlin test dependency

diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index bf1be1f..d999742 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -54,6 +54,7 @@ dependencies {
     implementation(libs.androidx.compose.material3)
     implementation("com.google.android.material:material:1.12.0")
 
+    testImplementation(kotlin("test"))
     testImplementation(libs.junit)
     androidTestImplementation(libs.androidx.junit)
     androidTestImplementation(libs.androidx.espresso.core)
```

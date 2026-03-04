# Consumer proguard rules for adaptive-grid library

# Keep all public classes and methods in the library
-keep public class com.lazyadaptivelayout.** { *; }
-keep public interface com.lazyadaptivelayout.** { *; }

# Keep Compose-related DSL methods
-keep class com.lazyadaptivelayout.AdaptiveGridScope { *; }
-keep class com.lazyadaptivelayout.AdaptiveGridScopeImpl { *; }
-keep class com.lazyadaptivelayout.CustomGroupScope { *; }

# Keep model classes
-keep class com.lazyadaptivelayout.model.** { *; }

# Keep state classes
-keep class com.lazyadaptivelayout.state.** { *; } 
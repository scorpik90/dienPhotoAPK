# ML Kit использует reflection для моделей, Room — генерирует код.
-keep class com.google.mlkit.** { *; }
-keep class ru.delivery.photo.data.db.** { *; }

# ML Kit / CameraX
-dontwarn com.google.mlkit.**

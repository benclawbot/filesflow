# FilesFlow relies on AndroidX, Compose, DocumentFile, Coil and Kotlin metadata.
# Their consumer ProGuard rules are packaged with the dependencies. Keep this
# file intentionally minimal so release shrinking remains effective.

# Preserve generic signatures and annotations used by AndroidX tooling.
-keepattributes Signature,*Annotation*

# Keep FileProvider subclasses and constructors referenced from the manifest.
-keep public class * extends androidx.core.content.FileProvider {
    public <init>();
}

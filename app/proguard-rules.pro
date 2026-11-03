# Правила для сжатия кода (R8).
# Классы из AndroidManifest (экраны, службы, приёмники) R8 сохраняет сам.
# Библиотеки (Compose, Google Play services, ML Kit) приносят свои правила.

# Имена в отчётах об ошибках — чтобы можно было понять, где упало.
-keepattributes SourceFile,LineNumberTable

# Union of the two clients' rules — R8 runs over the whole merged app, so both
# halves' keep rules have to be present here even though the code they protect
# lives in the feature modules.

# Tink (pulled in via androidx.security:security-crypto) references Google's
# errorprone annotations, which are compile-only and absent at runtime — R8
# treats the missing classes as an error without this.
-dontwarn com.google.errorprone.annotations.**

# kotlinx.serialization: keep the generated serializer lookups for the DTOs on
# both sides. Renzo's models are top-level in its api package; Shiori's sit
# under data.model.
-keepclassmembers class app.renzoshiori.client.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class app.renzoshiori.client.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class top.levitatemedia.renzo.tv.api.** {
    *** Companion;
}
-keepclasseswithmembers class top.levitatemedia.renzo.tv.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

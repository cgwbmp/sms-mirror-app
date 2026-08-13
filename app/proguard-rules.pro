# JavaMail resolves providers reflectively through its META-INF descriptors.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn java.awt.**
-dontwarn javax.security.**

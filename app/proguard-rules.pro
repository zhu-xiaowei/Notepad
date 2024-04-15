# Add project specific ProGuard rules here

-keepattributes LineNumberTable,SourceFile

# we can remvoe this keep file, total impact is 5B
-dontwarn software.aws.solution.clickstream.**
-keep class software.aws.solution.clickstream.**{*;}
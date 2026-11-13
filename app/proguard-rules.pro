# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 指定不去忽略非公共的库的类的成员
-dontskipnonpubliclibraryclassmembers

# google推荐算法
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# 避免混淆Annotation、内部类、泛型、匿名类
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepattributes LineNumberTable

# 混淆时不实用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames


# 保持四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class * extends android.widget.**
-keep public class * extends android.support.**
-keep public class * extends androidx.**
-keep public class * extends google.**

-keep public class com.android.vending.licensing.ILicensingService

# 保持support下的所有类及其内部类
-keep class android.support.** {*;}
-dontwarn android.support.**

# 保留继承的
-keep public class * extends android.support.v4.**
-keep public class * extends android.support.v7.**
-keep public class * extends android.support.annotation.**

# 保持自定义控件
-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保持所有实现 Serializable 接口的类成员
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


# webView处理
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
    public *;
}
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.webView, jav.lang.String);
}

# RenderScript
-keepclasseswithmembernames class * {
native <methods>;
}
-keep class androidx.renderscript.** { *; }

#----------------



#保持 native 方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

#保持自定义控件类不被混淆
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclasseswithmembers class * {
    void onClick*(...);
}
-keepclasseswithmembers class * {
    *** *Callback(...);
}

#保持自定义控件类不被混淆
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    *** get*();
}

#保持 Parcelable 不被混淆
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

#保持 Serializable 不被混淆
-keepnames class * implements java.io.Serializable

#保持 Serializable 不被混淆并且enum 类也不被混淆
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#保持枚举 enum 类不被混淆
-keepclassmembers enum * {
  public static **[] values();
  public static ** valueOf(java.lang.String);
}

-keepclassmembers class * {
    public void *ButtonClicked(android.view.View);
}

#不混淆资源类
-keepclassmembers class **.R$* {
    public static <fields>;
}

#---------
#gson
#如果用用到Gson解析包的，直接添加下面这几行就能成功混淆，不然会报错。
-keepattributes Signature
# Gson specific classes
-keep class sun.misc.Unsafe { *; }
# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }


-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}


-assumenosideeffects class android.util.Log {
public static boolean isLoggable(java.lang.String, int);
public static int v(...);
public static int i(...);
public static int w(...);
public static int d(...);
public static int e(...);
public static int println(...);
}

-keep class androidx.** {*;}
-keep class android.app.** {*;}

-keep class com.airbnb.lottie.** {*;}
-dontwarn com.airbnb.lottie.**
-keep class com.blankj.utilcode.** {*;}
-dontwarn com.blankj.utilcode.**
-keep class com.hjq.permissions.** {*;}
-dontwarn com.hjq.permissions.**
-keep class com.tencent.mmkv.** {*;}
-dontwarn com.tencent.mmkv.**
-dontwarn javax.annotation.**
# okhttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotaion.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keep class com.google.ads.** {*;}
-keep class com.applovin.** {*;}

# 不混淆 Keep 类
-keep class androidx.annotation.Keep

# 保护数据类 model
-keep class com.pdf.editor.data.** {*;}
-dontwarn com.pdf.editor.data.**

# Retain service method parameters.
-keepclassmembernames,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-keep class kotlinx.coroutines.android.** {*;}


-keep class com.google.android.gms.common.ConnectionResult {
    int SUCCESS;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    java.lang.String getId();
    boolean isLimitAdTrackingEnabled();
}
-keep public class com.android.installreferrer.**{ *; }

#-----eventbus------
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}


-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class com.appsflyer.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
# ========== pdfium-android (native) ==========
# 保留 pdfium 相关类（根据实际包名微调）
-keep class io.github.oothp.pdfium.** { *; }
# 如果 pdfium 使用 com.shockwave.pdfium 或其他包，请据实添加
-dontwarn io.github.oothp.pdfium.**

# PDF14-MIGRATION: 原样沿用 pdf14 的 PDFium JNI 保留规则。
-keep class io.legere.pdfiumandroid.** { *; }
-dontwarn io.legere.pdfiumandroid.**

# WorkManager persists worker class names and recreates workers via reflection.
# Services and receivers in this package are covered by the global component rules above.
-keepnames class com.pdf.editor.common.nf.work.** extends androidx.work.ListenableWorker
-keepclassmembers class com.pdf.editor.common.nf.work.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Media notification reflection support.
# OtherNF builds MediaSessionCompat / MediaMetadataCompat / PlaybackStateCompat
# and NotificationCompat.MediaStyle via reflection in the media notification path,
# so these classes, methods, and constants must keep stable names in release builds.
-keep class android.support.v4.media.session.MediaSessionCompat { *; }
-keep class android.support.v4.media.session.MediaSessionCompat$Token { *; }

-keepclassmembers class android.support.v4.media.session.MediaSessionCompat {
    public <init>(android.content.Context, java.lang.String);
    public void setFlags(int);
    public void setActive(boolean);
    public java.lang.Object getSessionToken();
    public void setMetadata(android.support.v4.media.MediaMetadataCompat);
    public void setPlaybackState(android.support.v4.media.session.PlaybackStateCompat);
}

-keep class android.support.v4.media.MediaMetadataCompat { *; }
-keep class android.support.v4.media.MediaMetadataCompat$Builder { *; }

-keepclassmembers class android.support.v4.media.MediaMetadataCompat {
    public static final java.lang.String METADATA_KEY_TITLE;
    public static final java.lang.String METADATA_KEY_ARTIST;
    public static final java.lang.String METADATA_KEY_ALBUM_ART;
    public static final java.lang.String METADATA_KEY_ART;
}

-keepclassmembers class android.support.v4.media.MediaMetadataCompat$Builder {
    public <init>();
    public android.support.v4.media.MediaMetadataCompat$Builder putString(java.lang.String, java.lang.String);
    public android.support.v4.media.MediaMetadataCompat$Builder putBitmap(java.lang.String, android.graphics.Bitmap);
    public android.support.v4.media.MediaMetadataCompat build();
}

-keep class android.support.v4.media.session.PlaybackStateCompat { *; }
-keep class android.support.v4.media.session.PlaybackStateCompat$Builder { *; }

-keepclassmembers class android.support.v4.media.session.PlaybackStateCompat {
    public static final long ACTION_PLAY_PAUSE;
    public static final int STATE_NONE;
    public static final int STATE_PLAYING;
    public static final long PLAYBACK_POSITION_UNKNOWN;
}

-keepclassmembers class android.support.v4.media.session.PlaybackStateCompat$Builder {
    public <init>();
    public android.support.v4.media.session.PlaybackStateCompat$Builder setActions(long);
    public android.support.v4.media.session.PlaybackStateCompat$Builder setState(int, long, float);
    public android.support.v4.media.session.PlaybackStateCompat build();
}

-keep class androidx.media.app.NotificationCompat$MediaStyle { *; }

-keepclassmembers class androidx.media.app.NotificationCompat$MediaStyle {
    public <init>();
    public androidx.media.app.NotificationCompat$MediaStyle setMediaSession(
        android.support.v4.media.session.MediaSessionCompat$Token
    );
    public androidx.media.app.NotificationCompat$MediaStyle setShowActionsInCompactView(int[]);
}

-keepclassmembers class android.view.WindowManager$LayoutParams {
    public <init>(int,int,int,int,int);
    public int gravity;
    public int x;
    public int y;
}

package com.pdf.editor.common.user

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object DeviceRiskUtils {
    val isEmulator: Boolean
        get() {
            runCatching {
                val model = Build.MODEL
                val product = Build.PRODUCT
                val hardware = Build.HARDWARE
                val device = Build.DEVICE
                val brand = Build.BRAND
                val board = Build.BOARD
                val manufacture = Build.MANUFACTURER

                if (model.contains("sdk_gphone") || model.contains("google_sdk") ||
                    model.lowercase(Locale.getDefault()).contains("droid4x") ||
                    model.contains("Emulator") ||
                    model.contains("Android SDK built for x86") ||
                    model.startsWith("iToolsAVM") ||
                    manufacture.contains("Genymotion") ||
                    manufacture.startsWith("iToolsAVM") || hardware == "goldfish" || hardware == "vbox86" ||
                    hardware.lowercase(Locale.getDefault()).contains("nox") ||
                    hardware.startsWith("vbox86") || product == "sdk" ||
                    product.startsWith("google_sdk") || product == "sdk_x86" || product == "vbox86p" ||
                    product.lowercase(Locale.getDefault()).contains("nox") ||
                    brand.startsWith("generic") ||
                    device.startsWith("iToolsAVM") ||
                    board.lowercase(Locale.getDefault()).contains("nox") ||
                    Build.BOOTLOADER.lowercase(Locale.getDefault()).contains("nox") ||
                    Build.SERIAL.lowercase(Locale.getDefault()).contains("nox") ||
                    Build.HOST.contains("Droid4x-BuildStation")
                ) {
                    return true
                }

                var risk = 0
                if (mutableListOf(
                        "sdk_x86_64", "sdk_google_phone_x86", "sdk_google_phone_x86_64", "sdk_google_phone_arm64", "vbox86p"
                    ).contains(product)
                ) {
                    ++risk
                }
                if (manufacture == "unknown") {
                    ++risk
                }
                if (mutableListOf<String>(
                        "generic", "generic_arm64", "generic_x86", "generic_x86_64"
                    ).contains(brand)
                ) {
                    ++risk
                } else if (brand.equals("android", ignoreCase = true)) {
                    ++risk
                }
                if (mutableListOf<String>(
                        "generic", "generic_arm64", "generic_x86", "generic_x86_64", "vbox86p"
                    ).contains(device)
                ) {
                    ++risk
                }

                if (mutableListOf<String>(
                        "sdk",
                        "Android SDK built for arm64",
                        "Android SDK built for armv7",
                        "Android SDK built for x86",
                        "Android SDK built for x86_64"
                    ).contains(model)
                ) {
                    ++risk
                }
                if (hardware == "ranchu") {
                    ++risk
                }
                if (brand.startsWith("generic") && device.startsWith("generic")) {
                    ++risk
                }
                return risk >= 2
            }.onFailure {

            }
            return false
        }

    val isEmulator2: Boolean
        get() {
            runCatching {
                if (existFile(arrayOf("/dev/socket/genyd", "/dev/socket/baseband_genyd")) ||
                    existFile(arrayOf("fstab.andy", "ueventd.andy.rc")) ||
                    existFile(arrayOf("fstab.nox", "init.nox.rc", "ueventd.nox.rc")) ||
                    existFile(arrayOf("/dev/socket/qemud", "/dev/qemu_pipe")) ||
                    existFile(
                        arrayOf(
                            "ueventd.android_x86.rc", "x86.prop", "ueventd.ttVM_x86.rc",
                            "init.ttVM_x86.rc", "fstab.ttVM_x86", "fstab.vbox86", "init.vbox86.rc", "ueventd.vbox86.rc"
                        )
                    )
                ) {
                    return true
                }

                var data: ByteArray
                val blackString = arrayOf("goldfish")
                val files = arrayOf(File("/proc/tty/drivers"), File("/proc/cpuinfo"))
                for (file in files) {
                    if (file.exists() && file.canRead()) {
                        try {
                            data = ByteArray(0x400)
                            val fileInputStream0 = FileInputStream(file)
                            val length = fileInputStream0.read(data)
                            fileInputStream0.close()
                            val s = String(data)
                            for (black in blackString) {
                                if (s.contains(black)) {
                                    return true
                                }
                            }
                        } catch (ignore: Throwable) {
                        }
                    }
                }
            }
            return false
        }

    fun isDevModel(context: Context): Boolean {
        runCatching {
            return Settings.Secure.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }.onFailure {}
        return false
    }

    fun isDebug(context: Context): Boolean {
        try {
            return Settings.Secure.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        } catch (ignore: Throwable) {
        }
        return false
    }

    @Volatile
    private var cachedIsXposed: Boolean? = null

    val isXposed: Boolean
        get() {
            runCatching {
                // 如果已经计算过，直接返回缓存值
                cachedIsXposed?.let { return it }

                synchronized(this) {
                    // 双重检查，避免多线程重复计算
                    cachedIsXposed?.let { return it }

                    val detected = runCatching {
                        Class.forName(
                            "de.robv.android.xposed.XposedBridge",
                            false,
                            ClassLoader.getSystemClassLoader()
                        )
                    }.isSuccess

                    cachedIsXposed = detected
                    return detected
                }
            }
            return false
        }

    val isAbnormalEnv: Boolean
        get() {
            runCatching {
                for (file in arrayOf(
                    "/su", "/su/bin/su", "/sbin/su", "/system/bin/su", "/system/xbin/su",
                    "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                    "/system/bin/failsafe/su", "/data/local/su", "/system/bin/cufsdosck", "/system/xbin/cufsdosck",
                    "/system/bin/cufsmgr", "/system/xbin/cufsmgr", "/system/bin/cufaevdd", "/system/xbin/cufaevdd",
                    "/system/bin/conbb", "/system/xbin/conbb"
                )) {
                    if (File(file).exists()) {
                        return true
                    }
                }
                return Build.TAGS != null && Build.TAGS.contains("test-keys")
            }
            return false
        }

    val isVpn: Boolean
        get() {
            runCatching {
                val e = NetworkInterface.getNetworkInterfaces()
                if (e != null) {
                    for (net in Collections.list(e)) {
                        if (net.isUp && !net.interfaceAddresses.isEmpty() && ("tun0" == net.name || "ppp0" == net.name)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

    fun isSim(context: Context): Boolean {
        runCatching {
            val state = (context.getSystemService(Activity.TELEPHONY_SERVICE) as TelephonyManager).simState
            return TelephonyManager.SIM_STATE_ABSENT != state && state != TelephonyManager.SIM_STATE_UNKNOWN
        }
        return true
    }

    fun getInstaller(context: Context): String {
        var installer = ""
        runCatching {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installer = packageManager.getInstallSourceInfo(context.packageName).installingPackageName!!
            } else {
                installer = packageManager.getInstallerPackageName(context.packageName)!!
            }
        }
        return installer ?: ""
    }

    private fun existFile(files: Array<String>): Boolean {
        for (file in files) {
            if (File(file).exists()) {
                return true
            }
        }
        return false
    }

}

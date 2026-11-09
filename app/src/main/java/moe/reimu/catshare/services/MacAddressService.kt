package moe.reimu.catshare.services

import android.util.Log
import moe.reimu.catshare.IMacAddressService
import moe.reimu.catshare.utils.TAG
import java.net.NetworkInterface
import kotlin.system.exitProcess

class MacAddressService: IMacAddressService.Stub() {

    override fun destroy() {
        exit()
    }

    override fun exit() {
        Log.i(TAG, "Exit")
        exitProcess(0)
    }

    override fun getP2pMacAddress(): String? {
        // 优先通过 Root 实时读取 p2p 接口 MAC，失败则回退到原有 NetworkInterface 方式
        getMacAddressViaRoot()?.let { return it }
        return getMacAddressByName("p2p0")
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getMacAddressByName(name: String): String? {
        val ifs = NetworkInterface.getNetworkInterfaces()
        for (intf in ifs) {
            if (intf.name == name) {
                return intf.hardwareAddress?.toHexString(HexFormat { bytes.byteSeparator = ":" })
            }
        }
        return null
    }

    /**
     * 通过 su 实时读取 /sys/class/net/<iface>/address。
     * 不做任何缓存，每次调用都会重新执行，保证拿到当前时刻的 p2p MAC。
     */
    private fun getMacAddressViaRoot(): String? {
        val interfaces = listOf("p2p0", "p2p1", "p2p-dev-wlan0")
        for (iface in interfaces) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "cat /sys/class/net/$iface/address")
                )
                val mac = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (mac.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) {
                    Log.i(TAG, "Root MAC ($iface): $mac")
                    return mac.uppercase()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Root read MAC for $iface failed: ${e.message}")
            }
        }
        return null
    }
}

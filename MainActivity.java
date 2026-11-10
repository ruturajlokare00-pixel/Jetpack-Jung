package com.jetpackjung.lan;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private WebView web;
    private LanServer server;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new LanBridge(), "JetpackLAN");
        setContentView(web);
        web.loadUrl("file:///android_asset/game/index.html");
    }

    private final class LanBridge {
        @JavascriptInterface public synchronized String startHost() {
            try {
                if (server == null || !server.isRunning()) {
                    server = new LanServer();
                    server.setRoomCode(Long.toHexString(System.nanoTime()).substring(0, 6).toUpperCase());
                    server.start();
                }
                return "ws://" + localIPv4() + ":" + LanServer.PORT;
            } catch (Exception e) { return "ERROR:" + e.getMessage(); }
        }
        @JavascriptInterface public String getLocalAddress() { return localIPv4(); }
        @JavascriptInterface public synchronized String getRoomCode() {
            return server == null ? "" : server.getRoomCode();
        }
        @JavascriptInterface public synchronized int getPlayerCount() {
            return server == null ? 0 : server.playerCount();
        }
        @JavascriptInterface public synchronized void broadcastPacket(String packet) {
            if (server != null && packet != null && packet.length() <= 8192) server.sendToPeers(packet);
        }
        @JavascriptInterface public synchronized String pollHostPacket() {
            if (server == null) return "";
            String packet = server.pollHostPacket();
            return packet == null ? "" : packet;
        }
        @JavascriptInterface public synchronized void stopHost() {
            try { if (server != null) { server.stop(500); server = null; } } catch (Exception ignored) { }
        }
    }

    private String localIPv4() {
        try {
            java.util.List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            interfaces.sort((a, b) -> Integer.compare(wifiRank(a.getName()), wifiRank(b.getName())));
            for (NetworkInterface ni : interfaces) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        if (!ip.startsWith("169.254.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }

    private int wifiRank(String name) {
        String n = name.toLowerCase();
        if (n.equals("wlan0") || n.startsWith("ap")) return 0;
        if (n.contains("wifi") || n.contains("p2p")) return 1;
        return 5;
    }

    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        try { if (server != null) server.stop(500); } catch (Exception ignored) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}

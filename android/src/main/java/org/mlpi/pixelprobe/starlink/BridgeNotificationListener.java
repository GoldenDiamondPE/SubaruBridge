package org.mlpi.pixelprobe.starlink;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only explicitly enabled categories are retained; never written to diagnostics. */
public final class BridgeNotificationListener extends NotificationListenerService {
    private static volatile BridgeNotificationListener connected;
    @Override public void onListenerConnected() {
        connected=this;
        refresh();
    }
    static void refresh() {
        BridgeNotificationListener service=connected;if(service==null)return;
        PhoneIntegrations integrations = PhoneIntegrations.get(service);
        try { integrations.replaceNotifications(service.getActiveNotifications()); }
        catch(RuntimeException unavailable) { integrations.clearNotifications(); }
    }
    @Override public void onNotificationPosted(StatusBarNotification notification) {
        try { PhoneIntegrations.get(this).posted(notification); }
        catch(RuntimeException malformed) { PhoneIntegrations.get(this).removed(notification.getKey()); }
    }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { PhoneIntegrations.get(this).removed(notification.getKey()); }
    @Override public void onListenerDisconnected() { if(connected==this)connected=null;PhoneIntegrations.get(this).clearNotifications(); }
}

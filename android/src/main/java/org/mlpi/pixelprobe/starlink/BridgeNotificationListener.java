package org.mlpi.pixelprobe.starlink;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only explicitly enabled categories are retained; never written to diagnostics. */
public final class BridgeNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        PhoneIntegrations integrations = PhoneIntegrations.get(this);
        integrations.clearNotifications();
        try { StatusBarNotification[] active=getActiveNotifications();if(active!=null)for(StatusBarNotification notification:active) integrations.posted(notification); }
        catch(RuntimeException unavailable) { integrations.clearNotifications(); }
    }
    @Override public void onNotificationPosted(StatusBarNotification notification) {
        try { PhoneIntegrations.get(this).posted(notification); }
        catch(RuntimeException malformed) { PhoneIntegrations.get(this).removed(notification.getKey()); }
    }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { PhoneIntegrations.get(this).removed(notification.getKey()); }
    @Override public void onListenerDisconnected() { PhoneIntegrations.get(this).clearNotifications(); }
}

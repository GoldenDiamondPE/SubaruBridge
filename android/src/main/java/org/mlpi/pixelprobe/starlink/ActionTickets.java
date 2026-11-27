package org.mlpi.pixelprobe.starlink;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, action-bound, one-use grants. A refreshed form cannot replay a command. */
final class ActionTickets {
    private static final long TTL = 5 * 60 * 1000L;
    private final Map<String, Grant> grants = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final class Grant {
        final String action, subject; final long expires;
        Grant(String action, String subject, long expires) { this.action=action; this.subject=subject; this.expires=expires; }
    }
    synchronized String issue(String action, String subject, long now) {
        grants.entrySet().removeIf(e -> e.getValue().expires <= now);
        while(grants.size() >= 128) grants.remove(grants.keySet().iterator().next());
        byte[] bytes = new byte[24]; random.nextBytes(bytes);
        StringBuilder token = new StringBuilder(); for(byte b:bytes) token.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        grants.put(token.toString(), new Grant(action,subject,now+TTL)); return token.toString();
    }
    synchronized boolean consume(String token, String action, String subject, long now) {
        Grant grant = grants.get(token);
        if(grant==null || grant.expires<=now || !grant.action.equals(action) || !grant.subject.equals(subject)) return false;
        grants.remove(token); return true;
    }
}

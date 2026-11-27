package org.mlpi.pixelprobe.starlink;

import java.util.*;

/** Provider-neutral read model. Controls are requests, not proof of playback or audio routing. */
final class MediaSnapshot {
    final String target,source,title,artist,album,state;
    final long durationMs,positionMs;
    final boolean truncated;
    final Set<String> commands;
    MediaSnapshot(String target,String source,String title,String artist,String album,String state,
                  long durationMs,long positionMs,boolean truncated,Collection<String> commands) {
        this.target=target;this.source=PhoneIntegrations.clean(source,160);this.title=PhoneIntegrations.clean(title,160);
        this.artist=PhoneIntegrations.clean(artist,160);this.album=PhoneIntegrations.clean(album,160);this.state=state;
        this.durationMs=durationMs;this.positionMs=positionMs;this.truncated=truncated;
        this.commands=Collections.unmodifiableSet(new HashSet<>(commands));
    }
}

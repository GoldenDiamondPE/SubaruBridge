package org.mlpi.pixelprobe.starlink;

/** Permission prompts and service starts are deliberate, finite transitions. */
public final class StartupPolicy {
    public enum Need { READY, NEARBY_PERMISSION, BLUETOOTH_OFF, NO_BLUETOOTH, CORE_UNAVAILABLE }
    public enum Action { NONE, ASK_PERMISSION, ASK_BLUETOOTH, START }
    private StartupPolicy() {}

    public static Action automatic(Need need, boolean enabled, boolean paused,
            boolean alreadyPrompted, boolean inFlight, boolean running) {
        if (!enabled || paused || inFlight || running) return Action.NONE;
        switch (need) {
            case NEARBY_PERMISSION: return alreadyPrompted ? Action.NONE : Action.ASK_PERMISSION;
            case BLUETOOTH_OFF: return alreadyPrompted ? Action.NONE : Action.ASK_BLUETOOTH;
            case READY: return Action.START;
            default: return Action.NONE;
        }
    }
}

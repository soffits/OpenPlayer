package dev.soffits.openplayer.runtime.action;

public enum ActionLifecycleState {
    IDLE,
    PLANNING,
    EXECUTING,
    PAUSED,
    RETRYING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
}

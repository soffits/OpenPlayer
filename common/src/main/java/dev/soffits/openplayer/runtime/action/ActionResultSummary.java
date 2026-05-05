package dev.soffits.openplayer.runtime.action;

public record ActionResultSummary(boolean success, String detail) {
    public ActionResultSummary {
        detail = detail == null || detail.isBlank() ? "none" : detail.trim();
    }
}

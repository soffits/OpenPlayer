package dev.soffits.openplayer.client;

public final class OpenPlayerControlLayoutTest {
    private OpenPlayerControlLayoutTest() {
    }

    public static void main(String[] args) {
        keepsGalleryUsableAtDefaultScaledHeight();
        keepsRightColumnUsableAtDefaultScaledWidth();
        keepsMainDetailsClearOfFirstActionRow();
        keepsMainControlsVisibleAtDefaultScaledHeight();
        keepsProfileControlsVisibleAtDefaultScaledHeight();
        keepsProviderStatusBelowTestButtonAtDefaultScaledSize();
        clampsVerySmallHeightsToMinimumGalleryRows();
    }

    private static void keepsGalleryUsableAtDefaultScaledHeight() {
        int visibleAssignments = OpenPlayerControlLayout.visibleAssignmentCount(360);
        require(visibleAssignments == 6, "640x360 GUI should show the full six-row gallery page");
        int pagerBottom = 58 + visibleAssignments * 24 + 2 + OpenPlayerControlLayout.BUTTON_HEIGHT;
        require(pagerBottom <= 224, "gallery pager must stay above lower status/message area");
    }

    private static void keepsRightColumnUsableAtDefaultScaledWidth() {
        OpenPlayerControlLayout.Columns columns = OpenPlayerControlLayout.columns(640);
        require(columns.listWidth() == 210, "default scaled 1280x720 width should keep the full gallery width");
        require(columns.rightLeft() == 234, "right column should start after the gallery gap");
        require(columns.rightWidth() == 394, "right column should have enough width for two action columns");
        int buttonWidth = Math.min(142, Math.max(58, (columns.rightWidth() - OpenPlayerControlLayout.BUTTON_SPACING) / 2));
        int buttonsWidth = buttonWidth * 2 + OpenPlayerControlLayout.BUTTON_SPACING;
        require(buttonsWidth <= columns.rightWidth(), "two action columns must fit inside the right column");
    }

    private static void clampsVerySmallHeightsToMinimumGalleryRows() {
        require(OpenPlayerControlLayout.visibleAssignmentCount(220) == 3, "small GUI heights keep at least three gallery rows");
    }

    private static void keepsMainDetailsClearOfFirstActionRow() {
        int firstActionTop = OpenPlayerControlLayout.mainActionRowTop();
        require(OpenPlayerControlLayout.mainSelectedDetailBottom() < firstActionTop, "selected detail text must end before first action row");
        require(OpenPlayerControlLayout.mainDefaultDetailBottom() < firstActionTop, "default detail text must end before first action row");
    }

    private static void keepsMainControlsVisibleAtDefaultScaledHeight() {
        int screenHeight = 360;
        int safeBottom = screenHeight - OpenPlayerControlLayout.MAIN_SAFE_BOTTOM_MARGIN;
        require(OpenPlayerControlLayout.mainControlsBottom() <= safeBottom, "main controls must fit inside 640x360 GUI height with safe margin");
    }

    private static void keepsProfileControlsVisibleAtDefaultScaledHeight() {
        int screenHeight = 360;
        int safeBottom = screenHeight - OpenPlayerControlLayout.MAIN_SAFE_BOTTOM_MARGIN;
        require(OpenPlayerControlLayout.profileControlsBottom() <= safeBottom, "profile controls must fit inside 640x360 GUI height with safe margin");
        require(OpenPlayerControlLayout.profileStatusBottom() <= safeBottom, "profile status text must fit inside 640x360 GUI height with safe margin");
    }

    private static void keepsProviderStatusBelowTestButtonAtDefaultScaledSize() {
        int screenHeight = 360;
        int safeBottom = screenHeight - OpenPlayerControlLayout.MAIN_SAFE_BOTTOM_MARGIN;
        require(OpenPlayerControlLayout.providerTestButtonBottom() + OpenPlayerControlLayout.BUTTON_SPACING
                        < OpenPlayerControlLayout.PROVIDER_STATUS_TOP,
                "provider test status must not overlap the Test Provider button");
        require(OpenPlayerControlLayout.providerTextBottom() <= safeBottom,
                "provider status and notes must fit inside 640x360 GUI height with safe margin");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

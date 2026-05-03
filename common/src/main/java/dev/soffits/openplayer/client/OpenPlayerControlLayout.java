package dev.soffits.openplayer.client;

public final class OpenPlayerControlLayout {
    public static final int MARGIN = 12;
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_SPACING = 6;
    public static final int MIN_VISIBLE_ASSIGNMENTS = 3;
    public static final int MAX_VISIBLE_ASSIGNMENTS = 6;
    public static final int PAGE_TOP = 70;
    public static final int TEXT_LINE_HEIGHT = 9;
    public static final int MAIN_SAFE_BOTTOM_MARGIN = 12;
    private static final int MAIN_DETAIL_LINE_SPACING = 14;
    private static final int MAIN_SELECTED_DETAIL_LINES = 6;
    private static final int MAIN_DEFAULT_DETAIL_LINES = 4;
    private static final int MAIN_DETAIL_ACTION_GAP = 5;
    private static final int MAIN_ACTION_ROW_OFFSET = 84;
    public static final int PROFILE_ROW_TOP = PAGE_TOP + 14;
    public static final int PROFILE_ROW_STEP = BUTTON_HEIGHT + 4;
    public static final int PROFILE_DEFAULT_NOTE_TOP = PAGE_TOP + 254;
    public static final int PROFILE_STATUS_TOP = PAGE_TOP + 268;
    public static final int PROVIDER_ROW_TOP = PAGE_TOP + 16;
    public static final int PROVIDER_ROW_STEP = BUTTON_HEIGHT + BUTTON_SPACING;
    public static final int PROVIDER_TEST_BUTTON_ROW = 5;
    public static final int PROVIDER_STATUS_TOP = PROVIDER_ROW_TOP + PROVIDER_ROW_STEP * PROVIDER_TEST_BUTTON_ROW + BUTTON_HEIGHT + 12;
    public static final int PROVIDER_NOTE_TOP = PROVIDER_STATUS_TOP + 16;
    private static final int PROFILE_LAST_CONTROL_ROW = 9;

    private OpenPlayerControlLayout() {
    }

    public static Columns columns(int screenWidth) {
        int listWidth = Math.min(210, Math.max(124, screenWidth / 3));
        int rightLeft = MARGIN + listWidth + 12;
        int rightWidth = Math.max(120, screenWidth - rightLeft - MARGIN);
        return new Columns(listWidth, rightLeft, rightWidth);
    }

    public static int visibleAssignmentCount(int screenHeight) {
        int availableHeight = screenHeight - 150;
        int byHeight = availableHeight <= 0 ? MIN_VISIBLE_ASSIGNMENTS : availableHeight / (BUTTON_HEIGHT + 4);
        return Math.max(MIN_VISIBLE_ASSIGNMENTS, Math.min(MAX_VISIBLE_ASSIGNMENTS, byHeight));
    }

    public static int centeredLeft(int containerLeft, int containerWidth, int itemWidth) {
        return containerLeft + Math.max(0, (containerWidth - itemWidth) / 2);
    }

    public static int clampedControlWidth(int rightWidth, int requestedWidth) {
        return Math.min(requestedWidth, rightWidth);
    }

    public static int mainActionRowTop() {
        return Math.max(PAGE_TOP + MAIN_ACTION_ROW_OFFSET, mainSelectedDetailBottom() + MAIN_DETAIL_ACTION_GAP);
    }

    public static int mainSelectedDetailBottom() {
        return mainDetailBottom(MAIN_SELECTED_DETAIL_LINES);
    }

    public static int mainDefaultDetailBottom() {
        return mainDetailBottom(MAIN_DEFAULT_DETAIL_LINES);
    }

    public static int mainControlsBottom() {
        int rowStep = BUTTON_HEIGHT + BUTTON_SPACING;
        return mainActionRowTop() + rowStep * 4 + BUTTON_HEIGHT;
    }

    public static int profileControlsBottom() {
        return PROFILE_ROW_TOP + PROFILE_ROW_STEP * PROFILE_LAST_CONTROL_ROW + BUTTON_HEIGHT;
    }

    public static int profileStatusBottom() {
        return PROFILE_STATUS_TOP + TEXT_LINE_HEIGHT;
    }

    public static int providerTestButtonBottom() {
        return PROVIDER_ROW_TOP + PROVIDER_ROW_STEP * PROVIDER_TEST_BUTTON_ROW + BUTTON_HEIGHT;
    }

    public static int providerTextBottom() {
        return PROVIDER_NOTE_TOP + TEXT_LINE_HEIGHT * 2 + 14;
    }

    private static int mainDetailBottom(int lineCount) {
        return PAGE_TOP + (lineCount - 1) * MAIN_DETAIL_LINE_SPACING + TEXT_LINE_HEIGHT;
    }

    public static final class Columns {
        private final int listWidth;
        private final int rightLeft;
        private final int rightWidth;

        private Columns(int listWidth, int rightLeft, int rightWidth) {
            this.listWidth = listWidth;
            this.rightLeft = rightLeft;
            this.rightWidth = rightWidth;
        }

        public int listWidth() {
            return listWidth;
        }

        public int rightLeft() {
            return rightLeft;
        }

        public int rightWidth() {
            return rightWidth;
        }
    }
}

package dev.soffits.openplayer.client;

public final class OpenPlayerGalleryPageTest {
    private OpenPlayerGalleryPageTest() {
    }

    public static void main(String[] args) {
        clampsEmptyAndInvalidPageSizes();
        pagesMoreThanSixAssignments();
        reportsNavigationState();
        mapsSelectedAssignmentIndexToStablePage();
    }

    private static void clampsEmptyAndInvalidPageSizes() {
        OpenPlayerGalleryPage page = OpenPlayerGalleryPage.of(0, 0, 5);
        require(page.pageIndex() == 0, "empty pages must clamp to page zero");
        require(page.pageSize() == 1, "invalid page sizes must clamp to one");
        require(page.pageCount() == 0, "empty galleries must have no pages");
        require(page.firstIndex() == 0, "empty first index must be zero");
        require(page.lastExclusiveIndex() == 0, "empty last index must be zero");
        require(page.displayPageIndex() == 0, "empty display page must be explicit");
    }

    private static void pagesMoreThanSixAssignments() {
        OpenPlayerGalleryPage first = OpenPlayerGalleryPage.of(7, 6, 0);
        require(first.pageCount() == 2, "seven assignments at six per page need two pages");
        require(first.firstIndex() == 0, "first page starts at zero");
        require(first.lastExclusiveIndex() == 6, "first page includes six rows");
        OpenPlayerGalleryPage second = OpenPlayerGalleryPage.of(7, 6, 1);
        require(second.firstIndex() == 6, "second page starts at item six");
        require(second.lastExclusiveIndex() == 7, "second page contains the remaining row");
    }

    private static void reportsNavigationState() {
        OpenPlayerGalleryPage first = OpenPlayerGalleryPage.of(12, 6, -1);
        require(!first.hasPrevious(), "first page must not have previous navigation");
        require(first.hasNext(), "first page must have next navigation");
        OpenPlayerGalleryPage last = OpenPlayerGalleryPage.of(12, 6, 99);
        require(last.pageIndex() == 1, "requested page must clamp to last page");
        require(last.hasPrevious(), "last page must have previous navigation");
        require(!last.hasNext(), "last page must not have next navigation");
        require(last.displayPageIndex() == 2, "non-empty display pages are one-based");
    }

    private static void mapsSelectedAssignmentIndexToStablePage() {
        require(OpenPlayerGalleryPage.pageForItemIndex(0, 6) == 0, "first item must be on first page");
        require(OpenPlayerGalleryPage.pageForItemIndex(5, 6) == 0, "sixth item must be on first page");
        require(OpenPlayerGalleryPage.pageForItemIndex(6, 6) == 1, "seventh item must be on second page");
        require(OpenPlayerGalleryPage.pageForItemIndex(-1, 6) == 0, "missing selection must map to first page");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

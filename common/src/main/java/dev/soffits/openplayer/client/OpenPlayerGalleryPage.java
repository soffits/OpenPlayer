package dev.soffits.openplayer.client;

public final class OpenPlayerGalleryPage {
    private final int totalItems;
    private final int pageSize;
    private final int pageIndex;

    private OpenPlayerGalleryPage(int totalItems, int pageSize, int pageIndex) {
        this.totalItems = Math.max(0, totalItems);
        this.pageSize = Math.max(1, pageSize);
        this.pageIndex = clampPageIndex(pageIndex, pageCount(this.totalItems, this.pageSize));
    }

    public static OpenPlayerGalleryPage of(int totalItems, int pageSize, int requestedPageIndex) {
        return new OpenPlayerGalleryPage(totalItems, pageSize, requestedPageIndex);
    }

    public static int pageForItemIndex(int itemIndex, int pageSize) {
        if (itemIndex < 0) {
            return 0;
        }
        return itemIndex / Math.max(1, pageSize);
    }

    public int totalItems() {
        return totalItems;
    }

    public int pageSize() {
        return pageSize;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int pageCount() {
        return pageCount(totalItems, pageSize);
    }

    public int firstIndex() {
        return pageIndex * pageSize;
    }

    public int lastExclusiveIndex() {
        return Math.min(totalItems, firstIndex() + pageSize);
    }

    public boolean hasPrevious() {
        return pageIndex > 0;
    }

    public boolean hasNext() {
        return pageIndex + 1 < pageCount();
    }

    public String label() {
        if (totalItems == 0) {
            return "Page 0/0";
        }
        return "Page " + (pageIndex + 1) + "/" + pageCount();
    }

    private static int pageCount(int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 0;
        }
        return (totalItems + Math.max(1, pageSize) - 1) / Math.max(1, pageSize);
    }

    private static int clampPageIndex(int requestedPageIndex, int pageCount) {
        if (pageCount <= 0 || requestedPageIndex <= 0) {
            return 0;
        }
        return Math.min(requestedPageIndex, pageCount - 1);
    }
}

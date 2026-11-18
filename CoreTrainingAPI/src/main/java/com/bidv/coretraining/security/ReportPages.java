package com.bidv.coretraining.security;

import java.util.*;

/**
 * The permission pages of the "Thống kê & báo cáo" menu. "overview" is the dashboard; every report view of the
 * menu has its own page, so a role can be given some report views and not others. The completion view is opened
 * from the dashboard charts and belongs to it.
 */
public final class ReportPages {
    private ReportPages() {
    }

    public static final String OVERVIEW = "overview";

    /** Report view id (the {@code report} query of /admin and the {@code view} parameter of the API) to page id. */
    public static final Map<String, String> VIEW_PAGES = Map.of(
            "learners", "report-learners",
            "courses", "report-courses",
            "programs", "report-programs",
            "units", "report-units",
            "scores", "report-scores",
            "domains", "report-domains");

    /** Page id, label and sort order of each report view page, in menu order. */
    public static final List<String[]> VIEW_PAGE_DEFINITIONS = List.of(
            new String[] { "learners", "Báo cáo · Học viên", "11" },
            new String[] { "courses", "Báo cáo · Khóa học", "12" },
            new String[] { "programs", "Báo cáo · Chương trình", "13" },
            new String[] { "units", "Báo cáo · Đơn vị", "14" },
            new String[] { "scores", "Báo cáo · Kết quả kiểm tra", "15" },
            new String[] { "domains", "Báo cáo · Lĩnh vực", "16" });

    /** Read permission of every report page. */
    public static final Set<String> READ = readPermissions();

    private static Set<String> readPermissions() {
        Set<String> result = new HashSet<>();
        result.add(OVERVIEW + ":read");
        VIEW_PAGES.values().forEach(page -> result.add(page + ":read"));
        return Set.copyOf(result);
    }

    /** The page that guards a report view; unknown views and the dashboard's completion detail fall to overview. */
    public static String pageOf(String view) {
        return view == null ? OVERVIEW : VIEW_PAGES.getOrDefault(view, OVERVIEW);
    }

    /** Course ratings are shown on the dashboard and in the course report. */
    public static final Set<String> RATINGS = Set.of(OVERVIEW + ":read", VIEW_PAGES.get("courses") + ":read");
}

package org.example.flow.constant;

public final class ProfileConst {

    // ── gender（与 ClickHouse Enum8('男'=1,'女'=2) 对齐）
    public static final int GENDER_MALE   = 1;
    public static final int GENDER_FEMALE = 2;
    public static final String GENDER_MALE_LABEL   = "男";
    public static final String GENDER_FEMALE_LABEL = "女";

    // ── is_resident
    public static final int RESIDENT     = 1;
    public static final int NON_RESIDENT = 0;

    // ── age_group 档位标签（顺序与 ageGroup() 输出一致）
    public static final String[] AGE_GROUP_LABELS = {
        "<18", "18-25", "26-35", "36-45", "46-60", "60+"
    };

    // ── 数据生成默认分布参数（运行时实际参数来自 application.yml）
    public static final int    DEFAULT_USER_COUNT     = 10000;
    public static final double DEFAULT_MALE_RATIO     = 0.55;
    public static final double DEFAULT_RESIDENT_RATIO = 0.60;
    public static final double[] DEFAULT_AGE_WEIGHTS  = {0.05, 0.15, 0.25, 0.25, 0.20, 0.10};

    private ProfileConst() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * 将年龄映射到固定 6 档年龄组标签。
     * 边界：age&lt;18 → "&lt;18"；18-25；26-35；36-45；46-60；age&gt;60 → "60+"
     */
    public static String ageGroup(int age) {
        if (age < 18) return "<18";
        if (age <= 25) return "18-25";
        if (age <= 35) return "26-35";
        if (age <= 45) return "36-45";
        if (age <= 60) return "46-60";
        return "60+";
    }
}

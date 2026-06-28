package org.example.flow.datagen;

import org.example.flow.constant.ProfileConst;
import org.example.flow.constant.RegionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DataGenerator {

    private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);

    private static final String CITY_CODE = "310000";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int[] AGE_LO = {1, 18, 26, 36, 46, 61};
    private static final int[] AGE_HI = {17, 25, 35, 45, 60, 90};

    private static final String SQL_INSERT_PROFILE =
            "INSERT INTO flow.dim_user_profile " +
            "(imsi, gender, age, age_group, is_resident, home_region_id) VALUES (?,?,?,?,?,?)";

    private static final String SQL_INSERT_DWD =
            "INSERT INTO flow.dwd_cell_imsi_5min " +
            "(stat_time, stat_date, city_code, lac, cell_id, region_id, imsi) VALUES (?,?,?,?,?,?,?)";

    private static final String SQL_DERIVE_HOUR =
            "INSERT INTO flow.dws_user_window_loc " +
            "SELECT imsi, 'hour' AS granularity, toStartOfHour(stat_time) AS window_start, " +
            "argMax(region_id, stat_time) AS region_id " +
            "FROM flow.dwd_cell_imsi_5min GROUP BY imsi, window_start";

    private static final String SQL_DERIVE_DAY =
            "INSERT INTO flow.dws_user_window_loc " +
            "SELECT imsi, 'day' AS granularity, toStartOfDay(stat_time) AS window_start, " +
            "argMax(region_id, stat_time) AS region_id " +
            "FROM flow.dwd_cell_imsi_5min GROUP BY imsi, window_start";

    private final DataSource dataSource;

    @Value("${app.datagen.seed}")
    private long seed;
    @Value("${app.datagen.user-count}")
    private int userCount;
    @Value("${app.datagen.start}")
    private String startStr;
    @Value("${app.datagen.end}")
    private String endStr;
    @Value("${app.datagen.stay-prob}")
    private double stayProb;
    @Value("${app.datagen.present-prob}")
    private double presentProb;
    @Value("${app.datagen.batch-size}")
    private int batchSize;

    public DataGenerator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public GenResult generate() {
        long startMs = System.currentTimeMillis();
        Random rng = new Random(seed);

        List<String> regions = RegionDef.allRegions();
        int regionCount = regions.size();
        String[] regionLac = new String[regionCount];
        String[][] regionCells = new String[regionCount][];
        for (int r = 0; r < regionCount; r++) {
            String regionId = regions.get(r);
            regionLac[r] = RegionDef.lacOfRegion(regionId);
            regionCells[r] = RegionDef.cellsOfRegion(regionId).toArray(new String[0]);
        }

        String[] imsis = new String[userCount];
        for (int i = 0; i < userCount; i++) {
            imsis[i] = md5("user_" + i);
        }

        List<String> sliceTs = new ArrayList<>();
        List<String> sliceDate = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse(startStr);
        LocalDateTime end = LocalDateTime.parse(endStr);
        for (LocalDateTime t = start; t.isBefore(end); t = t.plusMinutes(5)) {
            sliceTs.add(t.format(TS_FMT));
            sliceDate.add(t.format(DATE_FMT));
        }
        int sliceCount = sliceTs.size();

        try (Connection conn = dataSource.getConnection()) {
            truncate(conn);

            int[] homeRegion = new int[userCount];
            long profileRows = insertProfiles(conn, rng, imsis, regionCount, homeRegion);
            long dwdRows = insertTrajectory(conn, rng, imsis, homeRegion,
                    regionCount, regionLac, regionCells, sliceTs, sliceDate, sliceCount);

            execute(conn, SQL_DERIVE_HOUR);
            execute(conn, SQL_DERIVE_DAY);

            long dwsHourRows = countWhere(conn, "granularity='hour'");
            long dwsDayRows = countWhere(conn, "granularity='day'");

            long elapsed = System.currentTimeMillis() - startMs;
            return new GenResult(seed, userCount, profileRows, dwdRows,
                    dwsHourRows, dwsDayRows, elapsed);
        } catch (SQLException e) {
            throw new IllegalStateException("数据生成失败: " + e.getMessage(), e);
        }
    }

    private void truncate(Connection conn) throws SQLException {
        execute(conn, "TRUNCATE TABLE flow.dwd_cell_imsi_5min");
        execute(conn, "TRUNCATE TABLE flow.dim_user_profile");
        execute(conn, "TRUNCATE TABLE flow.dws_user_window_loc");
    }

    private long insertProfiles(Connection conn, Random rng, String[] imsis,
                                int regionCount, int[] homeRegion) throws SQLException {
        double[] ageWeights = ProfileConst.DEFAULT_AGE_WEIGHTS;
        long rows = 0;
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_PROFILE)) {
            int pending = 0;
            for (int i = 0; i < imsis.length; i++) {
                String genderLabel = rng.nextDouble() < ProfileConst.DEFAULT_MALE_RATIO
                        ? ProfileConst.GENDER_MALE_LABEL : ProfileConst.GENDER_FEMALE_LABEL;

                double u = rng.nextDouble();
                double acc = 0;
                int bucket = ageWeights.length - 1;
                for (int b = 0; b < ageWeights.length; b++) {
                    acc += ageWeights[b];
                    if (u < acc) {
                        bucket = b;
                        break;
                    }
                }
                int age = AGE_LO[bucket] + rng.nextInt(AGE_HI[bucket] - AGE_LO[bucket] + 1);

                int isResident = rng.nextDouble() < ProfileConst.DEFAULT_RESIDENT_RATIO
                        ? ProfileConst.RESIDENT : ProfileConst.NON_RESIDENT;
                int home = rng.nextInt(regionCount);
                homeRegion[i] = home;

                ps.setString(1, imsis[i]);
                ps.setString(2, genderLabel);
                ps.setInt(3, age);
                ps.setString(4, ProfileConst.ageGroup(age));
                ps.setInt(5, isResident);
                ps.setString(6, RegionDef.allRegions().get(home));
                ps.addBatch();
                rows++;
                if (++pending >= batchSize) {
                    ps.executeBatch();
                    ps.clearBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                ps.executeBatch();
                ps.clearBatch();
            }
        }
        log.info("dim_user_profile 写入完成: {} 行", rows);
        return rows;
    }

    private long insertTrajectory(Connection conn, Random rng, String[] imsis, int[] homeRegion,
                                  int regionCount, String[] regionLac, String[][] regionCells,
                                  List<String> sliceTs, List<String> sliceDate, int sliceCount)
            throws SQLException {
        List<String> regions = RegionDef.allRegions();
        long rows = 0;
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_DWD)) {
            int pending = 0;
            for (int i = 0; i < imsis.length; i++) {
                String imsi = imsis[i];
                int cur = homeRegion[i];
                for (int s = 0; s < sliceCount; s++) {
                    if (rng.nextDouble() >= presentProb) {
                        continue;
                    }
                    if (rng.nextDouble() >= stayProb) {
                        int offset = 1 + rng.nextInt(regionCount - 1);
                        cur = (cur + offset) % regionCount;
                    }
                    String[] cells = regionCells[cur];
                    String cellId = cells[rng.nextInt(cells.length)];

                    ps.setString(1, sliceTs.get(s));
                    ps.setString(2, sliceDate.get(s));
                    ps.setString(3, CITY_CODE);
                    ps.setString(4, regionLac[cur]);
                    ps.setString(5, cellId);
                    ps.setString(6, regions.get(cur));
                    ps.setString(7, imsi);
                    ps.addBatch();
                    rows++;
                    if (++pending >= batchSize) {
                        ps.executeBatch();
                        ps.clearBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                ps.executeBatch();
                ps.clearBatch();
            }
        }
        log.info("dwd_cell_imsi_5min 写入完成: {} 行", rows);
        return rows;
    }

    private void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private long countWhere(Connection conn, String where) throws SQLException {
        String sql = "SELECT count() FROM flow.dws_user_window_loc WHERE " + where;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}

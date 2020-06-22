import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GTFS_ToBussTUCTest {
    String data_path;
    String glob_pattern;

    @BeforeEach
    void setUp() {
        data_path = "/home/alex/IdeaProjects/GTFS_to_BussTUC/data/";
        glob_pattern = "glob:**/*.txt";
    }

    @AfterEach
    void tearDown() {

    }

    @Test
    void match() throws IOException {
        var result = GTFS_ToBussTUC.match(glob_pattern, data_path);

        assertTrue(result.contains("/home/alex/IdeaProjects/GTFS_to_BussTUC/data/agency.txt"));
    }

    @Test
    void padRight() {
        var result = GTFS_ToBussTUC.padRight("Hello", 5);

        assertEquals("Hello00000", result);
    }

    @Test
    void padLeft() {
        var result = GTFS_ToBussTUC.padLeft("Hello", 5);

        assertEquals("00000Hello", result);
    }

    @Test
    void updateRoutePeriode() {
        try {
            UpdateRoutePeriode.updateRoutePeriod("testing", "r160", "200622", "201212", "routes.pl");
        } catch (Exception e) {
            fail();
        }
    }
}
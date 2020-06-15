/**
 * @author Alexander Erlingsen @since 2020.06.02
 */

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.javatuples.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class GTFS_ToBussTUC {
    // Global time variables and constants
    static LocalDate starting_date = null;
    static final DateTimeFormatter IN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final DateTimeFormatter OUT_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    // Globing pattern for which type of files to find
    static String glob_pattern = "glob:**/*.txt";
    // Input folder to find the files to convert
    static String data_path = "/home/alex/IdeaProjects/GTFS_to_BussTUC/data/";
    // The output folder path... may be absolute or relative
    static String out_folder = "/home/alex/IdeaProjects/GTFS_to_BussTUC/tables/";

    // Will contain list of street endings
    static ArrayList<String> gater;

    public static void main(String[] args) {
        ArrayList<String> files_list = new ArrayList<>();

        try {
            files_list = match(glob_pattern, data_path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File calendar_file = null;
        File calendar_dates_file = null;
        File trips_file = null;
        File stops_file = null;

        try {
            for (String path : files_list) {
                var strings = path.split("/");
                switch (strings[strings.length - 1]) {
                    case "calendar.txt" -> calendar_file = new File(path);
                    case "calendar_dates.txt" -> calendar_dates_file = new File(path);
                    case "trips.txt" -> trips_file = new File(path);
                    case "stops.txt" -> stops_file = new File(path);
                    default -> System.out.println(path + " not used");
                }
            }

        } catch (NullPointerException e) {
            e.printStackTrace();
        }


        CSVFormat csv_format = CSVFormat.EXCEL.withHeader();

        CSVParser parser;
        List<CSVRecord> calendar_csv = null;
        List<CSVRecord> calendar_dates_csv = null;
        List<CSVRecord> trips_csv = null;
        List<CSVRecord> stops_csv = null;

        try {
            assert calendar_file != null;
            parser = CSVParser.parse(calendar_file, Charset.defaultCharset(), csv_format);
            calendar_csv = parser.getRecords();

            assert calendar_dates_file != null;
            parser = CSVParser.parse(calendar_dates_file, Charset.defaultCharset(), csv_format);
            calendar_dates_csv = parser.getRecords();

            assert trips_file != null;
            parser = CSVParser.parse(trips_file, Charset.defaultCharset(), csv_format);
            trips_csv = parser.getRecords();

            assert stops_file != null;
            parser = CSVParser.parse(stops_file, Charset.defaultCharset(), csv_format);
            stops_csv = parser.getRecords();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
        var dko_list = make_regdko_list(calendar_csv, calendar_dates_csv);
        var bus_list = make_regbus_list(trips_csv);
        var comp_and_hpl_list = make_regcomp_and_hpl_list(stops_csv);

        predicate_printer(dko_list, "regdko.pl");
        predicate_printer(bus_list, "regbus.pl");
        predicate_printer(comp_and_hpl_list.getValue0(),"regcomp.pl");
        predicate_printer(comp_and_hpl_list.getValue1(),"reghpl.pl");
    } // main method

    /**
     * A simple method to glob path to all files in a location
     *
     * @param glob     : the pattern to match, must start with glob:
     * @param location : folder location
     * @return : returns a list of all paths to files
     * @throws IOException : might throw exception if not a valid path to location
     */
    static ArrayList<String> match(String glob, String location) throws IOException {
        ArrayList<String> files = new ArrayList<>();

        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(
                glob);

        Files.walkFileTree(Paths.get(location), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path path,
                                             BasicFileAttributes attrs) {
                if (pathMatcher.matches(path)) {
                    files.add(path.toAbsolutePath().toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Methos that parses the content for regbus.pl file
     * @param trips list of records from the trips.txt file
     * @return list of content to print into regbus.pl
     */
    private static ArrayList<String> make_regbus_list(List<CSVRecord> trips) {
        var regbus = new ArrayList<String>();

        for (CSVRecord record: trips) {
            var line = record.get("trip_id").split(":")[2].split("_");
            var bus = "bus("+ line[0] +").";
            var route = "route(bus_"+ line[0] +"_"+ line[2] +","+ line[0] +","+ line[0] +").";

            if (!regbus.contains(bus))
                regbus.add(bus);

            if (!regbus.contains(route))
                regbus.add(route);
        }

        regbus.sort(null);

        return regbus;
    }

    /**
     * Parses the stops.txt file into regcomp.pl and reghpl.pl format
     * @param stops the list of csv records
     * @return tuple of regcomp and reghpl
     */
    private static Pair<ArrayList<String>, ArrayList<String>> make_regcomp_and_hpl_list(List<CSVRecord> stops) {
        gater = new ArrayList<String>();
        gater.add("gata");
        gater.add("gate");
        gater.add("gaten");
        gater.add("gt");
        gater.add("v");
        gater.add("veg");
        gater.add("vegen");
        gater.add("vei");
        gater.add("veien");
        gater.add("vg");
        gater.add("vn");

        var comp_list = new ArrayList<String>();
        var hpl_list = new ArrayList<String>();


        for (CSVRecord record : stops) {
            if (record.get("stop_id").contains("StopPlace"))
                continue;
            setHpl(comp_list, record, hpl_list);
        }

        return Pair.with(comp_list, hpl_list);
    }

    private static ArrayList<String> make_regdep_list() {
        return new ArrayList<>();
    }

    /**
     * Function that parses the calendar.txt and calendar_date.txt to regdko.pl format
     * @param calendar calendar.txt as a list of csv records
     * @param calendar_dates calendar_dates.txt as a list of csv records
     * @return list of strings to be printed in regdko.pl
     */
    private static ArrayList<String> make_regdko_list(List<CSVRecord> calendar, List<CSVRecord> calendar_dates) {
        starting_date = get_next_monday(get_date(calendar.get(0).get("start_date"))); // denotes the first start date it finds will be updated later

        var mask_length = 406; // some long length unlikely to be exceeded in Prolog code unless server auto update fails for a long period of time

        ArrayList<DKO> dko_list = new ArrayList<>();

        // Updates the starting date
        for (CSVRecord record : calendar) {
            var curr_monday = get_next_monday(get_date(record.get("start_date")));
            if (curr_monday.isBefore(starting_date)) {
                starting_date = curr_monday;
            }
        }
        for (CSVRecord record : calendar) {
            var weeks = record.get("monday")+record.get("tuesday")+record.get("wednesday")+record.get("thursday")+record.get("friday")+record.get("saturday")+record.get("sunday");
            StringBuilder days = new StringBuilder();
            var record_starting_monday = get_next_monday(get_date(record.get("start_date")));
            long days_valid = ChronoUnit.DAYS.between(record_starting_monday, get_date(record.get("end_date")));
            long days_befor_validity = ChronoUnit.DAYS.between(starting_date, record_starting_monday);
            // make string of validity
            for (int i = 0; i < days_valid; i++) {
                days.append(weeks.charAt(i % 7));
            }

            days = new StringBuilder(padLeft(days.toString(), (int)days_befor_validity));

            days = new StringBuilder(padRight(days.toString(), (mask_length-days.length())));

            System.out.println();
            dko_list.add(new DKO(record.get("service_id").split(":")[2].replaceAll("[_]",""),record_starting_monday, get_date(record.get("end_date")),weeks, days.toString()));
        }

        for(CSVRecord record : calendar_dates) {
            var temp = new DKO();
            var temp_day_code = record.get("service_id").split(":")[2].replaceAll("[_]","");
            temp.setDay_code(temp_day_code);
            var date = get_date(record.get("date"));
            var day_from_start = ChronoUnit.DAYS.between(starting_date, date);
            if (dko_list.contains(temp)) {
                var other_dko = dko_list.get(dko_list.indexOf(temp));
                if (record.get("exception_type").equals("2")) {
                    other_dko.setDays(replaceCharUsingCharArray(other_dko.getDays(),'0',(int)day_from_start));
                } else {
                    other_dko.setDays(replaceCharUsingCharArray(other_dko.getDays(),'1',(int)day_from_start));
                    other_dko.setTo(date.plus(1, ChronoUnit.DAYS));
                }
            } else {
                var day_mask = new char[406];
                Arrays.fill(day_mask, '0');
                if (record.get("exception_type").equals("1")) {
                    day_mask[(int)day_from_start] = '1';
                }
                temp.setDays(String.valueOf(day_mask));
                temp.setWeek1("Special");
                temp.setWeek2("Special");
                temp.setWeek3("Special");
                temp.setWeek4("Special");
                temp.setFrom(date.with(DayOfWeek.MONDAY));
                temp.setTo(date.plus(1, ChronoUnit.DAYS));

                dko_list.add(temp);
            }
        }

        ArrayList<String> regdko = new ArrayList<>();

        regdko.add("dkodate("+starting_date.format(OUT_FORMAT)+",1).");
        for (DKO dko : dko_list) {
            regdko.add(dko.toString());
        }
        return regdko;
    }

    private static ArrayList<String> make_regpas_list() {
        return new ArrayList<>();
    }

    private static void predicate_printer(ArrayList<String> predicates, String file) {
        try {
            var out_file = new BufferedWriter(new FileWriter(out_folder + file));

            out_file.write("/* -*- Mode:Prolog; coding:utf-8; -*- */\n");

            for (String predicate : predicates) {
                out_file.write(predicate + "\n");
            }
            out_file.close();
        } catch (IOException e) {
            System.out.println("******** IOException " + e);
            e.printStackTrace();
        }
    }


    /* Helper Functions after this line */


    /**
     * @param date String representation of a date
     * @return returns a LocalDate object of said date
     */
    private static LocalDate get_date(String date) {
        return LocalDate.parse(date, IN_FORMAT);
    }

    /**
     * @param date takes inn any arbitrary LocalDate object
     * @return returns the Monday after said date
     */
    private static LocalDate get_next_monday(LocalDate date) {
        return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /**
     * Pads a string on the right with 0
     * @param s string to be padded
     * @param n amount to pad
     * @return padded string
     */
    public static String padRight(String s, int n) {
        return org.apache.commons.lang3.StringUtils.rightPad(s, n+s.length(), "0");
    }

    /**
     * Pads a string on the left with 0
     * @param s string to be padded
     * @param n amount to pad
     * @return padded string
     */
    public static String padLeft(String s, int n) {
        return org.apache.commons.lang3.StringUtils.leftPad(s, n+s.length(), "0");
    }

    /**
     *
     * @param str string to be edited
     * @param ch character to input
     * @param index where to alter the character
     * @return the new string
     */
    public static String replaceCharUsingCharArray(String str, char ch, int index) {
        char[] chars = str.toCharArray();
        chars[index] = ch;
        return String.valueOf(chars);
    }

    /* regcomp helper functions  used from earlier version of GTFS to BussTUC as they cover a good range of rules, that would be tedious to rewrite*/

    /**
     *
     * @param composite_stat_list list to store in
     * @param record CSV line to pars
     * @param hpl_list HashMap to store stat_ids with stop_id as key
     */
    public static void setHpl(ArrayList<String> composite_stat_list, CSVRecord record, ArrayList<String> hpl_list) {
        String statname = record.get("stop_name") + " " + record.get("platform_code");
        String statid = statname.toLowerCase().replaceAll("/"," ").replaceAll("-"," ").trim().replaceAll(" ", "_");// Is sufficient as UTF-8 has higher support for norwegian characters and some of the characters are never used

        ArrayList<String> composite_stat = new ArrayList<>();
        // update hpl_list
        var hpl = "hpl("+record.get("stop_id").split(":")[2]+", "+statid+", "+statid+","+statname+").";
        if (!hpl_list.contains(hpl)){
            hpl_list.add(hpl);
        }
        StringTokenizer st = new StringTokenizer(statid, "_");
        String nameOne = "";
        if (st.hasMoreTokens())
            nameOne = st.nextToken();
        ArrayList<String> nameTab = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            nameTab.add(st.nextToken());
        }
        composite_stat.add("composite_stat(" + nameOne + "," + nameTab + "," + statid + ").");
        // nameTab members 1..n
        if (nameTab.size() > 0) {
            // last member a road word.
            if (isGate(nameTab.get(nameTab.size() - 1))) { // Street name handling
                if (nameTab.size() == 1) {
                    composite_stat
                            .add("composite_stat(" + nameOne + nameTab.get(0) + ",[]," + statid + "). % generated 1.0");
                    composite_stat.add("composite_stat(" + nameOne + "_street,[]," + statid + "). % generated 1.1");
                    composite_stat.add("composite_stat(" + nameOne + ",[street]," + statid + "). % generated 1.2");
                } else {
                    String last = nameTab.remove(nameTab.size() - 1);
                    String husk = nameTab.get(nameTab.size() - 1);
                    nameTab.set(nameTab.size() - 1, husk + last);
                    composite_stat
                            .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 2.0");
                    nameTab.set(nameTab.size() - 1, husk + "_street");
                    composite_stat
                            .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 2.1");
                    nameTab.set(nameTab.size() - 1, husk);
                    nameTab.add("street");
                    composite_stat
                            .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 2.2");
                }

            } else { // Street name handling // ends with s + road word
                IsGateRec theGateRec = isGateEnds(nameTab.get(nameTab.size() - 1));
                if (theGateRec.bEndswith) {
                    if (nameTab.size() == 1) {
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix);
                        nameTab.add(theGateRec.strSuffix);
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 3.0");
                        nameTab.set(nameTab.size() - 1, "street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 3.2");
                        nameTab.remove(nameTab.size() - 1);
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix + "_street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 3.1");
                    } else {
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix + "_street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 7.0");
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix);
                        nameTab.add(theGateRec.strSuffix);
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 7.1");
                        nameTab.set(nameTab.size() - 1, "street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 7.2");
                    }
                } else { // ends with road word
                    theGateRec = isGateEnd(nameTab.get(nameTab.size() - 1));
                    if (theGateRec.bEndswith) {
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix + "_street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 6.0");
                        nameTab.set(nameTab.size() - 1, theGateRec.strPrefix);
                        nameTab.add(theGateRec.strSuffix);
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 6.1");
                        nameTab.set(nameTab.size() - 1, "street");
                        composite_stat
                                .add("composite_stat(" + nameOne + "," + nameTab + "," + statid + "). % generated 6.2");
                    }
                }
            }
        } else {
            IsGateRec theGateRec = isGateEnds(nameOne);
            if (!(theGateRec.strPrefix.trim().equals(""))) {

                if (theGateRec.bEndswith) {
                    composite_stat.add("composite_stat(" + theGateRec.strPrefix + ",[" + theGateRec.strSuffix + "],"
                            + statid + "). % generated 4.0");
                    composite_stat.add(
                            "composite_stat(" + theGateRec.strPrefix + ",[street]," + statid + "). % generated 4.1");
                    composite_stat.add(
                            "composite_stat(" + theGateRec.strPrefix + "_street,[]," + statid + "). % generated 4.2");
                } else {
                    theGateRec = isGateEnd(nameOne);
                    if (theGateRec.bEndswith) {
                        composite_stat.add("composite_stat(" + theGateRec.strPrefix + ",[" + theGateRec.strSuffix + "],"
                                + statid + "). % generated 5.0");
                        composite_stat.add("composite_stat(" + theGateRec.strPrefix + ",[street]," + statid
                                + "). % generated 5.1");
                        composite_stat.add("composite_stat(" + theGateRec.strPrefix + "_street,[]," + statid
                                + "). % generated 5.2");
                    }
                }
            }

        }

        for (String comp : composite_stat) {
            if (!composite_stat_list.contains(comp)) {
                composite_stat_list.add(comp);
            }
        }
    }

    /**
     * Test if iStr is denoting a street or not.<br>
     */
    public static boolean isGate(String iStr) {
        if (gater.contains(iStr))
            return true;
        return false;
    }

    /**
     * Test if iStr is ending with a string that is denoting a street or not.<br>
     */
    public static IsGateRec isGateEnd(String iStr) {
        if (iStr.endsWith("gata")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 4), "gata", true);
        }
        if (iStr.endsWith("gate")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 4), "gate", true);
        }
        if (iStr.endsWith("gaten")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 5), "gaten", true);
        }
        if (iStr.endsWith("gt")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 2), "gt", true);
        }
        if (iStr.endsWith("v")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 1), "v", true);
        }
        if (iStr.endsWith("veg")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 3), "veg", true);
        }
        if (iStr.endsWith("vegen")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 5), "vegen", true);
        }
        if (iStr.endsWith("vei")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 3), "vei", true);
        }
        if (iStr.endsWith("veien")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 5), "veien", true);
        }
        if (iStr.endsWith("vg")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 2), "vg", true);
        }
        if (iStr.endsWith("vn")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 2), "vn", true);
        }
        return new IsGateRec("", "", false);
    }

    /**
     * Test if iStr is ending with a string that is denoting a street or not.<br>
     */
    public static IsGateRec isGateEnds(String iStr) {

        if (iStr.endsWith("sgata")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 5), "gata", true);
        }
        if (iStr.endsWith("sgate")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 5), "gate", true);
        }
        if (iStr.endsWith("sgaten")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 6), "gaten", true);
        }
        if (iStr.endsWith("sgt")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 3), "gt", true);
        }
        if (iStr.endsWith("sv")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 2), "v", true);
        }
        if (iStr.endsWith("sveg")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 4), "veg", true);
        }
        if (iStr.endsWith("svegen")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 6), "vegen", true);
        }
        if (iStr.endsWith("svei")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 4), "vei", true);
        }
        if (iStr.endsWith("sveien")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 6), "veien", true);
        }
        if (iStr.endsWith("svg")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 3), "vg", true);
        }
        if (iStr.endsWith("svn")) {
            return new IsGateRec(iStr.substring(0, iStr.length() - 3), "vn", true);
        }
        return new IsGateRec("", "", false);
    }
}

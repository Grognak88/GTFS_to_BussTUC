/**
 * @author Alexander Erlingsen @since 2020.06.02
 */

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GTFS_ToBussTUC {
    static LocalDate starting_date = null;
    static final DateTimeFormatter IN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final DateTimeFormatter OUT_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    // Globing pattern for which type of files to find
    static String glob_pattern = "glob:**/*.txt";
    // Input folder to find the files to convert
    static String data_path = "data/";
    // The output folder path... may be absolute or relative
    static String out_folder = "tables/";


    public static void main(String[] args) {
        ArrayList<String> files_list = new ArrayList<>();

        try {
            files_list = match(glob_pattern, data_path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File calendar_file = null;
        File calendar_dates_file = null;

        try {
            for (String path : files_list) {
                var strings = path.split("/");
                switch (strings[strings.length - 1]) {
                    case "calendar.txt" -> calendar_file = new File(path);
                    case "calendar_dates.txt" -> calendar_dates_file = new File(path);
                    default -> System.out.println("Blip Bloop");
                }
            }

        } catch (NullPointerException e) {
            e.printStackTrace();
        }


        CSVFormat csv_format = CSVFormat.EXCEL.withHeader();

        CSVParser calendar_parser;
        List<CSVRecord> calendar_csv = null;
        CSVParser calendar_dates_parser;
        List<CSVRecord> calendar_dates_csv = null;

        try {
            assert calendar_file != null;
            calendar_parser = CSVParser.parse(calendar_file, Charset.defaultCharset(), csv_format);
            calendar_csv = calendar_parser.getRecords();

            assert calendar_dates_file != null;
            calendar_dates_parser = CSVParser.parse(calendar_dates_file, Charset.defaultCharset(), csv_format);
            calendar_dates_csv = calendar_dates_parser.getRecords();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }

        assert calendar_csv != null;
        var dko_list = make_regdko_list(calendar_csv, calendar_dates_csv);

        predicate_printer(dko_list, "regdko.pl");
    }

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

    private static ArrayList<String> make_regbus_list() {
        return new ArrayList<>();
    }

    private static ArrayList<String> make_regcomp_list() {
        return new ArrayList<>();
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

    private static ArrayList<String> make_reghpl_list() {
        return new ArrayList<>();
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
}

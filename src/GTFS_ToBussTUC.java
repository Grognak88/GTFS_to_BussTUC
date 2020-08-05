/**
 * @author Alexander Erlingsen @since 2020.06.02
 *
 * This project is intended to work in unission with https://github.com/saetre/busstuc
 */

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

public class GTFS_ToBussTUC {
    // Global time variables and constants
    static LocalDate starting_date = null;
    static LocalDate ending_date = null;
    static final DateTimeFormatter IN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final DateTimeFormatter OUT_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");


    // Globing pattern for which type of files to find
    static String glob_pattern = "glob:**/*.txt";
    // Input folder to find the files to convert
    static String data_path = "data_full";
    // The output folder path... may be absolute or relative
    static String out_folder = "tables";
    // stat_id to quay mappings
    static HashMap<Integer, String> stat_ids = new HashMap<>();

    // Will contain list of street endings
    static ArrayList<String> gater;

    public static void main(String[] args) {
        var start_time = System.currentTimeMillis();
        String usage = "USAGE:\n"
                + "java GTFS_ToBussTUC"
                + " [INPUT_FOLDER] [OUTPUT_FOLDER]\n"
                + "Converting the GTFS source in INPUT_FOLDER,\n"
                + "creating the prolog code which is stored in OUTPUT_FOLDER\n";

        if(args.length != 2){
            System.err.println( usage );	//System.exit(1);
            System.err.println("Assuming default paths:");
            System.err.println("SOURCE: ./data");
            System.err.println("OUTPUT: ./tables");
        }else{
            data_path = args[0];
            out_folder = args[1];
        }

        var separator = File.separator;

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
        String stop_times_path = "";

        try {
            for (String path : files_list) {
                if (separator.equals("\\")) {
                    path = path.replaceAll(Pattern.quote(separator), "\\\\");
                }
                var strings = path.split(Pattern.quote(separator));
                switch (strings[strings.length - 1]) {
                    case "calendar.txt" -> calendar_file = new File(path);
                    case "calendar_dates.txt" -> calendar_dates_file = new File(path);
                    case "trips.txt" -> trips_file = new File(path);
                    case "stops.txt" -> stops_file = new File(path);
                    case "stop_times.txt" -> stop_times_path = path;
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
        ArrayList<CSVRecord> stop_times_csv = new ArrayList<>();

        try {
            assert calendar_file != null;
            parser = CSVParser.parse(calendar_file, StandardCharsets.UTF_8, csv_format);
            calendar_csv = parser.getRecords();

            assert calendar_dates_file != null;
            parser = CSVParser.parse(calendar_dates_file, StandardCharsets.UTF_8, csv_format);
            calendar_dates_csv = parser.getRecords();

            assert trips_file != null;
            parser = CSVParser.parse(trips_file, StandardCharsets.UTF_8, csv_format);
            trips_csv = parser.getRecords();

            assert stops_file != null;
            parser = CSVParser.parse(stops_file, StandardCharsets.UTF_8, csv_format);
            stops_csv = parser.getRecords();

            try (BufferedReader reader = new BufferedReader(new FileReader(stop_times_path), 1048576 * 10)) {
                Iterable<CSVRecord> records = csv_format.parse(reader);
                for (CSVRecord record : records) {
                    stop_times_csv.add(record);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
        System.out.println("**********************************************************************");
        System.out.println("All files read");
        System.out.println("\nParsing calendar.txt and calendar_dates.txt to regdko.pl");
        var dko_list = make_regdko_list(calendar_csv, calendar_dates_csv);
        System.out.println("*** DKO parsing finished");
        System.out.println("**********************************************************************");
        System.out.println("\nParsing to regbus.pl");
        var bus_list = make_regbus_list(trips_csv);
        System.out.println("*** regbus parsing finished.");
        System.out.println("**********************************************************************");
        System.out.println("\nParsing to regcomp and reghpl ...");
        var comp_and_hpl_list_and_statids = make_regcomp_and_hpl_list(stops_csv);
        System.out.println("*** regcomp and reghpl parsing finished.");
        System.out.println("**********************************************************************");
        stat_ids = comp_and_hpl_list_and_statids.getRight();
        System.out.println("\nParsing to regdep and regpas ...");
        var pas_and_dep_lists = make_regpas_and_dep_list(stop_times_csv, trips_csv, dko_list.getRight());
        System.out.println("**********************************************************************");
        System.out.println("Congratulations parsing finished successfully, writing files now ....");

        File newDir = new File(out_folder + separator + "r160_" + starting_date.format(OUT_FORMAT));
        if (newDir.mkdir()) {
            System.out.println(newDir.getAbsolutePath() + " created...");
        } else {
            System.err.println("Writing into existing folder");
        }

        predicate_printer(dko_list.getLeft(), newDir.getAbsolutePath() + separator + "regdko.pl");
        predicate_printer(bus_list, newDir.getAbsolutePath() + separator + "regbus.pl");
        predicate_printer(comp_and_hpl_list_and_statids.getLeft(), newDir.getAbsolutePath() + separator + "regcomp.pl");
        predicate_printer(comp_and_hpl_list_and_statids.getMiddle(), newDir.getAbsolutePath() + separator + "reghpl.pl");
        predicate_printer(pas_and_dep_lists.getLeft(), newDir.getAbsolutePath() + separator + "regpas.pl");
        predicate_printer(pas_and_dep_lists.getRight(), newDir.getAbsolutePath() + separator + "regdep.pl");

        var stop_time = System.currentTimeMillis();
        System.out.println("Writing finished");

        System.out.println("Elapsed time: " + (stop_time - start_time) + " msec.");

        System.out.println("\nUpdating routes ....");
        String route_period_path = newDir.toPath().toAbsolutePath().getParent().getParent().toString() + separator + "route_period.pl";
        UpdateRoutePeriode.main(new String[]{"Auto Update: " + LocalDate.now(), "r160", starting_date.format(OUT_FORMAT), ending_date.format(OUT_FORMAT), route_period_path});
        System.out.println("Route Periods updated ... ");

        String version_pl_path = newDir.toPath().toAbsolutePath().getParent().getParent().getParent().toString() + separator + "version.pl";
        System.out.println("\nUpdating version.pl ... ");
        version_update("Automatic update", version_pl_path);
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
     *
     * @param trips list of records from the trips.txt file
     * @return list of content to print into regbus.pl
     */
    private static ArrayList<String> make_regbus_list(List<CSVRecord> trips) {
        var regbus = new ArrayList<String>();

        for (CSVRecord record : trips) {
            var line = record.get("trip_id").split(":")[2].split("_");
            var bus = "regbus(" + line[0] + ").";
            var route = "route(bus_" + line[0] + "_" + Math.abs(record.get("trip_id").hashCode()) + "," + line[0] + "," + line[0] + ").";

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
     *
     * @param stops the list of csv records
     * @return tuple of regcomp and reghpl
     */
    private static Triple<ArrayList<String>, ArrayList<String>, HashMap<Integer, String>> make_regcomp_and_hpl_list(List<CSVRecord> stops) {
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
        var stat_id = new HashMap<Integer, String>();


        for (CSVRecord record : stops) {
            if (record.get("stop_id").contains("StopPlace"))
                continue;
            setHpl(comp_list, record, hpl_list, stat_id);
        }

        comp_list.sort(null);
        hpl_list.sort(null);

        return Triple.of(comp_list, hpl_list, stat_id);
    }

    /**
     * Function that parses the calendar.txt and calendar_date.txt to regdko.pl format
     *
     * @param calendar       calendar.txt as a list of csv records
     * @param calendar_dates calendar_dates.txt as a list of csv records
     * @return list of strings to be printed in regdko.pl
     */
    private static Pair<ArrayList<String>, HashMap<String, String>> make_regdko_list(List<CSVRecord> calendar, List<CSVRecord> calendar_dates) {
        starting_date = get_next_monday(get_date(calendar.get(0).get("start_date"))); // denotes the first start date it finds will be updated later
        ending_date = get_date(calendar.get(0).get("end_date"));
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
            if (get_date(record.get("end_date")).isAfter(ending_date)) {
                ending_date = get_date(record.get("end_date"));
            }

            var weeks = record.get("monday") + record.get("tuesday") + record.get("wednesday") + record.get("thursday") + record.get("friday") + record.get("saturday") + record.get("sunday");
            StringBuilder days = new StringBuilder();
            var record_starting_monday = get_next_monday(get_date(record.get("start_date")));
            long days_valid = ChronoUnit.DAYS.between(record_starting_monday, get_date(record.get("end_date")));
            long days_befor_validity = ChronoUnit.DAYS.between(starting_date, record_starting_monday);
            // make string of validity
            for (int i = 0; i < days_valid; i++) {
                days.append(weeks.charAt(i % 7));
            }

            days = new StringBuilder(padLeft(days.toString(), (int) days_befor_validity));

            days = new StringBuilder(padRight(days.toString(), (mask_length - days.length())));

            dko_list.add(new DKO(record.get("service_id").split(":")[2].replaceAll("_",""), record_starting_monday, get_date(record.get("end_date")), weeks, days.toString()));
        }

        for (CSVRecord record : calendar_dates) {
            var temp = new DKO();
            var temp_day_code = record.get("service_id").split(":")[2].replaceAll("_","");
            temp.setDay_code(temp_day_code);
            var date = get_date(record.get("date"));
            var day_from_start = ChronoUnit.DAYS.between(starting_date, date);
            if (dko_list.contains(temp)) {
                var other_dko = dko_list.get(dko_list.indexOf(temp));
                if (record.get("exception_type").equals("2")) {
                    other_dko.setDays(replaceCharUsingCharArray(other_dko.getDays(), '0', (int) day_from_start));
                } else {
                    other_dko.setDays(replaceCharUsingCharArray(other_dko.getDays(), '1', (int) day_from_start));
                    other_dko.setTo(date.plus(1, ChronoUnit.DAYS));
                }
            } else {
                var day_mask = new char[406];
                Arrays.fill(day_mask, '0');
                if (record.get("exception_type").equals("1")) {
                    day_mask[(int) day_from_start] = '1';
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
        HashMap<String, String> old_to_new_day_code = new HashMap<>();

        for (DKO dko :
                dko_list) {
            for (DKO other_dko :
                    dko_list) {
                if (!dko.equals(other_dko)) {
                    if (dko.getDays().equals(other_dko.getDays())) {
                        other_dko.setOld_day_code(other_dko.getDay_code());
                        other_dko.setDay_code(dko.getDay_code());
                        old_to_new_day_code.put(other_dko.getOld_day_code(), other_dko.getDay_code());
                    }
                }
            }
        }

        ArrayList<String> regdko = new ArrayList<>();

        for (DKO dko : dko_list) {
            var print_string = dko.toString();
            if (!regdko.contains(print_string))
                regdko.add(print_string);
        }
        regdko.sort(null);
        regdko.add(0, "dkodate(" + starting_date.format(OUT_FORMAT) + ",1).");

        return Pair.of(regdko, old_to_new_day_code);
    }

    /**
     * @param stop_times list of csv records of stop times
     * @param trips      list of trips csv records
     * @param old_to_new_day_code list of altered dko's
     * @return tuple with list of dep and pas outputs
     */
    private static Pair<ArrayList<String>, ArrayList<String>> make_regpas_and_dep_list(ArrayList<CSVRecord> stop_times, List<CSVRecord> trips, HashMap<String, String> old_to_new_day_code) {
        ArrayList<PasSegment> all_passes = new ArrayList<>();
        HashMap<String, Integer> trip_seg_mapping = new HashMap<>();

        var counter = 0;

        // loop through all the stops times and extract and parse the information that is needed for the pas and dep files.
        for (CSVRecord record :
                stop_times) {
            var seq = Integer.parseInt(record.get("stop_sequence"));
            if (seq == 0) {
                counter++;
                var trip_id = record.get("trip_id");
                all_passes.add(new PasSegment(counter, new ArrayList<>(), trip_id));
                all_passes.get(counter - 1).add(new PasHelper(record.get("stop_id").split(":")[2], record.get("arrival_time"), record.get("departure_time"), Integer.parseInt(record.get("stop_sequence"))));
                trip_seg_mapping.put(trip_id, counter);
                continue;
            }
            var start_time_split = all_passes.get(counter - 1).getPasses().get(0).getArrival_time().split(":");
            var start_time = Integer.parseInt(start_time_split[0]) * 60 + Integer.parseInt(start_time_split[1]);
            // Converting to minutes from midnight
            var arrival_time_split = record.get("arrival_time").split(":");
            var arrival_time = Integer.parseInt(arrival_time_split[0]) * 60 + Integer.parseInt(arrival_time_split[1]);
            // Converting to minutes from midnight
            var depart_time_split = record.get("departure_time").split(":");
            var depart_time = Integer.parseInt(depart_time_split[0]) * 60 + Integer.parseInt(depart_time_split[1]);
            // Calculating the minutes off sett from starting time in the first part the segment
            var arr = arrival_time - start_time;
            var dep = depart_time - start_time;

            all_passes.get(counter - 1).add(new PasHelper(record.get("stop_id").split(":")[2], record.get("arrival_time"), record.get("departure_time"), Integer.parseInt(record.get("stop_sequence")), arr, dep));
        }

        ArrayList<PasSegment> no_dup = new ArrayList<>();

        /* This part unfortunately has a runtime of O(n^2) at worst and O(nlog(n)) at best,
         but none of these will occur and will be somewhere in between closer to O(nlog(n)),
         As the no_dup list will not grow linearly. */
        for (PasSegment segment : all_passes) {
            var index = is_unique(segment, no_dup);
            if (index == -1) {
                no_dup.add(segment);
            } else {
                trip_seg_mapping.replace(segment.getTrip_id(), trip_seg_mapping.get(all_passes.get(index).getTrip_id()));
            }
        }

        ArrayList<String> pas_list = new ArrayList<>();

        // making the regpas.pl elements and store them in array list
        for (PasSegment segment :
                no_dup) {
            pas_list.add(segment.toString());
            pas_list.add("ntourstops(" + segment.getSeg_id() + ", " + segment.getPasses().size() + ").");
        }

        pas_list.sort(null);

        ArrayList<String> dep_list = new ArrayList<>();
        // index of each trip should correspond to each index in all passes
        var index = 0;
        // making the regdep.pl elements
        for (CSVRecord trip :
                trips) {
            var trip_id_parts = trip.get("trip_id").split(":")[2].split("_");
            var segment = find(all_passes, trip.get("trip_id"));
            var dep_time_parts = segment.getPasses().get(0).getDeparture_time().split(":");
            var dep_time = Integer.parseInt(dep_time_parts[0] + dep_time_parts[1]);
            var day_code = trip.get("service_id").split(":")[2].replaceAll("_","");
            // Replacing daycode with the new one
            if (old_to_new_day_code.containsKey(day_code))
                day_code = old_to_new_day_code.get(day_code);

            var temp = "departureday( bus_" + trip_id_parts[0] + "_" + Math.abs(trip.get("trip_id").hashCode()) + ", " + trip_seg_mapping.get(trip.get("trip_id")) + ", " + dep_time + ", " + day_code + ").";

            if (!dep_list.contains(temp))
                dep_list.add(temp);

            index++;
        }

        dep_list.sort(null);
        pas_list.sort(null);

        return Pair.of(pas_list, dep_list);
    }

    /**
     * Prints out the predicates to prolog files
     * @param predicates a list of predicates represented as strings
     * @param file the path to the file to be written
     */
    private static void predicate_printer(ArrayList<String> predicates, String file) {
        try {
            var out_file = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8));

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

    /**
     * A simple method to update the version.pl file in busstuc
     * @param comment An update comment if provided
     * @param path path to version.pl in busstuc
     */
    private static void version_update(String comment, String path) {
        String data = "/* -*- Mode:Prolog; coding:utf-8; -*- */\n"
                +"%% Generated by " + GTFS_ToBussTUC.class + " on "+new Timestamp(new Date().getTime())+"\n"
                +"%% FILE version.pl\n"
                +"%% SYSTEM TUC\n"
                +"%% CREATED TA-970913\n"
                +"/* REVISED : AE- */  version_date('AtB-I "+comment+", updated on "+ new Timestamp(new Date().getTime()) +"')."
                +"\n\n"
                +"%% "+comment+"\n";

        try (var outFile = new BufferedWriter(new FileWriter(path))) {
            System.out.println("Path is " + path);

            outFile.write(data);

        } catch (IOException e) {
            System.out.println("File does not exist: " + e);
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Helper Functions after this line */

    private static PasSegment find(ArrayList<PasSegment> all_passes, String trip_id) {
        for (PasSegment pas :
                all_passes) {
            if (trip_id.equals(pas.getTrip_id()))
                return pas;
        }

        return null;
    }

    /**
     * @param segment segment to check if is not in no_dup
     * @param no_dup  the list to check against
     * @return index of the equal segment or -1 to denote it is not in no_dup
     */
    private static int is_unique(PasSegment segment, ArrayList<PasSegment> no_dup) {
        for (PasSegment seg : no_dup) {
            if (seg.equals(segment))
                // return index of the equal segment in no_dup, as segment id is 1 higher than index
                return seg.getSeg_id() - 1;
        }
        return -1;
    }

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
        if (date.getDayOfWeek().equals(DayOfWeek.MONDAY)){
            return date;
        }
        return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /**
     * Pads a string on the right with 0
     *
     * @param s string to be padded
     * @param n amount to pad
     * @return padded string
     */
    public static String padRight(String s, int n) {
        return org.apache.commons.lang3.StringUtils.rightPad(s, n + s.length(), "0");
    }

    /**
     * Pads a string on the left with 0
     *
     * @param s string to be padded
     * @param n amount to pad
     * @return padded string
     */
    public static String padLeft(String s, int n) {
        return org.apache.commons.lang3.StringUtils.leftPad(s, n + s.length(), "0");
    }

    /**
     * @param str   string to be edited
     * @param ch    character to input
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
     * @param composite_stat_list list to store in
     * @param record              CSV line to pars
     * @param hpl_list            HashMap to store stat_ids with stop_id as key
     */
    public static void setHpl(ArrayList<String> composite_stat_list, CSVRecord record, ArrayList<String> hpl_list, HashMap<Integer, String> stat_ids) {
        String statname = (record.get("stop_name") + " " + record.get("platform_code")).trim().replaceAll("'", "`");
        String statid = conv_statname(statname); // used util function from precious solution as the regex I tried did not work as expected all the time

        // store stop_id stat_id pair in hash map
        stat_ids.put(Integer.valueOf(record.get("stop_id").split(":")[2]), statid);

        ArrayList<String> composite_stat = new ArrayList<>();
        // update hpl_list
        var hpl = "hpl(" + record.get("stop_id").split(":")[2] + "," + statid + "," + statid + ",'" + statname + "').";
        if (!hpl_list.contains(hpl)) {
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
     * Removes unwanted characters from the string e.g. "-, /, (, ), ...."
     * @param iStr input string
     * @return sanitized string
     */
    public static String conv_statname(String iStr) {
        // punktum til blank, blanke til slutt fjernes.
        StringBuffer statid = new StringBuffer();
        char tegn = ' ';
        String new_statname = iStr.toLowerCase(); //.trim();
        for (int i = 0; i < new_statname.length(); i++) {
            tegn = new_statname.charAt(i);
            if (tegn == '.' || tegn == '(' || tegn == ')' || tegn == '/' || tegn == '|' ||
                    tegn == ',' || tegn == '-' || tegn == '&' || tegn == '`') { // TA-110225

                statid.append(' ');
            } else {
                statid.append(tegn);
            }
        }

        new_statname = statid.toString().trim();  //2

        new_statname = new_statname.replaceAll(" +", " ");//3

        new_statname = new_statname.replaceAll(" ", "_"); //4

        statid = new StringBuffer();
        for (int i = 0; i < new_statname.length(); i++) {
            tegn = new_statname.charAt(i);
            if (Character.isDigit(tegn) || Character.isLetter(tegn) ||
                    tegn == '(' || tegn == ')' || tegn == '&' || tegn == ',' || // %% TA-110209
                    tegn == '_' || tegn == 'æ' || tegn == 'ø' || tegn == 'å') { // UTF-8
                statid.append(tegn);
            } else {
                System.out.print(tegn);
            }
        }

        String strID = statid.toString();

        return strID;
    }// conv_statname

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

/**
 * Simple helper class to hold the different segments of a trip
 */
class PasSegment {
    private int seg_id;
    private ArrayList<PasHelper> passes;
    private final String trip_id;

    public PasSegment(int seg_id, ArrayList<PasHelper> passes, String trip_id) {
        this.seg_id = seg_id;
        this.passes = passes;
        this.trip_id = trip_id;
    }

    public int getSeg_id() {
        return seg_id;
    }

    public void setSeg_id(int seg_id) {
        this.seg_id = seg_id;
    }

    public ArrayList<PasHelper> getPasses() {
        return passes;
    }

    public void setPasses(ArrayList<PasHelper> passes) {
        this.passes = passes;
    }

    public boolean contains(PasHelper pas) {
        return passes.contains(pas);
    }

    public void add(PasHelper pas) {
        passes.add(pas);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PasSegment) {
            var temp = (PasSegment) obj;

            return this.getPasses().equals(temp.getPasses());
        }

        return false;
    }

    public String getTrip_id() {
        return trip_id;
    }

    @Override
    public String toString() {
        String return_string = "";
        for (PasHelper pas :
                passes) {
            return_string = return_string.concat("\npasses4(" + getSeg_id() + ", " + pas.getId() + ", " + GTFS_ToBussTUC.stat_ids.get(Integer.parseInt(pas.getId())) + ", " + (pas.getSeq() + 1) + ", " + pas.getArr() + ", " + pas.getDep() + ").");
        }
        return return_string;
    }
}

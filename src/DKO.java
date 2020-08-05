import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DKO {
    private final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private String day_code;
    private LocalDate from;
    private LocalDate to;
    private String week1;
    private String week2;
    private String week3;
    private String week4;

    private String days;

    private String old_day_code;

    // Default constructor without given values
    public DKO(){}

    public DKO(String day_code, LocalDate from, LocalDate to, String weeks, String days) {
        this.day_code = day_code;
        this.from = from;
        this.to = to;
        this.week1 = weeks;
        this.week2 = weeks;
        this.week3 = weeks;
        this.week4 = weeks;
        this.days = days;
    }

    public String getDay_code() {
        return day_code;
    }

    public void setDay_code(String day_code) {
        this.day_code = day_code;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public String getWeek1() {
        return week1;
    }

    public void setWeek1(String week1) {
        this.week1 = week1;
    }

    public String getWeek2() {
        return week2;
    }

    public void setWeek2(String week2) {
        this.week2 = week2;
    }

    public String getWeek3() {
        return week3;
    }

    public void setWeek3(String week3) {
        this.week3 = week3;
    }

    public String getWeek4() {
        return week4;
    }

    public void setWeek4(String week4) {
        this.week4 = week4;
    }

    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public String getOld_day_code() {
        return old_day_code;
    }

    public void setOld_day_code(String old_day_code) {
        this.old_day_code = old_day_code;
    }

    @Override
    public boolean equals(Object obj) {
        DKO other;
        if (obj instanceof DKO) {
            other = (DKO) obj;
        } else {
            other = new DKO();
        }
        return this.day_code.equals(other.day_code);
    }

    @Override
    public String toString() {
        return "dko("+day_code+","+from.format(FORMAT)+","+to.format(FORMAT)+","+week1+","+week2+","+week3+","+week4+",'"+days+"').";
    }
}

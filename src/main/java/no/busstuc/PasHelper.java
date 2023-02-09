package no.busstuc;

public class PasHelper {
    private String id;
    private int seq;
    private int arr;
    private int dep;
    private String arrival_time;
    private String departure_time;

    public PasHelper(String id, String arrival_time, String departure_time, int seq) {
        // Always leave at the same time it arrives on first stop
        this(id, arrival_time, departure_time, seq, 999, 0);
    }

    public PasHelper(String id, String arrival_time, String departure_time, int seq, int arr, int dep) {
        this.id = id;
        // Parsing arrival times to amount of minutes since midnight
        this.arrival_time = arrival_time;
        this.departure_time = departure_time;
        this.seq = seq;
        this.arr = arr;
        this.dep = dep;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getArr() {
        return arr;
    }

    public int getDep() {
        return dep;
    }

    public void setArr(int arr) {
        this.arr = arr;
    }

    public void setDep(int dep) {
        this.dep = dep;
    }

    public int getSeq() {
        return seq;
    }

    public String getArrival_time() {
        return arrival_time;
    }

    public String getDeparture_time() {
        return departure_time;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PasHelper) {
            var temp = (PasHelper) obj;
            if (this.arr == temp.arr && this.id.equals(temp.id) && this.dep == temp.dep){
                return true;
            }
        }

        return false;
    }
}

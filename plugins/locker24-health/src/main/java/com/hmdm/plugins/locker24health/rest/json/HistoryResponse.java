package com.hmdm.plugins.locker24health.rest.json;

import java.util.ArrayList;
import java.util.List;

public class HistoryResponse {

    private long from;
    private long to;
    private long bucketMs;
    private boolean truncated;
    private List<Point> points = new ArrayList<>();
    private List<Period> offlinePeriods = new ArrayList<>();

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

    public long getBucketMs() {
        return bucketMs;
    }

    public void setBucketMs(long bucketMs) {
        this.bucketMs = bucketMs;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public List<Period> getOfflinePeriods() {
        return offlinePeriods;
    }

    public void setOfflinePeriods(List<Period> offlinePeriods) {
        this.offlinePeriods = offlinePeriods;
    }

    public static class Point {

        private long t;
        private Integer batteryPct;
        private Float temperatureC;
        private Integer rssiDbm;
        private Boolean charging;
        private Boolean kioskRunning;

        public long getT() {
            return t;
        }

        public void setT(long t) {
            this.t = t;
        }

        public Integer getBatteryPct() {
            return batteryPct;
        }

        public void setBatteryPct(Integer batteryPct) {
            this.batteryPct = batteryPct;
        }

        public Float getTemperatureC() {
            return temperatureC;
        }

        public void setTemperatureC(Float temperatureC) {
            this.temperatureC = temperatureC;
        }

        public Integer getRssiDbm() {
            return rssiDbm;
        }

        public void setRssiDbm(Integer rssiDbm) {
            this.rssiDbm = rssiDbm;
        }

        public Boolean getCharging() {
            return charging;
        }

        public void setCharging(Boolean charging) {
            this.charging = charging;
        }

        public Boolean getKioskRunning() {
            return kioskRunning;
        }

        public void setKioskRunning(Boolean kioskRunning) {
            this.kioskRunning = kioskRunning;
        }
    }

    public static class Period {

        private long from;
        private long to;

        public Period() {
        }

        public Period(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public long getFrom() {
            return from;
        }

        public void setFrom(long from) {
            this.from = from;
        }

        public long getTo() {
            return to;
        }

        public void setTo(long to) {
            this.to = to;
        }
    }
}

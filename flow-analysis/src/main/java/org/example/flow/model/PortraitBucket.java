package org.example.flow.model;

public class PortraitBucket {

    private String bucket;
    private long   count;
    private double ratio;

    public PortraitBucket() {}

    public PortraitBucket(String bucket, long count, double ratio) {
        this.bucket = bucket;
        this.count  = count;
        this.ratio  = ratio;
    }

    public String getBucket() { return bucket; }
    public long   getCount()  { return count; }
    public double getRatio()  { return ratio; }

    public void setBucket(String bucket) { this.bucket = bucket; }
    public void setCount(long count)     { this.count  = count; }
    public void setRatio(double ratio)   { this.ratio  = ratio; }
}

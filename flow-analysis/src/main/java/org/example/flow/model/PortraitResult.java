package org.example.flow.model;

import java.util.List;

public class PortraitResult {

    private String direction;
    private String dimension;
    private long   total;
    private List<PortraitBucket> buckets;

    public PortraitResult() {}

    public PortraitResult(String direction, String dimension,
                          long total, List<PortraitBucket> buckets) {
        this.direction = direction;
        this.dimension = dimension;
        this.total     = total;
        this.buckets   = buckets;
    }

    public String getDirection()             { return direction; }
    public String getDimension()             { return dimension; }
    public long   getTotal()                 { return total; }
    public List<PortraitBucket> getBuckets() { return buckets; }

    public void setDirection(String direction)             { this.direction = direction; }
    public void setDimension(String dimension)             { this.dimension = dimension; }
    public void setTotal(long total)                       { this.total     = total; }
    public void setBuckets(List<PortraitBucket> buckets)   { this.buckets   = buckets; }
}

package org.example.flow.model;

public class ODFlow {

    private String fromRegionId;
    private String fromRegionName;
    private String toRegionId;
    private String toRegionName;
    private long   flow;

    public ODFlow() {}

    public ODFlow(String fromRegionId, String fromRegionName,
                  String toRegionId,   String toRegionName, long flow) {
        this.fromRegionId   = fromRegionId;
        this.fromRegionName = fromRegionName;
        this.toRegionId     = toRegionId;
        this.toRegionName   = toRegionName;
        this.flow           = flow;
    }

    public String getFromRegionId()   { return fromRegionId; }
    public String getFromRegionName() { return fromRegionName; }
    public String getToRegionId()     { return toRegionId; }
    public String getToRegionName()   { return toRegionName; }
    public long   getFlow()           { return flow; }

    public void setFromRegionId(String fromRegionId)     { this.fromRegionId   = fromRegionId; }
    public void setFromRegionName(String fromRegionName) { this.fromRegionName = fromRegionName; }
    public void setToRegionId(String toRegionId)         { this.toRegionId     = toRegionId; }
    public void setToRegionName(String toRegionName)     { this.toRegionName   = toRegionName; }
    public void setFlow(long flow)                       { this.flow           = flow; }
}

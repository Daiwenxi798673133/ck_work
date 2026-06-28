package org.example.flow.model;

public class RegionVO {

    private String regionId;
    private String regionName;
    private String lac;
    private int cellCount;

    public RegionVO() {}

    public RegionVO(String regionId, String regionName, String lac, int cellCount) {
        this.regionId   = regionId;
        this.regionName = regionName;
        this.lac        = lac;
        this.cellCount  = cellCount;
    }

    public String getRegionId()   { return regionId; }
    public String getRegionName() { return regionName; }
    public String getLac()        { return lac; }
    public int    getCellCount()  { return cellCount; }

    public void setRegionId(String regionId)     { this.regionId   = regionId; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public void setLac(String lac)               { this.lac        = lac; }
    public void setCellCount(int cellCount)      { this.cellCount  = cellCount; }
}

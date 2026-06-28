package org.example.flow.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainConstTest {

    @Test
    void allRegionsSize() {
        assertEquals(3, RegionDef.allRegions().size());
    }

    @Test
    void allCellsSize() {
        assertEquals(8, RegionDef.allCells().size());
    }

    @Test
    void regionOfCell_6254_179() {
        assertEquals("310000_6254", RegionDef.regionOfCell("310000_6254_140854179"));
        assertEquals("6254", RegionDef.lacOfCell("310000_6254_140854179"));
    }

    @Test
    void regionOfCell_6200_186() {
        assertEquals("310000_6200", RegionDef.regionOfCell("310000_6200_140854186"));
    }

    @Test
    void ageGroupBoundaries() {
        assertEquals("<18",   ProfileConst.ageGroup(17));
        assertEquals("18-25", ProfileConst.ageGroup(18));
        assertEquals("18-25", ProfileConst.ageGroup(25));
        assertEquals("46-60", ProfileConst.ageGroup(60));
        assertEquals("60+",   ProfileConst.ageGroup(61));
    }
}

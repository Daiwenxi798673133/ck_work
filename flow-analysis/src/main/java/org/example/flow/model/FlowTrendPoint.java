package org.example.flow.model;

import java.time.LocalDateTime;

public class FlowTrendPoint {

    private LocalDateTime windowStart;
    private long population;
    private long inflow;
    private long outflow;
    private long retained;

    public FlowTrendPoint() {}

    public FlowTrendPoint(LocalDateTime windowStart, long population,
                          long inflow, long outflow, long retained) {
        this.windowStart = windowStart;
        this.population  = population;
        this.inflow      = inflow;
        this.outflow     = outflow;
        this.retained    = retained;
    }

    public LocalDateTime getWindowStart() { return windowStart; }
    public long getPopulation()           { return population; }
    public long getInflow()               { return inflow; }
    public long getOutflow()              { return outflow; }
    public long getRetained()             { return retained; }

    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public void setPopulation(long population)            { this.population  = population; }
    public void setInflow(long inflow)                    { this.inflow      = inflow; }
    public void setOutflow(long outflow)                  { this.outflow     = outflow; }
    public void setRetained(long retained)                { this.retained    = retained; }
}

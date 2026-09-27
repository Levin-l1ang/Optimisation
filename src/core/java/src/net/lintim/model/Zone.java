package net.lintim.model;

import net.lintim.io.CsvWriter;
import net.lintim.model.Stop;

import java.util.*;

/**
 * implementation of a zone object for tariff planning.
 */
public class Zone {
    protected int zoneId;
    protected Set<Stop> stops = new HashSet<Stop>();

    /**
     * Create a new Zone given the information of a LinTim zone.
     * @param zoneId the id of the zone. Needs to be unique.
     * @param stops the set of stops that belong to this zone.
     */
    public Zone(int zoneId, Set<Stop> stops) {
        this.zoneId = zoneId;
        this.stops = stops;
    }

	public int getZoneId() {
		return zoneId;
	}

	public void setZoneId(int id) {
		this.zoneId = id;
	}

    public Set<Stop> getStops() {
        return stops;
    }

    public void addStop(Stop stop) {
        this.stops.add(stop);
    }

    public boolean containsStop(Stop stop) {
        return stops.contains(stop);
    }

    public void removeStop(Stop stop) {
        this.stops.remove(stop);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Zone zone = (Zone) o;
        return zoneId == zone.zoneId && stops == zone.stops;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId, stops);
    }

}

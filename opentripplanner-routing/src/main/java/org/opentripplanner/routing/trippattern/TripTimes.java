package org.opentripplanner.routing.trippattern;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.common.MavenVersion;

/**
 * Holds the arrival / departure times for a single trip in an ArrayTripPattern Also gets carried
 * along by States when routing to ensure that they have a consistent view of the trip when realtime
 * updates are being taken into account. 
 * All times are in seconds since midnight (as in GTFS). The array indexes are actually *hop* indexes,
 * not stop indexes, in the sense that 0 refers to the hop between stops 0 and 1, so arrival 0 is actually
 * an arrival at stop 1. 
 * By making indexes refer to stops not hops we could reuse the departures array for arrivals, 
 * at the cost of an extra entry. This seems more coherent to me (AMB) but would probably break things elsewhere.
 */
public class TripTimes implements Cloneable, Serializable {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();
    public static final int PASSED = -1;
    public static final int CANCELED = -2;
    public static final int EXPIRED = -3;
    
    public final Trip trip;

    public final int index; // this is kind of ugly, but the headsigns are in the pattern not here
    
    @XmlElement
    protected int[] departureTimes;

    // null means all dwells are 0-length, and arrival times are to be derived from departure times
    @XmlElement
    protected int[] arrivalTimes; 

    public TripTimes(Trip trip, int index, List<StopTime> stopTimes) {
        // stopTimes are assumed to be pre-filtered / valid / monotonically increasing etc.
        this(trip, index, stopTimes.size(), false);
        boolean nullArrivals = true;
        // this might be clearer if time array indexes were stops instead of hops
        for (int hop = 0; hop < stopTimes.size() - 1; hop++) {
            departureTimes[hop] = stopTimes.get(hop).getDepartureTime();
            arrivalTimes[hop] = stopTimes.get(hop+1).getArrivalTime();
            if (departureTimes[hop] != arrivalTimes[hop])
                nullArrivals = false;
        }
        // if all dwell times are 0, arrival times array is not needed. save some memory.
        if (nullArrivals)
            arrivalTimes = null;
    }
    
    public TripTimes(Trip trip, int index, int nStops, boolean nullArrivals) {
        // departure arrays could be 1 shorter when arrivals are present, but they are not
        this.trip = trip;
        this.index = index;
        departureTimes = new int[nStops];
        if (nullArrivals) {
            arrivalTimes = null;
        } else {
            arrivalTimes = new int[nStops];
        }
    }

    public int getDepartureTime(int hop) {
        return departureTimes[hop];
    }

    public int getArrivalTime(int hop) {
        if (arrivalTimes == null)
            return departureTimes[hop + 1];
        return arrivalTimes[hop];
    }

    public int getRunningTime(int hop) {
        return getArrivalTime(hop) - getDepartureTime(hop);
    }

    public int getDwellTime(int hop) {
        // the dwell time of a hop is the dwell time *before* that hop.
        // Therefore it is undefined for hop 0, and at the end of a trip.
        // see GTFSPatternHopFactory.makeTripPattern()
        int arrivalTime = getArrivalTime(hop-1);
        int departureTime = getDepartureTime(hop);
        return departureTime - arrivalTime;
    }
    
    public Trip getTrip() {
        return trip;
    }

    // replace arrivals array with null when arrivals and departures are equal
    public boolean compact() {
        return true;
    }

    public String dumpTimes() {
        StringBuilder sb = new StringBuilder();
        for (int hop=0; hop<departureTimes.length; hop++) {
            sb.append(departureTimes[hop]); 
            sb.append('_');
            sb.append(arrivalTimes[hop]);
            sb.append(' ');
        }
        return sb.toString();
    }
    
    public TripTimes updatedClone(UpdateList ul, int startIndex) {
        TripTimes ret = (TripTimes) this.clone();
        for (int i = 0; i < ul.updates.size(); i++) {
            int targetIndex = startIndex + i;
            Update u = ul.updates.get(i);
            ret.departureTimes[targetIndex] = u.depart;
            // hops not stops... arrival time is for the previous hop 
            // and if arv / dep have been merged?
            if (targetIndex >= 1)
                ret.arrivalTimes[targetIndex-1] = u.arrive; 
        }
        System.out.println(this.dumpTimes());
        System.out.println(ret.dumpTimes());
        return ret;
    }
    
    @Override
    public TripTimes clone() {
        TripTimes ret = null; 
        try {
            ret = (TripTimes) super.clone();
            ret.arrivalTimes = this.arrivalTimes.clone();
            ret.departureTimes = this.departureTimes.clone();
        } catch (CloneNotSupportedException e) {
            // will not happen
        }
        return ret;
    }
    
}

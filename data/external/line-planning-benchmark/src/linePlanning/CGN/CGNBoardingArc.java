package linePlanning.CGN;

import linePlanning.instance.Station;

public class CGNBoardingArc extends CGNArc
{
	private final Station station;
	private final double duration;
	private final boolean isBoarding;

	public CGNBoardingArc(double duration, Station station, boolean isBoarding) {
		this.duration = duration;
		this.station = station;
		this.isBoarding = isBoarding;
	}

	@Override
	public double getDuration()
	{
		return duration; 
	}

	@Override
	public String toString() {
		if(isBoarding) {
			return "Board at "+station.getNameFull()+" duration="+duration;
		} else {
			return "Disembark at "+station.getNameFull()+" duration="+duration;
		}
	}

	
}

package linePlanning.CGN;

public class CGNTransferArc extends CGNArc {
    
    private final double duration;

	public CGNTransferArc(double duration) {
		this.duration = duration;
	}

	@Override
	public double getDuration()
	{
		return duration; 
	}

	@Override
	public String toString() {
		return "Transfer with duration=" + duration;
	}

	
}

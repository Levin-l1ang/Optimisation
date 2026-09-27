import java.io.File;
import java.io.IOException;

public class NonPeriodicTimetableEvaluator {

	public static void evaluateNonPeriodicTimetable(NonPeriodicEANetwork ean)
			throws IOException {
		long weightedTotalSlack =
				calculateWeightedSlack(ean.getActivities());
		long weightedDrivingSlack =
				calculateWeightedSlack(ean.getDrivingActivities());
		long weightedWaitingSlack =
				calculateWeightedSlack(ean.getWaitingActivities());
		long weightedCirculationSlack =
				calculateWeightedSlack(ean.getCirculationActivities());
		long weightedChangingSlack =
				calculateWeightedSlack(ean.getChangingActivities());
		long weightedNiceSlack =
				calculateWeightedSlack(ean.getNiceActivities());
		long weightedHeadwaySlack =
				calculateWeightedSlack(ean.getHeadwayActivities());
		double weightedTime = calculateWeightedTime(ean.getActivities());
		Config config = new Config(new File("basis/Config.cnf"));
		Statistic s = new Statistic(new File(
				config.getStringValue("default_statistic_file")));
		s.setLongValue("tim_nonperiodic_weighted_total_slack",
				weightedTotalSlack);
		s.setLongValue("tim_nonperiodic_weighted_driving_slack",
				weightedDrivingSlack);
		s.setLongValue("tim_nonperiodic_weighted_waiting_slack",
				weightedWaitingSlack);
		s.setLongValue("tim_nonperiodic_weighted_circulation_slack",
				weightedCirculationSlack);
		s.setLongValue("tim_nonperiodic_weighted_changing_slack",
				weightedChangingSlack);
		s.setLongValue("tim_nonperiodic_weighted_nice_slack",
				weightedNiceSlack);
		s.setLongValue("tim_nonperiodic_weighted_headway_slack",
				weightedHeadwaySlack);
		s.setDoubleValue("tim_nonperiodic_weighted_time",weightedTime);
		s.writeStatistic(new File(
				config.getStringValue("default_statistic_file")));
		System.out.println("weighted total slack: " +
				weightedTotalSlack);
		System.out.println("weighted driving slack: " +
				weightedDrivingSlack);
		System.out.println("weighted waiting slack: " +
				weightedWaitingSlack);
		System.out.println("weighted circulation slack: " +
				weightedCirculationSlack);
		System.out.println("weighted changing slack: " +
				weightedChangingSlack);
		System.out.println("weighted nice slack: " +
				weightedNiceSlack);
		System.out.println("weighted headway slack: " +
				weightedHeadwaySlack);
		System.out.println("weighted time: " +
				weightedTime);
				
	}

	private static long calculateWeightedSlack(
			Iterable<? extends NonPeriodicActivity> activities) {
		long weightedSlack = 0;
		for (NonPeriodicActivity a : activities){
			weightedSlack += a.getWeight() *
					(a.getTarget().getTime() - a.getSource().getTime()
					- a.getLowerBound());
		}
		return weightedSlack;
	}
	
	private static double calculateWeightedTime(Iterable<? extends NonPeriodicActivity> activities) {
		double weightedTime = 0;
		for (NonPeriodicActivity a : activities){
			weightedTime += a.getWeight() * (a.getTarget().getTime() - a.getSource().getTime());
		}
		return weightedTime;
	}

	public static void main(String[] args) throws Exception {
		evaluateNonPeriodicTimetable(IO.readNonPeriodicEANetwork(false, false));
	}

}

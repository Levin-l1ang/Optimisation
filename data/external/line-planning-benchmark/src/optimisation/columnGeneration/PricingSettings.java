package optimisation.columnGeneration;

import util.Configuration;

public class PricingSettings
{
	public static boolean START_EXACT_PRICING = Configuration	.getConfiguration()
																.getBooleanProperty("START_EXACT_PRICING");
	public static boolean SWITCH_TO_EXACT_PRICING = Configuration	.getConfiguration()
																	.getBooleanProperty("SWITCH_TO_EXACT_PRICING");
	public static boolean EXACT_PRICING = START_EXACT_PRICING;
}

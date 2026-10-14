package util;

public class Util
{
	public static int[] retrieveSettings(int[] sizes, int index)
	{
		// Find indices of settings based on lexicographic index.
		int product = 1;
		int[] settings = new int[sizes.length];
		for (int i = sizes.length - 1; i >= 0; i--)
		{
			settings[i] = Math.floorDiv(index, product) % sizes[i];
			product *= sizes[i];
		}
		return settings;
	}
}

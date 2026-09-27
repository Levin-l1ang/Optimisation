import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;



public class Tools
{
	private Tools() {}  // class only contains static methods



	// counts the number of relevant lines in a data file
	public static int countRelevantLines(String filename) throws IOException
	{
		int count = 0;
		String line;
		BufferedReader in = new BufferedReader(new FileReader(filename));
		while ((line = in.readLine()) != null)
		{
			line = line.trim();
			if (! (line.isEmpty() || line.charAt(0) == '#'))
				count++;
		}
		in.close();
		return count;
	}

	// check whether all IDs in a data file are consecutive, starting with 1
	public static boolean checkIDs(String filename) throws IOException
	{
		int count = 1;
		String line;
		BufferedReader in = new BufferedReader(new FileReader(filename));
		while ((line = in.readLine()) != null)
		{
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#')
				continue;

			String[] tokens = line.split(";");
			if (Integer.parseInt(tokens[0].trim()) != count)
				return false;
			count++;
		}
		in.close();
		if (count < 2) // no line containing data
			return false;
		return true;
	}
}

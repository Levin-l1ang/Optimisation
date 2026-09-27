import java.util.*;
import java.io.*;

public class DemandCSV{
	public static void fromFile(Demand demand,File demand_file) throws IOException{
		DemandPoint current_demand_point;
		String line;
		String[] values;

		Scanner scan = new Scanner(new BufferedReader(new FileReader(demand_file)));
		while (scan.hasNext()) {
			line = scan.nextLine().trim();
			if (line.indexOf("#") == 0)
				continue;
			if (line.indexOf("#") > -1) {
				line = line.substring(0, line.indexOf("#") - 1);
			}
			if (line.contains(";")) {
				values = line.split(";");
				if (values.length != 6) {
					throw new IOException(
							"Wrong number of entries in line!");
				}
				current_demand_point = new DemandPoint(Integer.parseInt(values[0].trim()), // index
						Double.parseDouble(values[3].trim()), // x-coordinate
						Double.parseDouble(values[4].trim()), // y-coordinate
						Integer.parseInt(values[5].trim()));
				demand.addDemandPoint(current_demand_point);
			}
		}
		scan.close();
	}
}

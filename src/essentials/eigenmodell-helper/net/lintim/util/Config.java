package net.lintim.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@SuppressWarnings("JavaDoc")
public class Config {
	
	private static final TreeMap<String, String> data = new TreeMap<>();
	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static final TreeSet<Integer> lines_with_errors = new TreeSet<>();

	public static void readConfig(File sourceFile) throws IOException{

	    readConfig(sourceFile, false);

	}
	
	private static void readConfig(File sourceFile, Boolean only_if_exists) throws IOException{
		try{
			System.out.println("Read file " + sourceFile.getAbsolutePath());
			BufferedReader rd = new BufferedReader(new FileReader(sourceFile));

		    String line;
		    String filename = sourceFile.getName();

		    for (Integer line_counter = 0; (line = rd.readLine()) != null; line_counter++) {
			    
			    line_counter++;
			    
			    if(line.startsWith("#") || line.trim().length() == 0){
				    continue;
			    }
			    
			    String[] fragments = line.split(";", 2);
			    
			    if(fragments.length != 2){
				    
				    System.err.println("Error in line " + line_counter + 
						    ", file " + filename + 
						    ": malformed input, skipping line.");
				    
				    lines_with_errors.add(line_counter);

				    continue;
			    }
			    
			    String name = fragments[0].trim();
			    String value = fragments[1].trim();
			    
			    if(value.length() >= 2 && value.charAt(0) == '"' 
				    && value.charAt(value.length()-1) == '"'){
			    
				    value = value.substring(1, value.length()-1);
			    }

			    switch (name) {
				    case "include":

					    readConfig(new File(sourceFile.getParent() + File.separator +
							    value));

					    break;
				    case "include_if_exists":
					    File toInclude = new File(sourceFile.getParent() +
							    File.separator + value);

					    if (toInclude.exists()) {
						    readConfig(toInclude);
					    }

					    break;
				    default:

					    data.put(name, value);

					    break;
			    }

		    }
		    
		    rd.close();
		} catch(IOException e){
		    if(only_if_exists){
		        return;
		    }
		    throw e;
		}
	}

	public static String getStringValue(String name){
		if (name.equals("default_activities_periodic_file")
			&& data.keySet().contains("use_buffered_activities")
			&& getBooleanValue("use_buffered_activities"))
				name = "default_activity_buffer_file";
		if (name.equals("default_activities_periodic_unbuffered_file")
			&& data.keySet().contains("use_buffered_activities")
			&& getBooleanValue("use_buffered_activities")){
				name = "default_activity_relax_file";
		} else if(name.equals("default_activities_periodic_unbuffered_file") && (!data.keySet().contains("use_buffered_activities") || !getBooleanValue("use_buffered_activities"))) {
			name = "default_activities_periodic_file";
		}
		String value = data.get(name);
		if(value == null){
			throw new IllegalArgumentException("The given key " + name + " was not found in the Config. Maybe the config " +
					"was not read yet?");
		}
		return value;
	}

	public static Double getDoubleValue(String name){
		if(!data.keySet().contains(name)){
			throw new IllegalArgumentException("The given key " + name + " was not found in the Config. Maybe the config " +
					"was not read yet?");
		}
		return Double.parseDouble(data.get(name));
	}

	public static Boolean getBooleanValue(String name){
		if(!data.keySet().contains(name)){
			throw new IllegalArgumentException("The given key " + name + " was not found in the Config. Maybe the config " +
					"was not read yet?");
		}
		return Boolean.parseBoolean(data.get(name));
	}
	
	public static Integer getIntegerValue(String name){
		if(!data.keySet().contains(name)){
			throw new IllegalArgumentException("The given key " + name + " was not found in the Config. Maybe the config " +
					"was not read yet?");
		}
		return Integer.parseInt(data.get(name));
	}

	// For debugging only.
	static void dumpConfiguration(){
		for(Map.Entry<String, String> e : data.entrySet()){
			
			System.out.println(e.getKey() + "; " + e.getValue());
			
		}
	}
	
}

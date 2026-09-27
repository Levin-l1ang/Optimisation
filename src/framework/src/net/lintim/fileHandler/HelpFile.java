package net.lintim.fileHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class HelpFile extends File {

	private static final long serialVersionUID = 1L;
	
	public HelpFile(String pathname) {
		super(pathname);
	}

	public HashMap<String, String> read() {
		HashMap<String, String> mapOfMainHelpCalls = new HashMap<String, String>();
		if (this.exists()) {
			try {
				String line = null;
				String helpCall = null;
				String helpMessage = null;
				BufferedReader in = new BufferedReader(new FileReader(this));
				while ((line = in.readLine()) != null) {
					if (!line.startsWith("#")) {
						if (line.trim().matches("[\\w\\-\\_]+[:]{1}")) {
							if (helpCall != null) {
								mapOfMainHelpCalls.put(helpCall, helpMessage.replaceAll("@newline",System.getProperty("line.separator")));
								helpMessage = null;
							}
							helpCall = line.replaceAll(":", "");
						} else {
							if (helpMessage == null) {
								helpMessage = line.trim();
							} else {
								helpMessage = helpMessage + " " + line.trim();
							}
						}
					}
				}
				mapOfMainHelpCalls.put(helpCall, helpMessage);
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mapOfMainHelpCalls;
	}
}

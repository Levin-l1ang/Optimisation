package net.lintim.fileHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ErrorFile extends File {

	private static final long serialVersionUID = 1L;

	public ErrorFile(String pathname) {
		super(pathname);
	}

	public HashMap<String, String> read() {
		HashMap<String, String> mapOfErrorMessages = new HashMap<String, String>();
		if (this.exists()) {
			try {
				String line = null;
				String errorCall = null;
				String errorMessage = null;
				BufferedReader in = new BufferedReader(new FileReader(this));
				while ((line = in.readLine()) != null) {
					if (!line.startsWith("#")) {
						if (line.trim().matches("[\\w\\-\\_]+[:]{1}")) {
							if (errorCall != null) {
								mapOfErrorMessages.put(errorCall, errorMessage);
								errorMessage = null;
							}
							errorCall = line.replaceAll(":", "");
						} else {
							if (errorMessage == null) {
								errorMessage = line.trim();
							} else {
								errorMessage = errorMessage + " " + line.trim();
							}
						}
					}
				}
				mapOfErrorMessages.put(errorCall, errorMessage);
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mapOfErrorMessages;
	}
}

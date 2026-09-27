package net.lintim.completor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jline.SimpleCompletor;
import net.lintim.instance.LintimInstance;

public class MakeCompletor {

	private List<SimpleCompletor> makeListCompletor;

	// Constructs the completor for the make-command
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public MakeCompletor() {
		makeListCompletor = new LinkedList();
		makeListCompletor.add(new SimpleCompletor(new String[] { "make" }));
		
		// Read make file, in case instance is already set, we have a Makefile
		File makeFile = null;
		if (!LintimInstance.getLintimInstance().isEmpty()) {
			makeFile = new File("datasets/" + LintimInstance.getLintimInstance() + "/Makefile");
		}

		ArrayList<String> completingCommandsArrayList = new ArrayList<String>();
		String input;
		try {
			if (makeFile != null) {
				// Read Makefile
				BufferedReader in = new BufferedReader(new FileReader(makeFile));
				while ((input = in.readLine()) != null) {
					// Read make commands 
					if (input.trim().endsWith(":") && !input.startsWith("#") && !input.startsWith("	")) {
						completingCommandsArrayList.add(input.substring(0, input.indexOf(':')));
					}
				}
				in.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Add read make commands to string array.
		String[] completingCommands = new String[completingCommandsArrayList.size()];
		for (String command : completingCommandsArrayList) {
			completingCommands[completingCommandsArrayList.indexOf(command)] = command;
		}
		makeListCompletor.add(new SimpleCompletor(completingCommands));
	}

	public List<SimpleCompletor> getMakeListCompletor() {
		return makeListCompletor;
	}

	public void setMakeListCompletor(List<SimpleCompletor> makeListCompletor) {
		this.makeListCompletor = makeListCompletor;
	}

}

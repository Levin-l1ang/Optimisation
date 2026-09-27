package net.lintim.completor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jline.SimpleCompletor;
import net.lintim.input.FileInput;

public class HelpCompletor {

	private List<SimpleCompletor> makeHelpListCompletor;
	private ArrayList<List<SimpleCompletor>> paramSettingHelpListCompletor;

	// Constructs the completor for the help command
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public HelpCompletor() {
		makeHelpListCompletor = new LinkedList();
		paramSettingHelpListCompletor = new ArrayList<List<SimpleCompletor>>();
		makeHelpListCompletor.add(new SimpleCompletor(new String[] { "help" }));

		// Read make help file
		ArrayList<String> completingMakeHelpCommands = new ArrayList<String>();

		makeHelpListCompletor.add(new SimpleCompletor(new String[] { "make" }));

		// Add make help calls to completor
		Iterator<String> makeHelpCallIterator = FileInput.getMapOfMakeHelpCalls().keySet().iterator();
		while (makeHelpCallIterator.hasNext()) {
			String makeHelpCall = makeHelpCallIterator.next();
			if (!makeHelpCall.startsWith("information") && !makeHelpCall.startsWith("make-target") && !makeHelpCall.startsWith("model") && !makeHelpCall.startsWith("config"))
				completingMakeHelpCommands.add(makeHelpCall);
		}
		completingMakeHelpCommands.add("");
		makeHelpListCompletor.add(new SimpleCompletor(completingMakeHelpCommands.toArray(new String[completingMakeHelpCommands.size()])));

		for (int paramSettingCounter = 0; paramSettingCounter < FileInput.mapOfParamHelpCalls.size(); paramSettingCounter++) {
			paramSettingHelpListCompletor.add(paramSettingCounter, new LinkedList());
			paramSettingHelpListCompletor.get(paramSettingCounter).add(new SimpleCompletor(new String[] { "help" }));
			paramSettingHelpListCompletor.get(paramSettingCounter).add(new SimpleCompletor(new String[] { "param" }));
		}

		ArrayList<ArrayList<String>> completingParamSettingHelp = new ArrayList<ArrayList<String>>();

		// Read Param Help Files
		String[] completingParamHelpCommands = new String[FileInput.getMapOfParamHelpCalls().size() + 1];

		// Add param help calls to completor
		Iterator<String> paramHelpCallIterator = FileInput.getMapOfParamHelpCalls().keySet().iterator();
		int completorArrayIterator = 0;
		boolean paramSettingSet = false;
		String[] helpParamLineArray = null;
		while (paramHelpCallIterator.hasNext()) {
			String paramHelpCall = paramHelpCallIterator.next();
			completingParamHelpCommands[completorArrayIterator] = paramHelpCall;
			paramSettingHelpListCompletor.get(completorArrayIterator).add(new SimpleCompletor(paramHelpCall));
			completingParamSettingHelp.add(completorArrayIterator, new ArrayList<String>());
			paramSettingSet = false;
			for (String helpParamLine : FileInput.getMapOfParamHelpCalls().get(paramHelpCall)) {
				helpParamLineArray = helpParamLine.split(";");
				if (helpParamLineArray.length > 1 && FileInput.getMapOfParamSettingHelpCalls().containsKey(helpParamLineArray[1].trim())) {
					paramSettingSet = true;
					completingParamSettingHelp.get(completorArrayIterator).add(helpParamLineArray[1].trim());
				}
			}
			if (paramSettingSet) {
				paramSettingHelpListCompletor.get(completorArrayIterator).add(
						new SimpleCompletor(completingParamSettingHelp.get(completorArrayIterator).toArray(new String[completingParamSettingHelp.get(completorArrayIterator).size()])));
			}
			completorArrayIterator++;
		}
		completingParamHelpCommands[FileInput.getMapOfParamHelpCalls().size()] = "";
	}

	public List<SimpleCompletor> getMakeHelpListCompletor() {
		return makeHelpListCompletor;
	}

	public void setMakeHelpListCompletor(List<SimpleCompletor> makeHelpListCompletor) {
		this.makeHelpListCompletor = makeHelpListCompletor;
	}

	public ArrayList<List<SimpleCompletor>> getParamSettingHelpListCompletor() {
		return paramSettingHelpListCompletor;
	}

	public void setParamSettingHelpListCompletor(ArrayList<List<SimpleCompletor>> paramSettingHelpListCompletor) {
		this.paramSettingHelpListCompletor = paramSettingHelpListCompletor;
	}

}

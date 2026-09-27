package net.lintim.consoleListener;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import jline.ArgumentCompletor;
import jline.ConsoleReader;
import jline.History;
import jline.MultiCompletor;
import jline.SimpleCompletor;
import net.lintim.command.HelpCommand;
import net.lintim.command.MakeCommand;
import net.lintim.command.ParamCommand;
import net.lintim.command.UseCommand;
import net.lintim.completor.HelpCompletor;
import net.lintim.completor.MakeCompletor;
import net.lintim.completor.ParamCompletor;
import net.lintim.completor.UseCompletor;
import net.lintim.input.FileInput;
import net.lintim.instance.LintimInstance;

public class KeyConsoleListener {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public KeyConsoleListener() throws IOException {
		ConsoleReader reader = new ConsoleReader();
		reader.setBellEnabled(false);

		// Construct Completor
		UseCompletor useCompletor = new UseCompletor();
		HelpCompletor helpCompletor = new HelpCompletor();
		ParamCompletor paramCompletor = new ParamCompletor();

		List completors = new LinkedList();
		completors.add(new ArgumentCompletor(useCompletor.getUseListCompletor()));
		completors.add(new ArgumentCompletor(helpCompletor.getMakeHelpListCompletor()));
		for(List<SimpleCompletor> list: helpCompletor.getParamSettingHelpListCompletor()){
			completors.add(new ArgumentCompletor(list));
		}
		completors.add(new ArgumentCompletor(paramCompletor.getParamListCompletor()));
		reader.addCompletor(new MultiCompletor(completors));

		// Associate history with current reader
		History history = new History(new File("history.log"));
		reader.setHistory(history);

		// Construct commmand instances
		HelpCommand helpCommand = null;
		MakeCommand makeCommand = null;
		UseCommand useCommand = null;
		ParamCommand paramCommand = null;
		
		String inputString = null;
		while ((inputString = reader.readLine(LintimInstance.getLintimInstance() + ">")) != null) {
			inputString = inputString.trim();
			history.addToHistory(inputString);

			// input needs to start with valid input
			if (inputString.startsWith("exit") || inputString.startsWith("quit") || inputString.startsWith("make") || inputString.startsWith("help") || inputString.startsWith("param")
					|| inputString.startsWith("use")) {
				
				// direct input to certain handler/classes
				if (inputString.equals("exit") || inputString.equals("quit")) {
					System.out.println(FileInput.getMapOfMainHelpCalls().get("exit-help"));
					break;
				} else if (inputString.trim().startsWith("help")) {
					helpCommand = new HelpCommand(inputString);
					helpCommand.execute();
				} else if (inputString.trim().startsWith("make")) {
					makeCommand = new MakeCommand(inputString);
					makeCommand.execute();
				} else if (inputString.trim().startsWith("param")) {
					paramCommand = new ParamCommand(inputString);
					paramCommand.execute();
				} else if (inputString.trim().startsWith("use")) {
					if(!LintimInstance.getLintimInstance().equals("")) {
						paramCommand = new ParamCommand("allow_keylistener ");
						paramCommand.unsetStateParam();
					}
					useCommand = new UseCommand(inputString);
					useCommand.execute();
					paramCommand = new ParamCommand("allow_keylistener false");
					paramCommand.setStateParam();
					FileInput input = new FileInput();
					input.readParameterNew();
					
					// After changing working instance renew completors
					MakeCompletor makeCompletor = new MakeCompletor();
					completors.add(new ArgumentCompletor(makeCompletor.getMakeListCompletor()));
					helpCompletor = new HelpCompletor();
					completors.add(new ArgumentCompletor(helpCompletor.getMakeHelpListCompletor()));
//					completors.add(new ArgumentCompletor(helpCompletor.getParamHelpListCompletor()));
					for(List<SimpleCompletor> list: helpCompletor.getParamSettingHelpListCompletor()){
						completors.add(new ArgumentCompletor(list));
					}
					paramCompletor = new ParamCompletor();
					completors.add(new ArgumentCompletor(paramCompletor.getParamListCompletor()));
					reader.addCompletor(new MultiCompletor(completors));
				} else {
					System.out.println(FileInput.getMapOfErrorMessages().get("no-valid-input"));
				}
			} else {
				System.out.println(FileInput.getMapOfErrorMessages().get("no-valid-input"));

			}
		}
		if(!LintimInstance.getLintimInstance().equals("")) {
			paramCommand = new ParamCommand("allow_keylistener ");
			paramCommand.unsetStateParam();
		}
	}
}

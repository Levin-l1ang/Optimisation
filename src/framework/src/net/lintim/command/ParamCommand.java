package net.lintim.command;

import net.lintim.fileHandler.ConfigFile;
import net.lintim.fileHandler.OutputFile;
import net.lintim.input.FileInput;
import net.lintim.instance.LintimInstance;

public class ParamCommand {

	String command;

	public ParamCommand(String command) {
		this.command = command;
	}

	public void execute() {
		OutputFile writeFile = new OutputFile("output.log");
		writeFile.appendln(this.command);
		this.command = command.replaceAll("param", "").trim();
		if (this.command.startsWith("read")) {
			this.command = command.replaceAll("read", "").trim();
			System.out.println(FileInput.getMapOfParameter().get(this.command));
			writeFile.appendln(FileInput.getMapOfParameter().get(this.command));
		} else if (this.command.startsWith("write")) {
			this.command = command.replaceAll("write", "").trim();
			if (command.contains(" ") && FileInput.mapOfParameter.containsKey(command.substring(0, command.indexOf(" ")).trim())) {
				ConfigFile configFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/Private-Config.cnf");
				configFile.writeParameter(command.substring(0, command.indexOf(" ")).trim(), command.substring(command.indexOf(" ")).trim());
				FileInput input = new FileInput();
				input.readParameterNew();
				System.out.println("\"" + command.substring(0, command.indexOf(" ")).trim() + "\" is set to \"" + command.substring(command.indexOf(" ")).trim() + "\".");
				writeFile.appendln("\""+command.substring(0, command.indexOf(" ")).trim() + "\" is set to \"" + command.substring(command.indexOf(" ")).trim() + "\".");
			} else {
				System.out.println("\"" + this.command + "\" " + FileInput.mapOfErrorMessages.get("no-valid-param-read-write-call"));
				writeFile.appendln("\"" + this.command + "\" " + FileInput.mapOfErrorMessages.get("no-valid-param-read-write-call"));
			}
		} else {
			System.out.println("\"" + this.command + "\" " + FileInput.mapOfErrorMessages.get("no-valid-param-call"));
			writeFile.appendln("\"" + this.command + "\" " + FileInput.mapOfErrorMessages.get("no-valid-param-call"));
		}
		writeFile.closeWriter();
	}
	
	public void setStateParam(){
		ConfigFile stateConfigFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/State-Config.cnf");
		stateConfigFile.writeParameter(command.substring(0, command.indexOf(" ")).trim(), command.substring(command.indexOf(" ")).trim());
	}
	
	public void unsetStateParam(){
		if(FileInput.mapOfParameter.containsKey(command.substring(0, command.indexOf(" ")).trim())) {
			ConfigFile stateConfigFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/State-Config.cnf");
			stateConfigFile.unsetParameter(command.substring(0, command.indexOf(" ")).trim());
		}
	}
}

package net.lintim.command;

import java.io.File;

import net.lintim.fileHandler.OutputFile;
import net.lintim.input.FileInput;
import net.lintim.instance.LintimInstance;

public class UseCommand {

	public String command;

	public UseCommand(String useCommand) {
		this.command = useCommand.trim();
	}

	public void execute() {
		OutputFile writeFile = new OutputFile("output.log");
		writeFile.appendln(this.command + "");
		this.command = command.replace("use", "").trim();
		File instanceFolder = new File("datasets/" + this.command);
		if (instanceFolder.exists()) {
			// Change working intance
			writeFile.appendln("working instance is set to \"" + this.command + "\".");
			LintimInstance.setLintimInstance(command);
		} else {
			writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-instance"));
			System.out.println(("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-instance")));
		}		
		writeFile.closeWriter();
	}

}

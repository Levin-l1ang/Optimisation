package net.lintim.command;

import net.lintim.fileHandler.OutputFile;
import net.lintim.input.FileInput;

public class HelpCommand {

	String command;

	public HelpCommand(String command) {
		this.command = command;
	}

	public void execute() {

		this.command = command.replaceAll("help", "").trim();
		OutputFile writeFile = new OutputFile("output.log");
		writeFile.appendln("help " + command);
		if (this.command.isEmpty()) {
			System.out.println(FileInput.getMapOfMainHelpCalls().get("main-help"));
			writeFile.appendln(FileInput.getMapOfMainHelpCalls().get("main-help"));
		} else if (this.command.startsWith("make")) {
			this.command = command.replaceAll("make", "").trim();
			if (this.command.isEmpty()) {
				System.out.println(FileInput.getMapOfMainHelpCalls().get("make-help"));
				writeFile.appendln(FileInput.getMapOfMainHelpCalls().get("make-help"));
			} else {
				if (FileInput.getMapOfMakeHelpCalls().containsKey(this.command)) {
					System.out.println(FileInput.getMapOfMakeHelpCalls().get(this.command));
					writeFile.appendln(FileInput.getMapOfMakeHelpCalls().get(this.command));
					if (FileInput.getMapOfMakeTargetParams().get(this.command) != null) {
						for (String helpLine : FileInput.getMapOfMakeTargetParams().get(this.command)) {
							System.out.println(helpLine);
							writeFile.appendln(helpLine);
						}
					}
				} else {
					System.out.println("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-make-target"));
					writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-make-target"));
				}
			}
		} else if (this.command.startsWith("param")) {
			this.command = command.replaceAll("param", "").trim();
			if (this.command.isEmpty()) {
				System.out.println(FileInput.getMapOfMainHelpCalls().get("param-help"));
				writeFile.appendln(FileInput.getMapOfMainHelpCalls().get("param-help"));
			} else {
				if (this.command.indexOf(" ") == -1) {
					if (FileInput.getMapOfParamHelpCalls().get(this.command) != null) {
						for (String helpLine : FileInput.getMapOfParamHelpCalls().get(this.command)) {
							System.out.println(helpLine);
							writeFile.appendln(helpLine);
						}
					} else {
						System.out.println("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-param-call"));
						writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-param-call"));
					}
				} else {
					this.command = this.command.substring(this.command.indexOf(" ")).trim();
					if (FileInput.getMapOfParamSettingHelpCalls().get(this.command) != null) {
						for (String helpLine : FileInput.getMapOfParamSettingHelpCalls().get(this.command)) {
							System.out.println(helpLine);
							writeFile.appendln(helpLine);
						}
					} else {
						System.out.println("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-param-call"));
						writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-param-call"));
					}
				}

			}
		}else if (this.command.startsWith("use")) {
			this.command = command.replaceAll("use", "").trim();
			if (this.command.isEmpty()) {
				System.out.println(FileInput.getMapOfMainHelpCalls().get("use-help"));
				writeFile.appendln(FileInput.getMapOfMainHelpCalls().get("use-help"));
			} else {
				System.out.println("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-use-call"));
				writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-use-call"));
			}
		} else {
			System.out.println("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-help-call"));
			writeFile.appendln("\"" + this.command + "\" " + FileInput.getMapOfErrorMessages().get("no-valid-help-call"));
		}
		writeFile.closeWriter();
	}
}

package net.lintim.command;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import net.lintim.fileHandler.OutputFile;
import net.lintim.instance.LintimInstance;

public class MakeCommand {

	String command;

	public MakeCommand(String makeCommand) {
		this.command = makeCommand;
	}

	public void execute() {
		try {
			// Execute process
			Process process = Runtime.getRuntime().exec(command, null, new File("datasets/" + LintimInstance.getLintimInstance()));

			String line = null;

			// Write output of process to console
			BufferedReader inputReCo = new BufferedReader(new InputStreamReader(process.getInputStream()));
			BufferedReader inputErCo = new BufferedReader(new InputStreamReader(process.getErrorStream()));

			OutputFile writeFile = new OutputFile("output.log");

			while ((line = inputReCo.readLine()) != null) {
				System.out.println(line);
				writeFile.appendln(line);
			}
			while ((line = inputErCo.readLine()) != null) {
				System.out.println(line);
				writeFile.appendln(line);
			}
			process.waitFor();
			writeFile.closeWriter();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

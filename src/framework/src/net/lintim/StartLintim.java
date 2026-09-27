package net.lintim;

import java.io.File;
import java.io.IOException;

import net.lintim.consoleListener.KeyConsoleListener;
import net.lintim.dateAndTime.DateUtils;
import net.lintim.fileHandler.OutputFile;
import net.lintim.input.FileInput;

public class StartLintim {

	public static void main(String[] args) {

		// Read all Input Files such as error messages, help messages, interface
		// files
		FileInput input = new FileInput();
		input.readAllMessages();
		
		// Delete History and Output Files
		File history = new File("history.log");
		history.delete();
		File output = new File("output.log");
		output.delete();

		OutputFile writeFile = new OutputFile("output.log");
		writeFile.appendln("********** New LinTim session started: " + DateUtils.now());
		writeFile.closeWriter();

		System.out.println(FileInput.getMapOfMainHelpCalls().get("entry-help"));

		try {
			// Associate a console listener to the session
			@SuppressWarnings("unused")
			KeyConsoleListener keyConsoleListener = new KeyConsoleListener();
		} catch (IOException e) {
			System.out.println(FileInput.getMapOfErrorMessages().get("not-working"));
		}
	}

}

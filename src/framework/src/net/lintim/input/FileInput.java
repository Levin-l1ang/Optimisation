package net.lintim.input;

import java.util.ArrayList;
import java.util.HashMap;

import net.lintim.fileHandler.ConfigFile;
import net.lintim.fileHandler.ErrorFile;
import net.lintim.fileHandler.HelpFile;
import net.lintim.fileHandler.InterfaceFile;
import net.lintim.instance.LintimInstance;

public class FileInput {

	public static HashMap<String, ArrayList<String>> mapOfMakeTargetParams;
	public static HashMap<String, ArrayList<String>> mapOfParamHelpCalls;
	public static HashMap<String, ArrayList<String>> mapOfParamSettingHelpCalls;
	public static HashMap<String, String> mapOfMainHelpCalls;
	public static HashMap<String, String> mapOfMakeHelpCalls;
	public static HashMap<String, String> mapOfErrorMessages;
	public static HashMap<String, String> mapOfParameter;

	public FileInput() {

	}

	public void readAllMessages() {
		HelpFile mainHelpFile = new HelpFile("src/framework/helpfiles/mainhelp.txt");
		FileInput.mapOfMainHelpCalls = mainHelpFile.read();

		HelpFile makeHelpFile = new HelpFile("src/framework/helpfiles/makehelp.txt");
		FileInput.mapOfMakeHelpCalls = makeHelpFile.read();

		ErrorFile errorFile = new ErrorFile("src/framework/helpfiles/errormessage.txt");
		FileInput.mapOfErrorMessages = errorFile.read();

		InterfaceFile paramFile = new InterfaceFile("src/make-src-mapping.cfg");
		FileInput.mapOfParamHelpCalls = new HashMap<String, ArrayList<String>>();
		FileInput.mapOfParamHelpCalls = paramFile.appendParamHelp(FileInput.mapOfParamHelpCalls);
		
		FileInput.mapOfParamSettingHelpCalls = new HashMap<String, ArrayList<String>>();
		FileInput.mapOfParamSettingHelpCalls = paramFile.appendParamSettingHelp(FileInput.mapOfParamSettingHelpCalls);

		FileInput.mapOfMakeTargetParams = new HashMap<String, ArrayList<String>>();
		FileInput.mapOfMakeTargetParams = paramFile.appendMakeTargetParam(FileInput.mapOfMakeTargetParams);

		ConfigFile configFile = new ConfigFile("datasets/Global-Config.cnf");
		FileInput.mapOfParameter = new HashMap<String, String>();
		FileInput.mapOfParameter = configFile.appendConfigFile(mapOfParameter);
	}

	public void readParameterNew() {
		if (!LintimInstance.getLintimInstance().isEmpty()) {
			FileInput.mapOfParameter = new HashMap<String, String>();
			ConfigFile globalConfigFile = new ConfigFile("datasets/Global-Config.cnf");
			FileInput.mapOfParameter = globalConfigFile.appendConfigFile(mapOfParameter);
			ConfigFile configFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/Config.cnf");
			FileInput.mapOfParameter = configFile.appendConfigFile(mapOfParameter);
			ConfigFile stateConfigFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/State-Config.cnf");
			FileInput.mapOfParameter = stateConfigFile.appendConfigFile(mapOfParameter);
			ConfigFile privateConfigFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/Private-Config.cnf");
			FileInput.mapOfParameter = privateConfigFile.appendConfigFile(mapOfParameter);
			ConfigFile afterConfigFile = new ConfigFile("datasets/" + LintimInstance.getLintimInstance() + "/basis/After-Config.cnf");
			FileInput.mapOfParameter = afterConfigFile.appendConfigFile(mapOfParameter);
		}
	}

	public static HashMap<String, ArrayList<String>> getMapOfMakeTargetParams() {
		return mapOfMakeTargetParams;
	}

	public static void setMapOfMakeTargetParams(HashMap<String, ArrayList<String>> mapOfMakeTargetParams) {
		FileInput.mapOfMakeTargetParams = mapOfMakeTargetParams;
	}

	public static HashMap<String, ArrayList<String>> getMapOfParamHelpCalls() {
		return mapOfParamHelpCalls;
	}

	public static void setMapOfParamHelpCalls(HashMap<String, ArrayList<String>> mapOfParamHelpCalls) {
		FileInput.mapOfParamHelpCalls = mapOfParamHelpCalls;
	}

	public static HashMap<String, String> getMapOfMainHelpCalls() {
		return mapOfMainHelpCalls;
	}

	public static void setMapOfMainHelpCalls(HashMap<String, String> mapOfMainHelpCalls) {
		FileInput.mapOfMainHelpCalls = mapOfMainHelpCalls;
	}

	public static HashMap<String, String> getMapOfMakeHelpCalls() {
		return mapOfMakeHelpCalls;
	}

	public static void setMapOfMakeHelpCalls(HashMap<String, String> mapOfMakeHelpCalls) {
		FileInput.mapOfMakeHelpCalls = mapOfMakeHelpCalls;
	}

	public static HashMap<String, String> getMapOfErrorMessages() {
		return mapOfErrorMessages;
	}

	public static void setMapOfErrorMessages(HashMap<String, String> mapOfErrorMessages) {
		FileInput.mapOfErrorMessages = mapOfErrorMessages;
	}

	public static HashMap<String, String> getMapOfParameter() {
		return mapOfParameter;
	}

	public static void setMapOfParameter(HashMap<String, String> mapOfParameter) {
		FileInput.mapOfParameter = mapOfParameter;
	}

	public static HashMap<String, ArrayList<String>> getMapOfParamSettingHelpCalls() {
		return mapOfParamSettingHelpCalls;
	}

	public static void setMapOfParamSettingHelpCalls(HashMap<String, ArrayList<String>> mapOfParamSettingHelpCalls) {
		FileInput.mapOfParamSettingHelpCalls = mapOfParamSettingHelpCalls;
	}
	
	

}

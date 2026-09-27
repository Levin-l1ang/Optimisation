package net.lintim.fileHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class InterfaceFile extends File {

	private static final long serialVersionUID = 1L;

	public InterfaceFile(String pathname) {
		super(pathname);
	}

	public HashMap<String, ArrayList<String>> appendMakeTargetParam(HashMap<String, ArrayList<String>> mapOfMakeTargetParams) {
		if (this.exists()) {
			try {
				String line = null;
				String[] stringArray;
				BufferedReader in = new BufferedReader(new FileReader(this));
				while ((line = in.readLine()) != null) {
					if (!line.startsWith("#") && !line.trim().isEmpty()) {
						line = line.replaceAll("\"", "");
						stringArray = line.split(";");
						if (mapOfMakeTargetParams.get(stringArray[0].trim()) == null) {
							mapOfMakeTargetParams.put(stringArray[0].trim(), new ArrayList<String>());
						}
						if (mapOfMakeTargetParams.get(stringArray[0].trim()).size() == 0) {
							mapOfMakeTargetParams.get(stringArray[0].trim()).add("information read from " + this.getCanonicalPath());
							mapOfMakeTargetParams.get(stringArray[0].trim()).add("model param name; src directory; description");
						}
						mapOfMakeTargetParams.get(stringArray[0].trim()).add(stringArray[1].trim() + "; " + stringArray[2].trim() + "; " + stringArray[3].trim());
					}
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mapOfMakeTargetParams;
	}

	public HashMap<String, ArrayList<String>> appendParamHelp(HashMap<String, ArrayList<String>> mapOfParamHelp) {
		if (this.exists()) {
			try {
				String parameter = null;
				String line = null;
				String lineSubFile = null;
				String[] stringArray;
				String[] stringArraySubFile;
				BufferedReader in = new BufferedReader(new FileReader(this));
				BufferedReader inSubFile = null;
				while ((line = in.readLine()) != null) {
					if (!line.startsWith("#") && !line.trim().isEmpty()) {
						line = line.replaceAll("\"", "");
						stringArray = line.split(";");
						parameter = stringArray[1].trim();
						if ((new File(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf")).exists()) {
							inSubFile = new BufferedReader(new FileReader(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf"));
							while ((lineSubFile = inSubFile.readLine()) != null) {
								if (!lineSubFile.startsWith("#") && !lineSubFile.trim().isEmpty()) {
									lineSubFile = lineSubFile.replaceAll("\"", "");
									stringArraySubFile = lineSubFile.split(";");
									if (mapOfParamHelp.get(parameter) == null) {
										mapOfParamHelp.put(parameter, new ArrayList<String>());
										mapOfParamHelp.get(parameter).add(
												"information read from " + this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf");
										mapOfParamHelp.get(parameter).add("make-target; parameter value; src directory; description");
									}
									if (stringArraySubFile.length > 2) {
										mapOfParamHelp.get(parameter).add(
												stringArray[0].trim() + "; " + stringArraySubFile[0].trim() + "; " + stringArray[2].trim() + "/" + stringArraySubFile[1].trim() + "; "
														+ stringArraySubFile[2].trim());
									} else {
										mapOfParamHelp.get(parameter).add(
												stringArray[0].trim() + "; " + stringArraySubFile[0].trim() + "; " + stringArray[2].trim() + "/" + stringArraySubFile[1].trim());
									}
								}
							}
						} else {
							System.out.println("Warning, file not found. In file \"src/make-src-mapping.cfg\" the file "
									+ (this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf") + " is referenced but it does not seem to exist.");
						}
					}
				}
				in.close();
				inSubFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mapOfParamHelp;
	}

	public HashMap<String, ArrayList<String>> appendParamSettingHelp(HashMap<String, ArrayList<String>> mapOfParamConfigHelp) {
		if (this.exists()) {
			try {
				boolean newfile = true;
				String paramSetting = null;
				String line = null;
				String lineSubFile = null;
				String lineSubSubFile = null;
				String[] stringArray;
				String[] stringArraySubFile;
				String[] stringArraySubSubFile;
				BufferedReader in = new BufferedReader(new FileReader(this));
				BufferedReader inSubFile = null;
				BufferedReader inSubSubFile = null;
				while ((line = in.readLine()) != null) {
					if (!line.startsWith("#") && !line.trim().isEmpty()) {
						line = line.replaceAll("\"", "");
						stringArray = line.split(";");
						if ((new File(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf")).exists()) {
							inSubFile = new BufferedReader(new FileReader(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf"));
							while ((lineSubFile = inSubFile.readLine()) != null) {
								if (!lineSubFile.startsWith("#") && !lineSubFile.trim().isEmpty()) {
									lineSubFile = lineSubFile.replaceAll("\"", "");
									stringArraySubFile = lineSubFile.split(";");
									paramSetting = stringArraySubFile[0].trim();
									int counter = 0;
									if (stringArraySubFile[1].trim() != null) {
										for (int interfacefiles = 0; interfacefiles < stringArraySubFile[1].split(",").length; interfacefiles++) {
											if ((new File(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/"
													+ stringArraySubFile[1].split(",")[interfacefiles].trim() + "/interface.cfg")).exists()) {
												newfile = true;
												inSubSubFile = new BufferedReader(new FileReader(this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/"
														+ stringArraySubFile[1].split(",")[interfacefiles].trim() + "/interface.cfg"));
												while ((lineSubSubFile = inSubSubFile.readLine()) != null) {
													if (!lineSubSubFile.startsWith("#") && !lineSubSubFile.trim().isEmpty()) {
														lineSubSubFile = lineSubSubFile.replaceAll("\"", "");
														stringArraySubSubFile = lineSubSubFile.split(";");
														if (mapOfParamConfigHelp.get(paramSetting) == null || newfile) {
															if (mapOfParamConfigHelp.get(paramSetting) == null) {
																mapOfParamConfigHelp.put(paramSetting, new ArrayList<String>());
															}
															mapOfParamConfigHelp.get(paramSetting).add(
																	counter++,
																	"information read from " + this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/"
																			+ stringArraySubFile[1].split(",")[interfacefiles].trim() + "/interface.cfg");
															mapOfParamConfigHelp.get(paramSetting).add(counter++,
																	"config parameter; access (read (file), write (file), use (param)); short description; long description");
															newfile = false;
														}
														if (stringArraySubSubFile.length > 2) {
															mapOfParamConfigHelp.get(paramSetting).add(
																	counter++,
																	stringArraySubSubFile[0].trim() + "; " + stringArraySubSubFile[1].trim() + "; " + stringArraySubSubFile[2].trim() + "; "
																			+ stringArraySubSubFile[3].trim());
														} else {
															mapOfParamConfigHelp.get(paramSetting).add(counter++,
																	stringArraySubSubFile[0].trim() + "; " + stringArraySubSubFile[1].trim() + "; " + stringArraySubSubFile[2].trim());
														}
													}
												}
											} else {
												System.out.println("Warning, file not found. From file \"src/make-src-mapping-cfg\" and file \""
														+ (this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf")
														+ "\" the file "
														+ (this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/"
																+ stringArraySubFile[1].split(",")[interfacefiles].trim() + "/interface.cfg") + " is referenced but it does not seem to exist.");
											}
										}
									}
								}
							}
						} else {
							System.out.println("Warning, file not found. In file \"src/make-src-mapping-cfg\" the file "
									+ (this.getPath().substring(0, this.getPath().lastIndexOf('/') + 1) + stringArray[2].trim() + "/models.cnf") + " is referenced but it does not seem to exist.");
						}
					}
				}
				in.close();
				inSubFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mapOfParamConfigHelp;
	}
}

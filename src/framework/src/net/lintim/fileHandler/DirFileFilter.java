package net.lintim.fileHandler;

import java.io.File;
import java.io.FilenameFilter;

public class DirFileFilter implements FilenameFilter {

	@Override
	public boolean accept(File mainDir, String name) {
		File file = new File(mainDir + "/" + name);
		return (file.isDirectory() && !file.getName().startsWith("."));
	}

}

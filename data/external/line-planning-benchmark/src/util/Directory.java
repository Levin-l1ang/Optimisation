package util;

import java.io.File;
import java.io.FilenameFilter;

public class Directory
{
	public static String[] getDirectories(String folderName)
	{
		File folder = new File(folderName);
		String[] directories = folder.list(new FilenameFilter()
		{
			@Override
			public boolean accept(File current, String name)
			{
				return new File(current, name).isDirectory();
			}
		});
		return directories;
	}
}

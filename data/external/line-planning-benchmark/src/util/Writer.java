package util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

public class Writer
{
	public static <E> void write(Collection<E> elements, String outputFile) throws IOException
	{
		StringBuilder stringBuilder = new StringBuilder();
		for (E e : elements)
		{
			stringBuilder.append(e + "\n");
		}
		write(stringBuilder.toString(), outputFile);
	}

	public static <E> void write(String output, String outputFile) throws IOException
	{
		BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile));
		bufferedWriter.write(output);
		bufferedWriter.close();
	}

	public static String formatDouble(double value)
	{
		return String.format(Locale.forLanguageTag("en-EN"), "%.4f",
				value);
	}
}

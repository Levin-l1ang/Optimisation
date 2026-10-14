package util;

import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.Properties;

public class Configuration
{
	private final static String DEFAULT_PROPERTIES = "default.properties";

	private static Configuration configuration;
	private Properties properties;

	private Configuration(String propertiesFile)
	{
		properties = new Properties();
		try
		{
			properties.load(new FileInputStream(propertiesFile));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public Properties getProperties()
	{
		return properties;
	}

	public String getStringProperty(String key)
	{
		return properties.getProperty(key);
	}

	public LocalDate getDateProperty(String key)
	{
		return LocalDate.parse(properties.getProperty(key));
	}

	public double getDoubleProperty(String key)
	{
		return Double.parseDouble(properties.getProperty(key));
	}

	public int getIntProperty(String key)
	{
		return Integer.parseInt(properties.getProperty(key));
	}

	public boolean getBooleanProperty(String key)
	{
		return Boolean.parseBoolean(properties.getProperty(key));
	}

	public static Configuration getConfiguration()
	{
		return (configuration == null) ? Configuration.configuration = new Configuration(DEFAULT_PROPERTIES)
				: configuration;
	}

	public static void initialiseConfiguration(String propertiesFile)
	{
		Configuration.configuration = new Configuration(propertiesFile);
	}

	public String getAllProperties()
	{
		StringBuilder sb = new StringBuilder();
		for (String key : properties.stringPropertyNames())
		{
			sb.append(key + " = " + properties.getProperty(key) + "\n");
		}
		return sb.toString();
	}
}

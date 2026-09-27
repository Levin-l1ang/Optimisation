import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * This class requests the screen size.
 */

public class Environment {

	private static int screenSizeX = 0;
	private static int screenSizeY = 0;

	public static int getX() {
		screenSizeX = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
		return screenSizeX;
	}

	public static int getY() {
		screenSizeY = (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() - 60;
		return screenSizeY;
	}

	public void setScreenSizeX(int screenSizeX) {
		Environment.screenSizeX = screenSizeX;
	}

	@SuppressWarnings("static-access")
	public void setScreenSizeY(int screenSizeY) {
		this.screenSizeY = screenSizeY;
	}
}

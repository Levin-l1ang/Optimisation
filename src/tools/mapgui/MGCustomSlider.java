import g4p_controls.*;
import processing.core.PApplet;

public class MGCustomSlider extends GCustomSlider{
	
	public MGCustomSlider(PApplet theApplet, float p0, float p1, float p2, float p3, String string) {
		super(theApplet, p0, p1, p2, p3, string);
	}
	
	@Override
	protected String getNumericDisplayString(float number){
		int hours = (int)number/3600;
		int minutes = ((int)number - hours*3600)/60;
		int seconds = ((int)number - hours*3600 - minutes * 60);
		String s = ""+((hours<10)?("0"+hours):(hours))+":"+((minutes<10)?("0"+minutes):(minutes))+":"+((seconds<10)?("0"+seconds):(seconds));
		return s.trim();
	}

}

import de.fhpotsdam.unfolding.*;
import processing.core.PApplet;
import g4p_controls.*;
import de.fhpotsdam.unfolding.providers.*;

public class CustomUnfoldingMap extends UnfoldingMap{
	
	GPanel panel;
	
	public CustomUnfoldingMap(PApplet p, String id, float x, float y, float width, float height, boolean useMask,boolean useDistortion, AbstractMapProvider provider) {
		super(p, generateId(), x, y, width, height, true, false, provider, null);
		this.panel = null;
	}
	
	public void setPanel(GPanel panel){
		this.panel = panel;
	}

	@Override
	public boolean isHit(float checkX, float checkY) {
		float[] panelCheckTopLeft = new float[]{0,0};
		float[] panelCheckBottomRight = new float[]{0,0};
		if(panel != null){
			 panelCheckTopLeft = mapDisplay.getObjectFromScreenPosition(this.panel.getX(), this.panel.getY());
			 if(panel.isCollapsed())
			 	panelCheckBottomRight = mapDisplay.getObjectFromScreenPosition(this.panel.getX()+this.panel.getWidth(), this.panel.getY() + this.panel.getTabHeight());
			 else
				panelCheckBottomRight = mapDisplay.getObjectFromScreenPosition(this.panel.getX()+this.panel.getWidth(), this.panel.getY() + this.panel.getHeight());
		}
		float[] check = mapDisplay.getObjectFromScreenPosition(checkX, checkY);
		return (check[0] > 0 && check[0] < mapDisplay.getWidth() && check[1] > 0 && check[1] < mapDisplay.getHeight() && !(check[0]>=panelCheckTopLeft[0] && check[0]<=panelCheckBottomRight[0] && check[1]>=panelCheckTopLeft[1] && check[1]<=panelCheckBottomRight[1]));
	}
}

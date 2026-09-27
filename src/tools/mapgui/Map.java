import de.fhpotsdam.unfolding.UnfoldingMap;
import de.fhpotsdam.unfolding.events.EventDispatcher;
import de.fhpotsdam.unfolding.events.PanMapEvent;
import de.fhpotsdam.unfolding.events.ZoomMapEvent;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.interactions.KeyboardHandler;
import de.fhpotsdam.unfolding.interactions.MouseHandler;
import de.fhpotsdam.unfolding.marker.SimplePointMarker;
import de.fhpotsdam.unfolding.providers.OpenStreetMap;
import de.fhpotsdam.unfolding.utils.DebugDisplay;
import de.fhpotsdam.unfolding.utils.ScreenPosition;
import g4p_controls.*;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import org.apache.log4j.Logger;
import processing.core.PApplet;
import processing.core.PFont;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;


public class Map extends PApplet{

	// Map on which everything is based
	CustomUnfoldingMap map;

	public static Logger log = Logger.getLogger(Map.class);

	// Controls
	MGCustomSlider slider;
	GPanel controlPanel;
	GButton stopButton;
	GButton pauseButton;
	GButton forwardButton;
	GButton rewindButton;
	GButton playButton;

	GPanel showPanel;
	ArrayList<GButton> showButtons;

	GPanel linePanel;
	GButton closeButton;
	ArrayList<GButton> lineButtons;

	// Input data, possibly empty
	private static PTN ptn;
	private static LinePool lp;
	private static LinePool lc;
	private static OD od;
	private static NonPeriodicEANetwork nean;
	private static ArrayList<ShadowTrain> shadowTrains;
	private static ArrayList<Train> trains;
	private static ArrayList<FullPeriodicEvent> listOfEvents;

	// Screen positions of all stops, updated at running time
	private static ArrayList<ScreenPosition> screenPositionOfStops;

	// Mouse and key event handler setups

	EventDispatcher eventDispatcher;
	DebugDisplay debugDisplay;

	Location startLocation;
	int startZoom;

	float mouseClickedX = -1;
	float mouseClickedY = -1;
	boolean allLinesHighlighted = true;
	int highlightedLineIndex = 0;

	private Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);

	// Control parameters
	private static int TRAIN_SIZE = 22;
	private static int STOP_SIZE = 10;
	private static int show_step = 0;
	private static int load_step = 0;
	private static int minTime = Integer.MAX_VALUE;
	private static int maxTime = 0;
	private static int currentTime = 0;
	private static double frameSpeed = 10;
	private static int shift = 0;
	private static int capacity;
	private static int width;
	private static int heigth;

	public Map(String dataPath, int width, int height){
		this.width = width;
		this.height = height;
		try{

        System.err.print("Loading Configuration... ");
        Config config = new ConfigReader.Builder(dataPath).build().read();
        System.err.println("done!");

		System.err.print("Loading Input Data...");
		readInput(config);
		System.err.println("done!");

		} catch(Exception e){
			System.out.println(e.toString());
		}
	}

	// All data setup should be computed here! This is only called once at the beginning.
	public void setup() {
		size(width, height, P2D);
		smooth();

		map = new CustomUnfoldingMap(this, "map", 0, 0, width, height, true, false, new OpenStreetMap.OpenStreetMapProvider());
		startLocation = new Location(ptn.getStops().get(0).getX_coordinate(),ptn.getStops().get(0).getY_coordinate());
		startZoom = 9;
		map.zoomAndPanTo(startLocation, startZoom);
		map.setZoomRange(3, 20);

		eventDispatcher = new EventDispatcher();

		//MapUtils.createDefaultEventDispatcher(this, map);
		//MapUtils.createMouseEventDispatcher(this, map);
		PFont font = createFont("serif-bold", 11);
		textFont(font);

		MouseHandler mouseHandler = new MouseHandler(this, map);
		eventDispatcher.addBroadcaster(mouseHandler);
		KeyboardHandler keyboardHandler = new KeyboardHandler(this, map);
		eventDispatcher.addBroadcaster(keyboardHandler);

		eventDispatcher.register(map, "zoom");
		eventDispatcher.register(map, "pan");

		map.setActive(true);

		setUpStops();
		if(show_step==1)
			setUpHighlightingLP(this);
		else if(show_step>1)
			setUpHighlightingLC(this);
		setUpShowPanel(this);

		if(show_step>2){
			setUpTrainData();
			setUpControls(this);
			map.setPanel(controlPanel);
		}
		currentTime = minTime;
	}

	private void setUpShowPanel(PApplet p){
		showPanel = new GPanel(p, 20, 20, 295, 80, "Show Data");
		showPanel.setOpaque(true);
		showPanel.setDraggable(true);
		showPanel.setCollapsed(false);
		map.setPanel(showPanel);
		showButtons = new ArrayList<GButton>();
		showButtons.add(0, new GButton(p, 5,20,90,20,"PTN"));
		if(load_step>0)
			showButtons.add(1, new GButton(p,100,20,90,20,"Linepool"));
		if(load_step>1)
			showButtons.add(2, new GButton(p,200,20,90,20,"Lineconcept"));
		if(load_step>2)
			showButtons.add(3, new GButton(p,5,50,55,20,"Timetable"));
		if(load_step>3)
			showButtons.add(4, new GButton(p,5,50,70,20,"DispoTimetable"));
		for(int count_buttons = 0; count_buttons<load_step+1;count_buttons++){
			showButtons.get(count_buttons).tagNo = count_buttons;
			showPanel.addControl(showButtons.get(count_buttons));
			System.out.println("Button " + showButtons.get(count_buttons).getText() + " added");
		}
	}

	public void handleSliderEvents(GValueControl slider, GEvent event){
		currentTime = slider.getValueI();
	}

	public void handleButtonEvents(GButton button, GEvent event){
		if(button == stopButton)
			currentTime = minTime;
		else if(button == pauseButton)
			frameSpeed = 0;
		else if(button == forwardButton)
			frameSpeed = frameSpeed +2;
		else if(button == rewindButton)
			frameSpeed = frameSpeed - 2;
		else if(button == playButton)
			frameSpeed = 10;
		else if(button.getText()=="x"){
			lineButtons = new ArrayList<GButton>();
			linePanel = ((GPanel)closeButton.getParent());
			linePanel.dispose();
			linePanel = null;
		} else if(button.getText().matches("Line \\d+")){
			if(show_step == 1){
				lp.getLines().get(button.tagNo).setHighlighted(!lp.getLines().get(button.tagNo).isHighlighted());
				if(lp.getLines().get(button.tagNo).isHighlighted()){
				button.setLocalColorScheme(100);
			} else {
				button.setLocalColorScheme(0);
			}
			}else if(show_step>1){
				lc.getLines().get(button.tagNo).setHighlighted(!lc.getLines().get(button.tagNo).isHighlighted());
				if(lc.getLines().get(button.tagNo).isHighlighted()){
				button.setLocalColorScheme(100);
			} else {
				button.setLocalColorScheme(0);
			}
			}

		} else if(showButtons.contains(button)){
			show_step = button.tagNo;
			setUpStopLineAdjacency();
		}
	}

	public void handlePanelEvents(GPanel panel, GEvent event){
		//System.out.println(event.toString());
	}

	private void setUpHighlightingLP(PApplet p){
		lineButtons = new ArrayList<GButton>();
		Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
		Integer lineIndex;
		while(lineIterator.hasNext()){
			lineIndex = lineIterator.next();
			lp.getLines().get(lineIndex).setHighlighted(true);
		}
	}

	private void setUpHighlightingLC(PApplet p){
		lineButtons = new ArrayList<GButton>();
		Iterator<Integer> lineIterator = lc.getLines().keySet().iterator();
		Integer lineIndex;
		while(lineIterator.hasNext()){
			lineIndex = lineIterator.next();
			lc.getLines().get(lineIndex).setHighlighted(true);
		}
	}

	private void setUpStops(){
		Location location = null;
		ScreenPosition sp = null;
		SimplePointMarker spm = null;
		screenPositionOfStops = new ArrayList<ScreenPosition>();
		for(Stop stop:ptn.getStops()){
			location = new Location(stop.getX_coordinate(), stop.getY_coordinate());
			spm = new SimplePointMarker(location);
			sp = spm.getScreenPosition(map);
			screenPositionOfStops.add(stop.getIndex()-1,sp);
		}
	}

	// Sets up data to provide visuals for timetabling and dispotimetabling
	private void setUpTrainData(){
		int trainCount = 0;
		trains = new ArrayList<Train>();
		NonPeriodicEvent currentEvent;
		NonPeriodicActivity currentActivity;
		FullNonPeriodicEvent fnonpeEvent;
		if(nean == null) {
			System.err.println("Cannot find aperiodic EAN");
			System.exit(1);
		}
		for(NonPeriodicEvent ne:nean.getEvents()){
			if(ne.getTime()<minTime){
				minTime = ne.getTime();
			}
			if(ne.getTime()>maxTime){
				maxTime = ne.getTime();
			}
			if(show_step>3 && ne.getDispoTime()<minTime){
				minTime = ne.getTime();
			}
			if(show_step>3 && ne.getDispoTime() >maxTime){
				maxTime = ne.getTime();
			}
			if(ne.getIncomingActivities().size()==0 && listOfEvents.get(ne.getPeriodicParentEventID()-1).getType().equals("\"departure\"")){
				trains.add(trainCount, new Train(trainCount));
				trains.get(trainCount).setCapacity(capacity);
				trains.get(trainCount).addEvent(new FullNonPeriodicEvent(ne, listOfEvents.get(ne.getPeriodicParentEventID()-1)));
				currentEvent = ne;
				currentActivity = null;
				while(currentEvent != null){
					for(NonPeriodicActivity act:currentEvent.getOutgoingActivities()){
						if(act.getType().equals("drive")||act.getType().equals("wait")){
							currentActivity = act;
							trains.get(trainCount).addEvent(new FullNonPeriodicEvent(currentActivity.getTarget(), listOfEvents.get(currentActivity.getTarget().getPeriodicParentEventID()-1)));
							trains.get(trainCount).addActivity(act);
							break;
						}
					currentActivity = null;
					}
					if(currentEvent.getOutgoingActivities().size()==0){
						currentActivity = null;
					}
					if(currentActivity != null)
						currentEvent = currentActivity.getTarget();
					else
						currentEvent = null;
				}
				trainCount++;
			}
		}

		FullNonPeriodicEvent startEvent;
		FullNonPeriodicEvent endEvent;
		NonPeriodicActivity startActivity;
		NonPeriodicActivity endActivity;
		for(Train train:trains){
			startEvent = new FullNonPeriodicEvent(new NonPeriodicEvent(0,train.getEvents().get(0).getTime()-180,0.0,true,0), new FullPeriodicEvent(0,"arrival",train.getEvents().get(0).getFullPeriodicEvent().getFullStation(),train.getEvents().get(0).getFullPeriodicEvent().getLine(),0.0));
			startActivity = new NonPeriodicActivity(0,startEvent,train.getEvents().get(0),3,5,0.0,"wait",0);
			endEvent = new FullNonPeriodicEvent(new NonPeriodicEvent(0,train.getEvents().get(train.getEvents().size()-1).getTime()+180,0.0,true,0), new FullPeriodicEvent(0,"departure",train.getEvents().get(train.getEvents().size()-1).getFullPeriodicEvent().getFullStation(),train.getEvents().get(train.getEvents().size()-1).getFullPeriodicEvent().getLine(),0.0));
			endActivity = new NonPeriodicActivity(0,train.getEvents().get(train.getEvents().size()-1),endEvent,3,5,0.0,"wait",0);
			train.addEvent(startEvent);
			train.addActivity(startActivity);
			train.addEvent(endEvent);
			train.addActivity(endActivity);
			train.initializePosition();
		}

		if(show_step>3){
			shadowTrains = new ArrayList<ShadowTrain>();
			for(Train train:trains){
				shadowTrains.add(train.getIndex(),new ShadowTrain(trains.size()+train.getIndex(),train.getIndex()));
				for(FullNonPeriodicEvent fne:train.getEvents()){
					shadowTrains.get(train.getIndex()).addEvent(fne);
				}
				for(NonPeriodicActivity na:train.getActivities()){
					shadowTrains.get(train.getIndex()).addActivity(na);
				}
				train.setShadowTrain(shadowTrains.get(train.getIndex()));
			}
			for(Train train:shadowTrains){
				train.initializePosition();
			}
		}
	}

	public void setUpControls(PApplet p){
		G4P.setCtrlMode(GControlMode.CORNER);
		controlPanel = new GPanel(p,20,p.height-200,300,180, "Control Panel");
		controlPanel.setOpaque(true);
		controlPanel.setDraggable(true);
		controlPanel.setCollapsed(false);
		slider = new MGCustomSlider(this, 5, 30, 290, 50, "blue18px");
		slider.setShowDecor(false, true, false, true);
		slider.setNumberFormat(G4P.DECIMAL, 3);
		slider.setLimits(1.0f, minTime, maxTime);
		slider.setShowValue(true);
		rewindButton = new GButton(p,5,100,55,20,"rewind");
		stopButton = new GButton(p,65,100,55,20,"stop");
		pauseButton = new GButton(p,125,100,55,20,"pause");
		playButton = new GButton(p,185,100,55,20,"play");
		forwardButton = new GButton(p,245,100,55,20,"forward");
		controlPanel.addControl(slider);
		controlPanel.addControl(rewindButton);
		controlPanel.addControl(stopButton);
		controlPanel.addControl(pauseButton);
		controlPanel.addControl(playButton);
		controlPanel.addControl(forwardButton);
	}

	// All drawn elements must be indicated here. This is called for every frame.

	public void draw() {
		map.draw();
		if(show_step==0)
			drawEdges();
		else{
			checkMouseClicked(this);
			drawLineEdges();
		}
		drawStops();

		currentTime = (int)(currentTime + frameSpeed);
		if(show_step>2)
		{
			slider.setValue(currentTime);
			drawTrains(currentTime);
		}
	}

	private void checkMouseClicked(PApplet p){
		Stop savedStop = null;
		boolean createLineChooserMenu = false;
		for(Stop stop:ptn.getStops()){
			if(screenPositionOfStops.get(stop.getIndex()-1).x-STOP_SIZE/2 <= mouseClickedX && mouseClickedX <= screenPositionOfStops.get(stop.getIndex()-1).x + STOP_SIZE/2 && screenPositionOfStops.get(stop.getIndex()-1).y-STOP_SIZE/2 <= mouseClickedY && mouseClickedY <= screenPositionOfStops.get(stop.getIndex()-1).y + STOP_SIZE/2){
				createLineChooserMenu = true;
				savedStop = stop;
				break;
			}
		}
		if(createLineChooserMenu)
			drawLineChooserMenu(p, savedStop);
	}

	private void drawLineChooserMenu(PApplet p, Stop stop){
		strokeWeight(3);
		G4P.setCtrlMode(GControlMode.CORNER);
		//Lower Left Part of Screen
		if(0 <= screenPositionOfStops.get(stop.getIndex()-1).x && screenPositionOfStops.get(stop.getIndex()-1).x <= Environment.getX()/2 && Environment.getY()/2 <= screenPositionOfStops.get(stop.getIndex()-1).y && screenPositionOfStops.get(stop.getIndex()-1).y <= Environment.getY()){
			line(screenPositionOfStops.get(stop.getIndex()-1).x,screenPositionOfStops.get(stop.getIndex()-1).y,screenPositionOfStops.get(stop.getIndex()-1).x+25,screenPositionOfStops.get(stop.getIndex()-1).y-25);
			drawLineChooserPanelAt(p, stop, screenPositionOfStops.get(stop.getIndex()-1).x+25,screenPositionOfStops.get(stop.getIndex()-1).y-25-(float)(10+30*Math.ceil(stop.getListOfPassingLines().size()/3.0)));
		//Lower Right Part of Screen
		} else if (Environment.getX()/2 <= screenPositionOfStops.get(stop.getIndex()-1).x && screenPositionOfStops.get(stop.getIndex()-1).x <= Environment.getX() && Environment.getY()/2 <= screenPositionOfStops.get(stop.getIndex()-1).y && screenPositionOfStops.get(stop.getIndex()-1).y <= Environment.getY()){
			line(screenPositionOfStops.get(stop.getIndex()-1).x,screenPositionOfStops.get(stop.getIndex()-1).y,screenPositionOfStops.get(stop.getIndex()-1).x-25,screenPositionOfStops.get(stop.getIndex()-1).y-25);
			drawLineChooserPanelAt(p, stop, screenPositionOfStops.get(stop.getIndex()-1).x-215,screenPositionOfStops.get(stop.getIndex()-1).y-25-(float)(10+30*Math.ceil(stop.getListOfPassingLines().size()/3.0)));
		//Top Left Part of Screen
		} else if (0 <= screenPositionOfStops.get(stop.getIndex()-1).x && screenPositionOfStops.get(stop.getIndex()-1).x <= Environment.getX()/2 && 0 <= screenPositionOfStops.get(stop.getIndex()-1).y && screenPositionOfStops.get(stop.getIndex()-1).y <= Environment.getY()/2){
			line(screenPositionOfStops.get(stop.getIndex()-1).x,screenPositionOfStops.get(stop.getIndex()-1).y,screenPositionOfStops.get(stop.getIndex()-1).x+25,screenPositionOfStops.get(stop.getIndex()-1).y+25);
			drawLineChooserPanelAt(p, stop, screenPositionOfStops.get(stop.getIndex()-1).x+25,screenPositionOfStops.get(stop.getIndex()-1).y+25);
		//Top Right Part of Screen
		} else if (Environment.getX()/2 <= screenPositionOfStops.get(stop.getIndex()-1).x && screenPositionOfStops.get(stop.getIndex()-1).x <= Environment.getX() && 0 <= screenPositionOfStops.get(stop.getIndex()-1).y && screenPositionOfStops.get(stop.getIndex()-1).y <= Environment.getY()/2){
			line(screenPositionOfStops.get(stop.getIndex()-1).x,screenPositionOfStops.get(stop.getIndex()-1).y,screenPositionOfStops.get(stop.getIndex()-1).x-25,screenPositionOfStops.get(stop.getIndex()-1).y+25);
			drawLineChooserPanelAt(p, stop, screenPositionOfStops.get(stop.getIndex()-1).x-215,screenPositionOfStops.get(stop.getIndex()-1).y+25);
		}
	}

	private void drawLineChooserPanelAt(PApplet p, Stop stop, float x, float y){
			int num_lines = stop.getListOfPassingLines().size();
			if(linePanel != null){
				linePanel.dispose();
				linePanel = null;
			}
			linePanel = new GPanel(p,x,y,(int)210,(int)(20+30*Math.ceil(num_lines/3.0)), "LineChooser " + stop.getIndex());
			linePanel.setOpaque(true);
			linePanel.setDraggable(true);
			linePanel.setCollapsed(false);
			map.setPanel(linePanel);
			int count = 0;
			for (Integer lineIndex:stop.getListOfPassingLines()){
				lineButtons.add(count, new GButton(p, (float)(10 + (count % 3) * 65),(float)(20 + 30 * Math.ceil(count/3)),(float) 60, (float) 20, "Line " + lineIndex));
				lineButtons.get(count).tagNo = lineIndex;
				if((show_step == 1 && lp.getLines().get(lineIndex).isHighlighted()) || (show_step>1 && lc.getLines().get(lineIndex).isHighlighted())){
					lineButtons.get(count).setLocalColorScheme(100);
				} else {
					lineButtons.get(count).setLocalColorScheme(0);
				}
				linePanel.addControl(lineButtons.get(count));
				count++;
			}
			closeButton = new GButton(p,178,2,10,10,"x");
			linePanel.addControl(closeButton);
	}


	// Draws the trains
	private void drawTrains(int time){
		if(time<maxTime){
			if(show_step>3){
				for(Train train:shadowTrains){
					if(time>=train.getEvents().get(0).getTime() && time<train.getEvents().get(train.getEvents().size()-1).getTime()){
						train.drive(time, map, false);
						if(train.isDelayed)
							train.display(false);
					}
				}
			}
			for(Train train:trains){
				if(show_step<4 && time>=train.getEvents().get(0).getTime() && time<train.getEvents().get(train.getEvents().size()-1).getTime()){
					train.drive(time, map, false);
					train.display(false);
				} else if(show_step>3 && time>=train.getEvents().get(1).getDispoTime() && time<train.getEvents().get(train.getEvents().size()-2).getDispoTime()){
					train.drive(time, map, true);
					train.display(true);
}
			}

		}
	}

	// Draws the edges with color coding corresponding to colors of lines. Note that colors of lines are same for every execution but determined randomly.
	private void drawLineEdges(){
		strokeWeight(4);
		int[] numberLinesPerEdge=new int[ptn.getEdges().size()];
		Iterator<Integer> lineIter = null;
		if (show_step ==1)
			lineIter = lp.getLines().keySet().iterator();
		else if(show_step > 1)
			lineIter = lc.getLines().keySet().iterator();
		double dirX=0.0;
		double dirY=0.0;
		double factor=0.0;
		double x1, x2, y1, y2;
		Line line = null;
		while(lineIter.hasNext()){
			if (show_step ==1)
				line = lp.getLines().get(lineIter.next());
			else if(show_step > 1)
				line = lc.getLines().get(lineIter.next());
			if(line.getFrequency()>0 && line.isHighlighted()){
				for(Edge edge:line.getEdges()){
					dirY = Math.pow(-1,numberLinesPerEdge[edge.getIndex()-1])* (screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).x - screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).y);
					dirX = Math.pow(-1,numberLinesPerEdge[edge.getIndex()-1])* (screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).x - screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).y);
					factor = Math.sqrt(Math.pow(dirX,2) + Math.pow(dirY,2))*2 / (5*numberLinesPerEdge[edge.getIndex()-1]+2);
					dirY = dirY/factor;
					dirX = dirX/factor;
					x1 = screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).x+dirX;
					x2 = screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).x+dirX;
					y1 = screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).y+dirY;
					y2 = screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).y + dirY;
					stroke(line.getColor().getRed(),line.getColor().getGreen(),line.getColor().getBlue());
					line((float)x1,(float)y1,(float)x2,(float)y2);
					numberLinesPerEdge[edge.getIndex()-1]+=1;
				}
			}
		}
	}

	// Draw PTN-edges.
	private void drawEdges(){
		for(Edge edge:ptn.getEdges()){
			line(screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).x,screenPositionOfStops.get(edge.getLeft_stop().getIndex()-1).y,screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).x,screenPositionOfStops.get(edge.getRight_stop().getIndex()-1).y);
		}
	}

	// Updates screen position of stops and draws all stops

	//TODO: DRAW STOPS AS BUTTONS!!!
	private void drawStops(){
		Location location = null;
		ScreenPosition sp = null;
		SimplePointMarker spm = null;
		screenPositionOfStops = new ArrayList<ScreenPosition>();

		for(Stop stop:ptn.getStops()){
			location = new Location(stop.getX_coordinate(), stop.getY_coordinate());
			spm = new SimplePointMarker(location);
			sp = spm.getScreenPosition(map);
			screenPositionOfStops.add(stop.getIndex()-1,sp);
			strokeWeight(3);
			stroke(0, 100);
			fill(0, 100, 0, 300);
			//ellipse(0, 10,STOP_SIZE,STOP_SIZE);
			//point(screenPositionOfStops.get(stop.getIndex()-1).x, screenPositionOfStops.get(stop.getIndex()-1).y);
			ellipse(screenPositionOfStops.get(stop.getIndex()-1).x, screenPositionOfStops.get(stop.getIndex()-1).y,STOP_SIZE,STOP_SIZE);
			text(stop.getLong_name(), screenPositionOfStops.get(stop.getIndex()-1).x +STOP_SIZE, screenPositionOfStops.get(stop.getIndex()-1).y + 4);
			strokeWeight(2);
		}

	}

	// Input reader function
	private static void readInput(Config config)  throws IOException, InterruptedException, Exception {
		String load_step_string = config.getStringValue("mapgui_show_step");
		if(load_step_string.equals("ptn")){
			load_step = 0;
		} else if(load_step_string.equals("linepool")){
			load_step = 1;
		} else if(load_step_string.equals("lineconcept")){
			load_step = 2;
		} else if(load_step_string.equals("timetable")){
			load_step = 3;
		} else if(load_step_string.equals("dispotimetable")){
			load_step = 4;
		}
		show_step = load_step;
		frameSpeed = config.getDoubleValue("mapgui_visual_speed");

		boolean undirected = config.getBooleanValue("ptn_is_undirected");
		capacity = config.getIntegerValue("gen_passengers_per_vehicle");
		ptn = new PTN(!undirected);
		if(!(new File(config.getStringValue("default_stops_file"))).exists()){
			System.err.print("Default stops file does not exist!");
			System.exit(1);
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(config.getStringValue("default_stops_coordinates_file")), "UTF-8"));
		String[] split;
		String row;

        HashMap<Integer, Double> latitudes = new HashMap<>();
        HashMap<Integer, Double> longitudes = new HashMap<>();
        while((row = in.readLine()) != null){
            if(!row.startsWith("#")){
                split = row.split(";");
                if(split.length>0){
                    latitudes.put(Integer.parseInt(split[0].trim()), Double.valueOf(split[1].trim()));
                    longitudes.put(Integer.parseInt(split[0].trim()), Double.valueOf(split[2].trim()));
                } else {
                    // File does not contain sufficient information
                    System.exit(1);
                }

            }
        }

        in = new BufferedReader(new InputStreamReader(new FileInputStream(config.getStringValue("default_stops_file")), "UTF-8"));
		while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>0){
                    int index = Integer.parseInt(split[0].trim());
                    double lat = 0;
                    double lon = 0;
                    try {
                        lat = latitudes.get(index);
                        lon = longitudes.get(index);
                    }
                    catch (NullPointerException e) {
                        System.err.println("Cannot find coordinates for stop " + index);
                        System.exit(1);
                    }
                    ptn.addStop(new Stop(index,split[1].trim(),split[2].trim().replaceAll("\"",""),lat,lon));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}
		if(!(new File(config.getStringValue("default_edges_file"))).exists()){
			System.err.print("Default edges file does not exist!");
			System.exit(1);
		}
		in = new BufferedReader(new FileReader(config.getStringValue("default_edges_file")));
		while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>4){
					ptn.addEdge(new Edge(!undirected, Integer.parseInt(split[0].trim()), ptn.getStop(Integer.parseInt(split[1].trim())), ptn.getStop(Integer.parseInt(split[2].trim())),Double.parseDouble(split[3].trim()), Integer.parseInt(split[4].trim()),Integer.parseInt(split[5].trim())));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}

        in.close();

        if(!(new File(config.getStringValue("default_od_file"))).exists()){
			System.err.print("Default OD file does not exist!");
			System.exit(1);
		}

		od = new OD();
        // parse OD matrix
        in = new BufferedReader(new FileReader(config.getStringValue("default_od_file")));
		while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>2){
					od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())),ptn.getStop(Integer.parseInt(split[1].trim())),Double.parseDouble(split[2].trim()));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}
		if(load_step==1 && !(new File(config.getStringValue("default_pool_file"))).exists()){
			System.err.print("Default line pool file does not exist!");
			System.exit(1);
		} else if (show_step>1 && !(new File(config.getStringValue("default_lines_file"))).exists()){
			System.err.print("Default line concept file does not exist!");
			System.exit(1);
		}

        in.close();
        lp = new LinePool(!undirected);
        lc = new LinePool(!undirected);
        in = new BufferedReader(new FileReader(config.getStringValue("default_pool_file")));
		Random gen = new Random(0);
        while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>2){
					if(lp.getLine(Integer.parseInt(split[0].trim())) == null){
						lp.addLine(new Line(!undirected, Integer.parseInt(split[0].trim())));
						lp.getLine(Integer.parseInt(split[0].trim())).setFrequency(1);
						lp.getLine(Integer.parseInt(split[0].trim())).setColor(new Color(gen.nextInt(255),gen.nextInt(255),gen.nextInt(255)));
					}
					lp.getLine(Integer.parseInt(split[0].trim())).addEdge(Integer.parseInt(split[1].trim()),ptn.getEdge(Integer.parseInt(split[2].trim())));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}
        in.close();

        in = new BufferedReader(new FileReader(config.getStringValue("default_lines_file")));
        while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>2){
					if(lc.getLine(Integer.parseInt(split[0].trim())) == null){
						lc.addLine(new Line(!undirected, Integer.parseInt(split[0].trim())));
						lc.getLine(Integer.parseInt(split[0].trim())).setFrequency(Integer.parseInt(split[3].trim()));
						lc.getLine(Integer.parseInt(split[0].trim())).setColor(lp.getLines().get(Integer.parseInt(split[0].trim())).getColor());
					}
					lc.getLine(Integer.parseInt(split[0].trim())).addEdge(Integer.parseInt(split[1].trim()),ptn.getEdge(Integer.parseInt(split[2].trim())));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}
        in.close();


        if(!(new File(config.getStringValue("default_pool_cost_file"))).exists()){
			System.err.print("Default pool cost file does not exist!");
			System.exit(1);
		}
        in = new BufferedReader(new FileReader(config.getStringValue("default_pool_cost_file")));
        while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>2){
					if(lp.getLine(Integer.parseInt(split[0].trim())) != null){
						lp.getLine(Integer.parseInt(split[0].trim())).setLength(Double.parseDouble(split[1].trim().replaceAll(",","\\.")));
						lp.getLine(Integer.parseInt(split[0].trim())).setCosts(Double.parseDouble(split[2].trim().replaceAll(",","\\.")));
					}
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}

			}
		}
        in.close();

        setUpStopLineAdjacency();

        if(load_step >2 && !(new File(config.getStringValue("default_events_periodic_file"))).exists()){
			System.err.print("Default events periodic file does not exist!");
			System.exit(1);
		}
		listOfEvents = new ArrayList<FullPeriodicEvent>();
		in = new BufferedReader(new FileReader(config.getStringValue("default_events_periodic_file")));
		while((row = in.readLine()) != null){
			if(!row.startsWith("#")){
				split = row.split(";");
				if(split.length>2){
					listOfEvents.add(Integer.parseInt(split[0].trim())-1,new FullPeriodicEvent(Integer.parseInt(split[0].trim()),split[1].trim(), ptn.getStop(Integer.parseInt(split[2].trim())),Integer.parseInt(split[3].trim()),Double.parseDouble(split[4].trim())));
				} else {
					// File does not contain sufficient information
					System.exit(1);
				}
			}
		}
		in.close();

		if(load_step >3 && (!(new File(config.getStringValue("default_events_expanded_file"))).exists() || !(new File(config.getStringValue("default_event_delays_file"))).exists() || !(new File(config.getStringValue("default_disposition_timetable_file"))).exists() || !(new File(config.getStringValue("default_activities_expanded_file"))).exists() || !(new File(config.getStringValue("default_activity_delays_file"))).exists())){
			System.err.print("At least one file from disposition timetable data does not exist!");
			System.exit(1);
		}

		nean = IO.readNonPeriodicEANetwork((show_step>3)?true:false, (show_step>3)?true:false);
	}

	private static void setUpStopLineAdjacency(){
		for(Stop stop:ptn.getStops())
			stop.resetListOfPassingLines();
        if(show_step == 1){
        Iterator<Integer> lineIter = lp.getLines().keySet().iterator();
        Line line = null;
		while(lineIter.hasNext()){
			line = lp.getLines().get(lineIter.next());
			if (line.getFrequency()>0){
				for(Edge edge:line.getEdges()){
					edge.getLeft_stop().addLine(line.getIndex());
					edge.getRight_stop().addLine(line.getIndex());
				}
			}
		}
		} else if(show_step>1){
		Iterator<Integer> lineIter = lc.getLines().keySet().iterator();
        Line line = null;
		while(lineIter.hasNext()){
			line = lc.getLines().get(lineIter.next());
			if (line.getFrequency()>0){
				for(Edge edge:line.getEdges()){
					edge.getLeft_stop().addLine(line.getIndex());
					edge.getRight_stop().addLine(line.getIndex());
				}
			}
		}
	}
}

	public void keyPressed() {
		if (key == ' ') {
			log.debug("programmed: fire panTo + zoomTo");
			PanMapEvent panMapEvent = new PanMapEvent(this, map.getId());
			panMapEvent.setToLocation(startLocation);
			eventDispatcher.fireMapEvent(panMapEvent);
			ZoomMapEvent zoomMapEvent = new ZoomMapEvent(this, map.getId(),"zoomTo");
			zoomMapEvent.setZoom(startZoom);
			eventDispatcher.fireMapEvent(zoomMapEvent);
		}
		if (key == '0' || key == '1' || key == '2' || key == '3' || key == '4' || key == '5' || key == '6' || key == '7' || key == '8' || key == '9'){
			ZoomMapEvent zoomMapEvent = new ZoomMapEvent(this, map.getId(),"zoomTo");
			zoomMapEvent.setZoom(Integer.valueOf(""+key)+3);
			eventDispatcher.fireMapEvent(zoomMapEvent);
		}
	}

	public void mouseClicked(){
		if(linePanel == null || mouseX < linePanel.getX() || mouseX > linePanel.getX()+linePanel.getWidth() || mouseY < linePanel.getY() || mouseY > linePanel.getY() + linePanel.getHeight()){
			mouseClickedX = mouseX;
			mouseClickedY = mouseY;
		}
		defaultCursor = new Cursor(Cursor.HAND_CURSOR);
	}

	public void mouseMoved(){
		boolean handdrawn = false;
		for(Stop stop:ptn.getStops()){
			if(screenPositionOfStops.get(stop.getIndex()-1).x-STOP_SIZE/2 <= mouseX && mouseX <= screenPositionOfStops.get(stop.getIndex()-1).x + STOP_SIZE/2 && screenPositionOfStops.get(stop.getIndex()-1).y-STOP_SIZE/2 <= mouseY && mouseY <= screenPositionOfStops.get(stop.getIndex()-1).y + STOP_SIZE/2){
				defaultCursor = new Cursor(Cursor.HAND_CURSOR);
				handdrawn = true;
				break;
			}
		}
		if (!handdrawn){
			defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
		}
	}


// Subclass modeling the trains.
// Note the methods for drawing a train are methods of the train itself!

class Train{

	public int capacity;
	public int index;
	public Line line;
	public ArrayList<FullNonPeriodicEvent> listOfEvents;
	public ArrayList<NonPeriodicActivity> listOfActivities;
	int lastEventPos=0;
	float xpos;
	float ypos;
	float xdir;
	float ydir;
	Location location;
	Location locationNext;
	SimplePointMarker spm;
	SimplePointMarker spmNext;
	ScreenPosition sp;
	ScreenPosition spNext;
	double normalisingFactor;
	boolean isDelayed;
	ShadowTrain shadowTrain;
	double passengers;


	public Train(int index){
		this.index = index;
		this.line = null;
		this.listOfEvents = new ArrayList<FullNonPeriodicEvent>();
		this.listOfActivities = new ArrayList<NonPeriodicActivity>();
		xpos = 0f;
		ypos = 0f;
	}

	public Train(int index, Line line){
		this.index = index;
		this.line = line;
		this.listOfEvents = new ArrayList<FullNonPeriodicEvent>();
	}

	public void initializePosition(){
		lastEventPos=0;
		xpos = (float)this.listOfEvents.get(0).getFullPeriodicEvent().getFullStation().getX_coordinate();
		ypos = (float)this.listOfEvents.get(0).getFullPeriodicEvent().getFullStation().getY_coordinate();
	}

	public void setShadowTrain(ShadowTrain shadowTrain){
		this.shadowTrain = shadowTrain;
	}

	public void setLine(Line line){
		this.line = line;
	}

	public Line getLine(){
		return this.line;
	}

	public int getIndex(){
		return this.index;
	}

	public ScreenPosition getScreenPosition(){
		return this.sp;
	}

	public boolean isDelayed(){
		return this.isDelayed;
	}

	public void setCapacity(int capacity){
		this.capacity = capacity;
	}

	public int getCapacity(){
		return this.capacity;
	}

	public void addActivity(NonPeriodicActivity act){
		int index = 0;
		if(this.listOfActivities.size()>0){
		for(NonPeriodicActivity actCompare:this.listOfActivities){
			if(act.getSource().getTime()>=actCompare.getTarget().getTime()){
				index=this.listOfActivities.indexOf(actCompare);
				break;
			}
		}
		this.listOfActivities.add(index,act);
	} else
		this.listOfActivities.add(0,act);
	}

	public void addEvent(FullNonPeriodicEvent event){
		int index = listOfEvents.size();
		for(FullNonPeriodicEvent eventIt:listOfEvents){
			if(event.getTime()<eventIt.getTime()){
				index = listOfEvents.indexOf(eventIt);
				break;
			}
		}
		this.listOfEvents.add(index,event);
		if(this.listOfEvents.size()==1){
			this.setLine(lp.getLines().get(event.getFullPeriodicEvent().getLine()));
		}
	}

	public ArrayList<FullNonPeriodicEvent> getEvents(){
		return this.listOfEvents;
	}

	public ArrayList<NonPeriodicActivity> getActivities(){
		return this.listOfActivities;
	}

	void display(boolean showDelayedTime){
		stroke(0);
		strokeWeight(1);
		rectMode(CENTER);
		drawTrain(sp.x,sp.y,spNext.x,spNext.y);
		if(showDelayedTime){
			line(sp.x,sp.y,this.shadowTrain.getScreenPosition().x,this.shadowTrain.getScreenPosition().y);
		}
	}

	void drawTrain(float x,float y, float x2, float y2){
		strokeWeight(1);
		xdir = (float)((x2-x)/(double)(Math.sqrt(Math.pow(x2-x,2)+Math.pow(y2-y,2))));
		ydir = (float)((y2-y)/(double)(Math.sqrt(Math.pow(x2-x,2)+Math.pow(y2-y,2))));
		if(this instanceof ShadowTrain) {
			fill(color(0,62,56));
		} else {
			fill(color(this.getLine().getColor().getRed(),this.getLine().getColor().getGreen(),this.getLine().getColor().getBlue()));
			quad((float)(x+TRAIN_SIZE*(0.5*ydir+xdir*((float)this.passengers/(float)this.capacity))),(float)(y+TRAIN_SIZE*(-0.5*xdir+ydir*((float)this.passengers/(float)this.capacity))),(float)(x+TRAIN_SIZE*(-0.5*ydir+xdir*((float)this.passengers/(float)this.capacity))),(float)(y+TRAIN_SIZE*(0.5*xdir+ydir*((float)this.passengers/(float)this.capacity))),(float)(x+TRAIN_SIZE*(-0.5*ydir-xdir)),(float)(y+TRAIN_SIZE*(0.5*xdir-ydir)),(float)(x+TRAIN_SIZE*(0.5*ydir - xdir)),(float)(y+TRAIN_SIZE*(-0.5*xdir-ydir)));
		}
		triangle((float)(x+TRAIN_SIZE*(0.5*ydir+xdir)),(float)(y+TRAIN_SIZE*(-0.5*xdir+ydir)),(float)(x+TRAIN_SIZE*(-0.5*ydir+xdir)),(float)(y+TRAIN_SIZE*(0.5*xdir+ydir)),(float)(x+1.5*TRAIN_SIZE*xdir),(float)(y+1.5*TRAIN_SIZE*ydir));
		if(!(this instanceof ShadowTrain))
			noFill();
		quad((float)(x+TRAIN_SIZE*(0.5*ydir+xdir)),(float)(y+TRAIN_SIZE*(-0.5*xdir+ydir)),(float)(x+TRAIN_SIZE*(-0.5*ydir+xdir)),(float)(y+TRAIN_SIZE*(0.5*xdir+ydir)),(float)(x+TRAIN_SIZE*(-0.5*ydir-xdir)),(float)(y+TRAIN_SIZE*(0.5*xdir-ydir)),(float)(x+TRAIN_SIZE*(0.5*ydir - xdir)),(float)(y+TRAIN_SIZE*(-0.5*xdir-ydir)));

	}

	void drive(int time, UnfoldingMap map, boolean showDelayedTime) {
		int count;
		for(count=0; count<=this.listOfEvents.size()-2; count++){
			if(!showDelayedTime && listOfEvents.get(count).getTime()<=time && listOfEvents.get(count+1).getTime()>time){
				lastEventPos = count;
				break;
			} else if(showDelayedTime && listOfEvents.get(count).getDispoTime()<=time && listOfEvents.get(count+1).getDispoTime()>time){
				lastEventPos = count;
				break;
			}
		}
		this.passengers = listOfActivities.get(count).getWeight();
		if(!listOfEvents.get(lastEventPos).getFullPeriodicEvent().getType().equals("\"arrival\"")){
			if(listOfEvents.get(lastEventPos).getDispoTime()+listOfEvents.get(lastEventPos+1).getDispoTime()-listOfEvents.get(lastEventPos).getTime()-listOfEvents.get(lastEventPos+1).getTime()>0)
				isDelayed = true;
			else
				isDelayed = false;
			if(!showDelayedTime){
				xpos =(float)((double)listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate() + (listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getX_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate())*((double)(time-listOfEvents.get(lastEventPos).getTime()))/((double)(listOfEvents.get(lastEventPos+1).getTime()-listOfEvents.get(lastEventPos).getTime())));
				ypos = (float)((double)listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate() + (listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getY_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate())*((double)(time-listOfEvents.get(lastEventPos).getTime()))/((double)(listOfEvents.get(lastEventPos+1).getTime()-listOfEvents.get(lastEventPos).getTime())));
			} else {
				xpos =(float)((double)listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate() + (listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getX_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate())*((double)(time-listOfEvents.get(lastEventPos).getDispoTime()))/((double)(listOfEvents.get(lastEventPos+1).getDispoTime()-listOfEvents.get(lastEventPos).getDispoTime())));
				ypos = (float)((double)listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate() + (listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getY_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate())*((double)(time-listOfEvents.get(lastEventPos).getDispoTime()))/((double)(listOfEvents.get(lastEventPos+1).getDispoTime()-listOfEvents.get(lastEventPos).getDispoTime())));
			}
			xdir = (float)(listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getX_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate());
			ydir = (float)(listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getY_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate());
			normalisingFactor = (Math.sqrt(((double)(Math.pow(xdir,2) + Math.pow(ydir,2)))));
			xdir = (float)((double)xdir/normalisingFactor);
			ydir = (float)((double)ydir/normalisingFactor);
			locationNext = new Location(xpos + xdir, ypos+ydir);
			spmNext = new SimplePointMarker(locationNext);
			spNext = spmNext.getScreenPosition(map);
			location = new Location(xpos, ypos);
			spm = new SimplePointMarker(location);
			sp = spm.getScreenPosition(map);
		}  else {
			if(listOfEvents.get(lastEventPos).getDispoTime()-listOfEvents.get(lastEventPos).getTime()>0)
				isDelayed = true;
			else
				isDelayed = false;
			xpos = (float)(listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate());
			ypos = (float)(listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate());
			location = new Location(xpos, ypos);
			spm = new SimplePointMarker(location);
			sp = spm.getScreenPosition(map);
			if(spNext == null){
				xdir = (float)(listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getX_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getX_coordinate());
				ydir = (float)(listOfEvents.get(lastEventPos+1).getFullPeriodicEvent().getFullStation().getY_coordinate()-listOfEvents.get(lastEventPos).getFullPeriodicEvent().getFullStation().getY_coordinate());
				normalisingFactor = (Math.sqrt(((double)(Math.pow(xdir,2) + Math.pow(ydir,2)))));
				xdir = (float)((double)xdir/normalisingFactor);
				ydir = (float)((double)ydir/normalisingFactor);
				locationNext = new Location(xpos + xdir, ypos+ydir);
				spmNext = new SimplePointMarker(locationNext);
				spNext = spmNext.getScreenPosition(map);
			}
		}
	}
	}

	// Subclass of Train to show the planned train, when the actual train is delayed.

	class ShadowTrain extends Train{
		 public int trainID;
		 public int index;

		 public ShadowTrain(int index, int trainID){
			 super(trainID);
			 this.trainID = trainID;
			 this.index = index;
		 }

		@Override
		public int getIndex(){
			return this.index;
		}

		public int getActualIndex(){
			return this.trainID;
		}

	}
}

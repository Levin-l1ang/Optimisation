/** 
 *	Diese Klasse enthält das Hauptprogramm sowie die Methode, die die
 * 	Verbreitung einer Verspätung im Netzwerk durchführt.
 */

import java.io.*;
import java.util.*;


public class ODM
{
	// Config-Werte

	private static boolean delaysKnownBefore;
	private static int delaysKnownBiggerThan;
	private static int delaysKnownSeed;
	private static String defaultDispoFile;
	private static String dispoHeader;

	public static String rwtAlgo;
	public static String passengerAlgo;
	public static String headwayAlgo;
	public static int fixedRWT;
	public static int slackRelevantEdges;
	public static double criticalRate;

	// Daten-Struktur 

	private static NonPeriodicEANetwork ean;
	private static Vector <NonPeriodicEvent> delayedEvents;
	private static Vector <NonPeriodicActivity> delayedActivities;

	private static void readConfig() throws Exception {

		Config config = new Config(new File("basis/Config.cnf"));
		
		defaultDispoFile = config.getStringValue("default_disposition_timetable_file");
		dispoHeader = config.getStringValue("timetable_header_disposition");

		delaysKnownBefore = config.getBooleanValue("delays_known_before");
		if(delaysKnownBefore) {
			delaysKnownSeed = config.getIntegerValue("delays_known_seed");
			delaysKnownBiggerThan = config.getIntegerValue("delays_known_bigger_than");
		}		

		rwtAlgo = config.getStringValue("algo_RWT");
		passengerAlgo = config.getStringValue("algo_Passenger");
		headwayAlgo = config.getStringValue("algo_headway");

		if (rwtAlgo.equals("RWT_fixed"))
			fixedRWT = config.getIntegerValue("fixed_time");

		else if (rwtAlgo.equals("RWT_slack"))
			slackRelevantEdges = config.getIntegerValue("slack_relevant_edges");

		if (!passengerAlgo.equals("none"))
			criticalRate= config.getDoubleValue("critical_rate");

	}

	/**
	 *	Liest das EAN mit Quellverspätungen und Fahrplan ein
	 * 	Setzt die Headway-Aktivitäten gemäß dem Fahrplan
	 * 	Ordnet Verspätungen chronologisch nach Zielzeit
	 */

	
	private static void readData() throws Exception {

		// Lese EAN mit Fahrplan und Quellverspätungen ein und setze InitalDispo
		ean = IO.readNonPeriodicEANetwork(true, false);
		for(NonPeriodicEvent e : ean.getEvents())
			e.setDispoTime(e.getTime());

		// Setze Variable gemäß Fahrplan
		ean.setZ();
		ean.setG();
				
		// Lese Quellverspätungen auf Aktivitäten ein

		delayedActivities = new Vector<NonPeriodicActivity>();
		Iterator<NonPeriodicActivity> it = ean.getDrivingActivities().iterator();

		while (it.hasNext()) {
			NonPeriodicActivity a = it.next();
			assert(a.getSourceDelay() >= 0) : "Fehler, negative Quellverspätung";
			if(a.getSourceDelay()!=0) {
				delayedActivities.add(a);
			}				
		}			
	} 

	/**
	 *	Ordnet Verspätungen nach Bearbeitungsreihenfolge. Falls delaysKnownBefore
	 *	gesetzt ist, werden zufällig Aktvititäten mit einer Quellverspätung
	 * 	größer als delaysKnownBiggerThan als im Vorhinein bekannt gesetzt.
	 */

	private static void orderDelays(Vector<NonPeriodicActivity> delays) {
		if(delaysKnownBefore) {
			Random r = new Random(delaysKnownSeed);
			for (NonPeriodicActivity a : delays) {			
				if(a.getSourceDelay() > delaysKnownBiggerThan) 
					if(r.nextBoolean()) a.setDelayKnownTime(0);
			}			
		}

		Collections.sort(delays, new NonPeriodicActivityTimeComparator());
	}


	/**
	 * 	Hauptfunktion zur Verbreitung der Verspätung. Arbeitet rekursiv
	 * 	ähnlich einer Tiefensuche.
	 */

	private static void spreaddelay(NonPeriodicEvent e) throws Exception {

		// Sortiere Liste ausgehender Kanten nach Fahr/Warte und Headway/Change
	
		ArrayList<NonPeriodicActivity> list = new ArrayList<NonPeriodicActivity>();
		list.addAll(e.getOutgoingActivities());
		Collections.sort(list);	

		NonPeriodicActivity first = null;	
		
		for (NonPeriodicActivity a : list) 
			if(a.getType().equals("drive") || a.getType().equals("wait")) 
				first = a;			

		if(first != null) {
			list.remove(first);
			list.add(0,first);
		}


		while (!list.isEmpty()) {
			NonPeriodicActivity a = list.get(0);

			// Falls keine Verspätung übertragen wird, nächste Kante
	
			if(e.getDispoTime() + a.getLowerBound() <= a.getTarget().getDispoTime()) {
				list.remove(a);
				continue;
			}

			// Bearbeitung der Fahr- oder Wartekanten
			if (a.getType().equals("drive") || a.getType().equals("wait")) {
				a.getTarget().setDispoTime(e.getDispoTime() + a.getLowerBound());				
				spreaddelay(a.getTarget());	
				list.remove(a);		
			}	

			// Bearbeite nun Headway oder Change-Kanten
			
			else if(a.getType().equals("headway")) {
		
				NonPeriodicHeadwayActivity headway = (NonPeriodicHeadwayActivity) a;
				
				if(headway.getG() == 1) {
					list.remove(headway);
					continue;	
				}

				// Treffe Entscheidung über Headway
				
				boolean keepHeadway = Heuristics.decideHeadway(ean, headway);

				if(keepHeadway) {
					System.out.println("Headway beibehalten!");
					headway.getTarget().setDispoTime(e.getDispoTime() +	headway.getLowerBound());
					spreaddelay(headway.getTarget());
				}

				// Änderung der Reihenfolge, evtl neue Dispositionszeit für e

				else {
					System.out.println("Headway-Wechsel!");
					headway.setG(1);
					headway.getCorrespodingHeadway().setG(0);
			
					int compare = headway.getTarget().getDispoTime() + 
						headway.getCorrespodingHeadway().getLowerBound();
					if (compare > e.getDispoTime()){
						e.setDispoTime(compare);
						spreaddelay(e);
					}
				}
				list.remove(headway);								
					
			}		

			// Bearbeitung der Umstiege
			
			else if(a.getType().equals("change")) {
	
				NonPeriodicChangingActivity change = (NonPeriodicChangingActivity) a;

				// Treffe Entscheidung über Anschluss
	
				boolean keepConnection = Heuristics.decideChange(ean, change);
	
				// Bei gehaltenem Anschluss, vrebreite Verspätung
	
				if (keepConnection)  {
					System.out.println("Anschluss halten!");
					change.setZ(0);
					change.getTarget().setDispoTime(e.getDispoTime() + 
					change.getLowerBound());
					spreaddelay(change.getTarget());
				}
	
				else {
					System.out.println("Anschluss nicht halten!");
					change.setZ(1);	
				}

				list.remove(change);
			}
		}
		
	}	

	/**
	 *	Main-Klasse führt die Verbreitung der Quellverspätungen in der vorgege-
	 * 	benen Reihenfolge durch. Schreibt den entstandenen Dispositionsfahrplan
	 *	in die dafür vorgesehene Datei.
	 */

	public static void main(String[] args) throws Exception
	{	
		
		readConfig();
		System.out.println("Konfiguration eingelesen!");
		readData();
		System.out.println("EAN mit Fahrplan und Quellverspätungen eingelesen!");


		System.out.println("************************************************");
		System.out.println("Erstelle Dispositionsfahrplan mit folgenden Einstellungen");
		System.out.println("Rgelwartezeit: " + rwtAlgo);
		if (rwtAlgo.equals("RWT_fixed"))
			System.out.println("feste Regelwartezeit in Sekunden: " + fixedRWT);

		System.out.println("Passagierabhängige Strategie: " + passengerAlgo);
		System.out.println("Strategie Headways: " + headwayAlgo);

		if(delaysKnownBefore)
			System.out.println("Verspätungen sind zum Teil vorher bekannt");

		

		// Berechne Slack auf Fahr/Warte-, Change- und Headway-Kanten
		
		int slack = 0;
		for (NonPeriodicHeadwayActivity h : ean.getHeadwayActivities())
			if(h.getG() == 0) slack += ean.getSlack(h);
		System.out.println("Gesamter Slack auf Headway-Kanten: " + slack);

		slack = 0;
		for (NonPeriodicChangingActivity a : ean.getChangingActivities()) 
			slack += ean.getSlack(a);	
		System.out.println("Gesamter Slack auf Change-Kanten: " + slack);
	
		slack = 0;
		for (NonPeriodicActivity a : ean.getDrivingActivities()) 
			slack += ean.getSlack(a);	
		for (NonPeriodicActivity a : ean.getWaitingActivities()) 
			slack += ean.getSlack(a);
		System.out.println("Gesamter Slack auf Fahr-und Warte-Kanten: " + slack);

		System.out.println("************************************************");
		

		//*********************ALGORITHMUS**************************************

		// Sortiere Verspätungen gemäß Zeitpunkt ihres Bekanntwerdens

		orderDelays(delayedActivities);

		for (NonPeriodicActivity a : delayedActivities) {

			NonPeriodicEvent target = a.getTarget();
			NonPeriodicEvent source = a.getSource();		

			if (target.getTime() < source.getTime() + a.getLowerBound() + a.getSourceDelay()) {
				target.setDispoTime(source.getTime()+ a.getLowerBound() + a.getSourceDelay());
				spreaddelay(target);	
			}
			

			// Überschreibe Fahrplan mit neuem Dispositionsfahrplan
			for (NonPeriodicEvent event : ean.getEvents()) {	
				assert (event.getTime()<=event.getDispoTime()) : "Fehler in Fahrplan";
				event.setTime(event.getDispoTime());
			}
		}

		ean.setZ();
		ean.setG();
		
		IO.outputDispoTimetable(ean, defaultDispoFile, dispoHeader);
	}
}

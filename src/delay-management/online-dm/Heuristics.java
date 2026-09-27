/**
 *	Diese Klasse enthält die verschiedene Heuristiken zur Anschluss- und 
 *	Headway Entscheidung. Sie besteht aus zwei statischen Methoden, die jeweils
 * 	einen @code{boolean}-Wert zurückgeben, der dann verarbeitet werden kann.
 *	Dabei bezieht sie die Informationen, welche Heuristik angewandt wird aus
 *	einer Setting Klasse.
 */

import java.io.*;
import java.util.*;

public class Heuristics {


	/**
	 *	Realisiert die einzelnen Heuristiken für Anschlussentscheidungen
	 * 	@return @code{true}, wenn Anschluss gehalten werden soll
	 *			@code{false}, sonst.
	 */

	public static boolean decideChange(NonPeriodicEANetwork ean, NonPeriodicChangingActivity change) throws Exception {
		
		NonPeriodicEvent source = change.getSource();
		NonPeriodicEvent target = change.getTarget();
		
		int delay = source.getDispoTime() + change.getLowerBound() - target.getDispoTime();

		assert(delay >= 0) : "Negative Verspätung!";

		assert(ean.getNextDrivingActivity(change) != null) : "Keine anschließende Fahrkante, bitte roll_out_whole_trips auf true!";

		// Algorithmen mit Regelwartezeit

		/*	Die Regelwartezeit wird je nach gewählter Strategie berechnet und
			am Ende mit der tatsächlichen Verspätung verglichen
		*/
			

		boolean RWTmaintain = true;

		int RWT = 0;

		// RWT 1, setzt RWT auf festen Wert
		
		if(ODM.rwtAlgo.equals("RWT_fixed")) {
			RWT  = ODM.fixedRWT;
		}


		// RWT 2
		// Slack der folgenden RelevantEdges Kanten, wobei Warte- und anschließende
		// Fahrkante zusammengefasst werde, d.h. relevantEdges = 3 bedeutet
		// Folgenden Fahrkante + Warte + Fahr + Warte + Fahr 

		else if(ODM.rwtAlgo.equals("RWT_slack")) {
		
			NonPeriodicActivity nextdrive = ean.getNextDrivingActivity(change);
			NonPeriodicActivity nextwait =  ean.getNextWaitingActivity(nextdrive);

			RWT = ean.getSlack(nextdrive);

			for (int i=1; i < ODM.slackRelevantEdges; i++) {
				RWT += ean.getSlack(nextwait);
				nextdrive = ean.getNextDrivingActivity(nextwait);
				RWT += ean.getSlack(nextdrive);
				nextwait =  ean.getNextWaitingActivity(nextdrive);
			}				
		}


		// RWT 3

		else if(ODM.rwtAlgo.equals("RWT_slack_minimal_change")) {			
			
			NonPeriodicActivity nextdrive = ean.getNextDrivingActivity(change);
			NonPeriodicActivity nextwait = null;

			int trainslack = ean.getSlack(nextdrive);
			RWT = Integer.MAX_VALUE;
				
			do {

				// Bestimme Umstieg mit minimalem Slack

				NonPeriodicActivity minchange = null;
				int minslack = Integer.MAX_VALUE;

				for(NonPeriodicActivity a : nextdrive.getTarget().getOutgoingActivities()) {
					if(a.getType().equals("change") && ean.getSlack(a) < minslack) {
						minchange = a;
						minslack = ean.getSlack(a);				
					}
				}

				if(minchange != null) {	
					RWT = Math.min(RWT, trainslack + ean.getSlack(minchange));
				}
					
				
				nextwait = ean.getNextWaitingActivity(nextdrive);
				nextdrive = ean.getNextDrivingActivity(nextwait);
				trainslack += ean.getSlack(nextwait) + ean.getSlack(nextdrive);
				
			}	while(nextwait !=  null);

		}

		else if (ODM.rwtAlgo.equals("none")) {}

		else throw new Exception("Kein zulässiger RWT-Algorithmus");

		if (delay>RWT && !ODM.rwtAlgo.equals("none"))
			RWTmaintain = false;


		// Passagierbasierte Algorithmen
		
		/*	Es wird jeweils immer ein Wert für weight berechnet, der dann
			am Ende mit dem Gewicht des Anschlusses verglichen wird
		*/

		boolean PSmaintain = true;

		double weight = 0;

		// PS 1

		if(ODM.passengerAlgo.equals("passenger_nextdrive")) {
			weight = ean.getNextDrivingActivity(change).getWeight();
		}

		// PS 2
	
		else if(ODM.passengerAlgo.equals("passenger_nextdrive_incoming")) {
			NonPeriodicActivity nextdrive = ean.getNextDrivingActivity(change);
			NonPeriodicActivity nextwait = ean.getNextWaitingActivity(nextdrive);

			weight = nextdrive.getWeight();
			
			while (nextwait != null) {
				for (NonPeriodicActivity a : nextwait.getTarget().getIncomingActivities()) {
					if(a.getType().equals("change"))
						weight += a.getWeight();
				}

				nextdrive = ean.getNextDrivingActivity(nextwait);
				nextwait = ean.getNextWaitingActivity(nextdrive);

			}
		}

		// PS 3

		else if(ODM.passengerAlgo.equals("passenger_nextdrive_outgoing")) {
			NonPeriodicActivity nextdrive = ean.getNextDrivingActivity(change);
			NonPeriodicActivity nextwait = null;

			while (nextdrive != null) {
				for(NonPeriodicActivity a : nextdrive.getTarget().getOutgoingActivities()) {
					if(a.getType().equals("change")) {
						weight += a.getWeight();			
					}
				}

				nextwait = ean.getNextWaitingActivity(nextdrive);
				nextdrive = ean.getNextDrivingActivity(nextwait);
			}
		}

		else if (ODM.passengerAlgo.equals("none")) {}
		
		else throw new Exception("Kein zulässiger Passagier-Algorithmus");

		if(weight >  ODM.criticalRate * change.getWeight())
			PSmaintain = false;

		return RWTmaintain && PSmaintain;
	}

	/**
	 *	Realisiert die Heuristiken für HeadwayEntscheidungen
	 * 	@return @code{true}, wenn Headway beibehalten werden soll
	 *			@code{false}, sonst.
	 */

	public static boolean decideHeadway(NonPeriodicEANetwork ean, NonPeriodicHeadwayActivity head) throws Exception {

		NonPeriodicEvent i = head.getSource();
		NonPeriodicEvent j = head.getTarget();
		NonPeriodicHeadwayActivity corresponding = head.getCorrespodingHeadway();


		int delay_i = j.getDispoTime() + corresponding.getLowerBound() - i.getDispoTime();
		int delay_j = i.getDispoTime() + head.getLowerBound() - j.getDispoTime();

		// Test, ob ein Wechsel zu einem gerichteten Kreis führt
	
		int i_oldTime = i.getDispoTime();
		i.setDispoTime(j.getDispoTime() + corresponding.getLowerBound());

		if(ean.containsDispoCircle()) {
			System.out.println("Wechsel würde Kreis erzeugen!");
			i.setDispoTime(i_oldTime);
			return true;
		}
		else {			
			i.setDispoTime(i_oldTime);
		}
		
		// Falls j stattfinden kann, ohne i weiter zu verspäten

		if(delay_i < 0) {
			return false;
		}

		// Fallunterscheidung ohne Gewichte (H1)
		
		if(ODM.headwayAlgo.equals("case_by_case")) {
			if (i.getDispoTime() > j.getDispoTime()) {
				return false;
			}
			else return true;
					
		}

		// Fallunterscheidung mit Gewichten (H2)
	
		else if(ODM.headwayAlgo.equals("case_by_case_weighted")) {

			if(ean.getNextDrivingActivity(head) == null) return true;
			if(ean.getNextDrivingActivity(corresponding) == null)
				return false;

			double w_head = ean.getNextDrivingActivity(head).getWeight();
			double w_corr = ean.getNextDrivingActivity(corresponding).getWeight();

			if (w_corr * delay_i < w_head * delay_j) {
				return false;
			}	
			else return true;				
		}

		else throw new Exception("Kein zulässiger Headway-Algorithmus!");
	}
}
	

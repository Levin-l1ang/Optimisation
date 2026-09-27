package net.lintim.algorithm;


import com.dashoptimization.*;
import net.lintim.model.*;
import net.lintim.util.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class containing all Xpress related code
 */
public class Xpress {

	public static double findTimetable(AperiodicEAN ean){
		int offset = Config.getIntegerValue("DM_earliest_time");
		XPRS.init();
		XPRB bcl = new XPRB();
		XPRBprob prob = bcl.newProb("TT1");
		prob.setMsgLevel(4);
		XPRBvar[] pi = new XPRBvar[ean.getVertices().size()];
		XPRBctr objective = prob.newCtr("nominal_objective");
		objective.setType(XPRB.N);
		//Initialize each variable and store the vertices in the map
		for(Vertex vertex : ean.getVertices()){
			pi[vertex.getId()-1] = prob.newVar("pi_" + vertex.getId());
		}
		//Now iterate the activities and create the constraints and fill the objective
		for(Edge edge : ean.getEdges()){
			AperiodicEANEdge aperiodicEANEdge = (AperiodicEANEdge) edge;
			XPRBvar sourceVariable = pi[aperiodicEANEdge.getSource().getId()-1];
			XPRBvar targetVariable = pi[aperiodicEANEdge.getTarget().getId()-1];
			prob.newCtr("l_" + aperiodicEANEdge.getId(),
					targetVariable.add(sourceVariable.mul(-1)).gEql(aperiodicEANEdge.getLowerBound()));
			prob.newCtr("u_" + aperiodicEANEdge.getId(),
					targetVariable.add(sourceVariable.mul(-1)).lEql(aperiodicEANEdge.getUpperBound()));
			objective.addTerm(aperiodicEANEdge.getNumberOfPassengers(), targetVariable);
			objective.addTerm(-1*aperiodicEANEdge.getNumberOfPassengers(), sourceVariable);
		}
		prob.setObj(objective);
		prob.setSense(XPRB.MINIM);
		try {
			prob.exportProb(XPRB.LP, "timetabling/TT.lp");
		} catch (IOException e) {
			System.err.println("Could not export timetabling problem");
			throw new RuntimeException(e);
		}
		prob.lpOptimise();
		int mipStatus = prob.getLPStat();
		if(mipStatus == XPRB.LP_OPTIMAL){
			for(Vertex vertex : ean.getVertices()){
				((AperiodicEANVertex) vertex).setTime((int)pi[vertex.getId()-1].getSol() + offset);
			}
		}
		return prob.getObjVal();
	}

	public static void main(String[] args){
		try{
			Config.readConfig(new File(args[0]));
		} catch (IOException e) {
			throw new RuntimeException("Could not read config file");
		}
		System.out.print("Reading in files... ");
		AperiodicEAN ean = new AperiodicEAN("AperiodicEAN");
		System.out.print("Done!\nCalculating timetable...");
		double result = findTimetable(ean);
		System.out.println("Done!");
		System.out.println("Found aperiodic timetable with objective value " + result);
		try{
			ean.output();
		}
		catch (IOException e){
			throw new RuntimeException("Could not write new timetable");
		}
	}
}

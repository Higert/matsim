/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.ikaddoura.parkAndRide.pRstrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.scenario.ScenarioImpl;

import playground.ikaddoura.parkAndRide.pR.ParkAndRideConstants;
import playground.ikaddoura.parkAndRide.pR.ParkAndRideFacility;

/**
 * @author Ihab
 *
 */
public class ParkAndRideChangeLocationStrategy implements PlanStrategyModule {
	private static final Logger log = Logger.getLogger(ParkAndRideChangeLocationStrategy.class);

	private ScenarioImpl sc;
	private Network net;
	private Map<Id, ParkAndRideFacility> id2prFacility = new HashMap<Id, ParkAndRideFacility>();
	private Map<Id, List<PrWeight>> personId2prWeights = new HashMap<Id, List<PrWeight>>();
	private int nrOfPrFacilitiesForReplanning = 0; // 0 means all P+R-Facilities are used for replanning
	private int gravity;
	
	public ParkAndRideChangeLocationStrategy(Controler controler, Map<Id, ParkAndRideFacility> id2prFacility, Map<Id, List<PrWeight>> personId2prWeights, int gravity) {
		this.sc = controler.getScenario();
		this.net = this.sc.getNetwork();
		this.id2prFacility = id2prFacility;
		this.personId2prWeights = personId2prWeights;
		this.gravity = gravity;
	}

	@Override
	public void finishReplanning() {
	}

	@Override
	public void handlePlan(Plan plan) {
		
			List<PlanElement> planElements = plan.getPlanElements();
			
			PlanIndicesAnalyzer planIndices = new PlanIndicesAnalyzer(plan);
			planIndices.setIndices();
			
			boolean hasHomeActivity = planIndices.hasHomeActivity();
			boolean hasWorkActivity = planIndices.hasWorkActivity();
			
			if (hasHomeActivity == true && hasWorkActivity == true) {
				log.info("Plan contains Home and Work Activity. Proceeding...");

				final int rndWork = (int)(MatsimRandom.getRandom().nextDouble() * planIndices.getWorkActs().size());
				log.info("PlanElements of possible Work Activities: " + planIndices.getWorkActs() + ". Creating Park'n'Ride before and after Work Activity " + planIndices.getWorkActs().get(rndWork) + "...");
				
				int workIndex;
				int maxHomeBeforeWork;
				int minHomeAfterWork;

				// Check if home-work-home sequence contains park-and-ride
				
				planIndices.setIndices();
				
				workIndex = planIndices.getWorkActs().get(rndWork);					
				minHomeAfterWork = planIndices.getMinHomeAfterWork(workIndex);
				maxHomeBeforeWork = planIndices.getMaxHomeBeforeWork(workIndex);

				List<Integer> prIndicesToChange = new ArrayList<Integer>();
				for (Integer prIndex : planIndices.getPrActs()){
					if (prIndex > maxHomeBeforeWork && prIndex < minHomeAfterWork){
						prIndicesToChange.add(prIndex);
					}
				}
				
				if (prIndicesToChange.size() == 2) {
					
					log.info("Home-Work-Home sequence contains two ParkAndRide Activities. Changing the Park'n'Ride Location...");

					// create a new ParkAndRideActivity
					Activity parkAndRide = createParkAndRideActivity(planIndices, planIndices.getWorkActs().get(rndWork), plan);
					
					int change0 = prIndicesToChange.get(0);
					int change1 = prIndicesToChange.get(1);
					
					planElements.set(change0, parkAndRide);
					planElements.set(change1, parkAndRide);
				
				} else if (prIndicesToChange.size() > 2) {
					log.warn("More than two ParkAndRide Activities in this Home-Work-Home Sequence. This should not be possible. Not modifiying the plan...");
				} else if (prIndicesToChange.isEmpty()){
					log.info("Home-Work-Home sequence doesn't contain Park'n'Ride. Not modifying the plan...");
				} else if (prIndicesToChange.size() < 2) {
					log.info("Home-Work-Home sequence contains only one ParkAndRide Activity. Not modifying the plan...");
				}
						
			} else {
				if (hasWorkActivity == false){
					log.warn("Plan doesn't contain Work Activity. This should not be possible. Not adding Park'n'Ride...");
				} else if (hasHomeActivity == false){
					log.info("Plan doesn't contain Home Activity. Not adding Park'n'Ride...");
				}
			}	
	}
	
	private Activity createParkAndRideActivity(PlanIndicesAnalyzer planIndices, Integer workIndex, Plan plan) {
		List<PrWeight> prWeights;
		EllipseSearch ellipseSearch = new EllipseSearch();
		
		Activity firstHomeAct = (Activity) plan.getPlanElements().get(planIndices.getHomeActs().get(0));
		Activity workAct = (Activity) plan.getPlanElements().get(workIndex);
		log.info("Create Park'n'Ride Activity for planElements (home: " + planIndices.getHomeActs().get(0) + " / work: " + workIndex +"): " + firstHomeAct.getCoord() + " / " + workAct.getCoord());
		if (planIndices.getWorkActs().size() > 1 || this.personId2prWeights.get(plan.getPerson().getId()) == null){
			log.info("Weights for ParkAndRide Facilities for person " + plan.getPerson().getId().toString() + " not calculated before / More than one work Activity. Calculating Weights...");
			prWeights = ellipseSearch.getPrWeights(this.nrOfPrFacilitiesForReplanning, this.net, this.id2prFacility, firstHomeAct.getCoord(), workAct.getCoord(), this.gravity);
			this.personId2prWeights.put(plan.getPerson().getId(), prWeights);
		} else {
			log.info("Weights for ParkAndRide Facilities for person " + plan.getPerson().getId().toString() + " already calculated before. Only one work Activity.");
			prWeights = this.personId2prWeights.get(plan.getPerson().getId());
		}
		
		log.info("Choose ParkAndRide Facility depending on weight...");
		Link rndPrLink = ellipseSearch.getRndPrLink(this.net, this.id2prFacility, prWeights);
		
		Activity parkAndRide = new ActivityImpl(ParkAndRideConstants.PARKANDRIDE_ACTIVITY_TYPE, rndPrLink.getToNode().getCoord(), rndPrLink.getId()); 
		parkAndRide.setMaximumDuration(ParkAndRideConstants.PARKANDRIDE_ACTIVITY_DURATION);
		
		return parkAndRide;
	}

	@Override
	public void prepareReplanning() {
	}
	
}

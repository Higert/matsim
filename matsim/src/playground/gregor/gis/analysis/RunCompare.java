/* *********************************************************************** *
 * project: org.matsim.*
 * RunCompare.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.gregor.gis.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.geotools.factory.FactoryRegistryException;
import org.geotools.feature.AttributeType;
import org.geotools.feature.AttributeTypeFactory;
import org.geotools.feature.DefaultAttributeTypeFactory;
import org.geotools.feature.Feature;
import org.geotools.feature.FeatureType;
import org.geotools.feature.FeatureTypeFactory;
import org.geotools.feature.IllegalAttributeException;
import org.geotools.feature.SchemaException;
import org.matsim.events.AgentArrivalEvent;
import org.matsim.events.AgentDepartureEvent;
import org.matsim.events.AgentStuckEvent;
import org.matsim.events.Events;
import org.matsim.events.EventsReaderTXTv1;
import org.matsim.events.handler.AgentArrivalEventHandler;
import org.matsim.events.handler.AgentDepartureEventHandler;
import org.matsim.events.handler.AgentStuckEventHandler;
import org.matsim.network.Link;
import org.matsim.network.MatsimNetworkReader;
import org.matsim.network.NetworkLayer;
import org.matsim.utils.collections.QuadTree;
import org.matsim.utils.collections.QuadTree.Rect;
import org.matsim.utils.geometry.geotools.MGC;
import org.matsim.utils.geometry.transformations.TransformationFactory;
import org.matsim.utils.gis.ShapeFileWriter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

public class RunCompare {
	
	private final static Logger log = Logger.getLogger(RunCompare.class);
		
	private final CoordinateReferenceSystem crs;
	final NetworkLayer network;
	private ArrayList<Polygon> polygons;
	private final GeometryFactory geofac;
	private final String eventsFile1;
	private final String eventsFile2;

	private TravelTimesFromEvents t1;

	private TravelTimesFromEvents t2;

	private ArrayList<Feature> features;

	private FeatureType ftRunCompare;
	final static Envelope ENVELOPE = new Envelope(648815,655804,9888424,9902468);
	final static double LENGTH = 250;
	
	
	public RunCompare(final String eventsFile1, final String eventsFile2,
			final CoordinateReferenceSystem crs, final NetworkLayer network) {
		
		this.eventsFile1 = eventsFile1;
		this.eventsFile2 = eventsFile2;
		this.crs = crs;
		this.network = network;
		this.geofac = new GeometryFactory();
		initFeatures();
		
		
	}

	
	private void initFeatures() {
		this.features = new ArrayList<Feature>();

		AttributeType geom = DefaultAttributeTypeFactory.newAttributeType("Polygon",Polygon.class, true, null, null, this.crs);
		AttributeType tt1 = AttributeTypeFactory.newAttributeType("TT1", Double.class);
		AttributeType tt2 = AttributeTypeFactory.newAttributeType("TT2", Double.class);
		AttributeType tt1DiffTt2 = AttributeTypeFactory.newAttributeType("tt1DiffTt2", Double.class);
		try {
			this.ftRunCompare = FeatureTypeFactory.newFeatureType(new AttributeType[] {geom, tt1,tt2,tt1DiffTt2}, "gridShape");
		} catch (FactoryRegistryException e) {
			e.printStackTrace();
		} catch (SchemaException e) {
			e.printStackTrace();
		}

		
	}


	public void run() {
		createPolygons();
		readEvents();
		iteratePolygons();
		writeFeatures();

	}
	
	private void writeFeatures() {
		try {
			ShapeFileWriter.writeGeometries(this.features, "./tmp/runComp.shp");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}


	private void iteratePolygons() {
		for (Polygon p : this.polygons) {
			double avgT1 = getAvgTT(p,this.t1);
			double avgT2 = getAvgTT(p,this.t2);
			
			try {
				this.features.add(this.ftRunCompare.create(new Object[]{p,avgT1,avgT2,avgT1-avgT2}));
			} catch (IllegalAttributeException e) {
				e.printStackTrace();
			}
		}
		
	}


	private double getAvgTT(final Polygon p, final TravelTimesFromEvents t) {
		
		Collection<AgentInfo> ais = t.getAgents(p);
		if (ais.size() == 0) {
			return 0;
		}
		
		double ttSum = 0;
		for (AgentInfo ai : ais) {
			ttSum += ai.time;
		}
		
		return ttSum / ais.size();
	}


	private void readEvents() {
		Events events1 = new Events();
		Events events2 = new Events();
		this.t1 = new TravelTimesFromEvents();
		this.t2 = new TravelTimesFromEvents();
		events1.addHandler(this.t1);
		events2.addHandler(this.t2);
		log.info("reading events file 1 from: " + this.eventsFile1);
		new EventsReaderTXTv1(events1).readFile(this.eventsFile1);
		this.t1.printAvgTT();
		log.info("done.");
		log.info("reading events file 2 from: " + this.eventsFile2);
		new EventsReaderTXTv1(events2).readFile(this.eventsFile2);
		this.t2.printAvgTT();
		log.info("done.");
	}


	private void createPolygons() {
		this.polygons = new ArrayList<Polygon>();
		GTH gth = new GTH(this.geofac);
	
		for (double x = ENVELOPE.getMinX(); x < ENVELOPE.getMaxX(); x += LENGTH) {
			for (double y = ENVELOPE.getMinY(); y < ENVELOPE.getMaxY(); y+= LENGTH) {
				Polygon p = gth.getSquare(new Coordinate(x,y), LENGTH);
				this.polygons.add(p);
			}

		}


	}
	
	public static void main(final String [] args) {
		String eventsFile1 = "../outputs/output_nash_wave/ITERS/it.150/150.events.txt.gz";
		String eventsFile2 = "../outputs/output_sysopt_wave/ITERS/it.150/150.events.txt.gz";
		String network = "../inputs/networks/padang_net_evac_v20080618.xml";
		
		NetworkLayer net = new NetworkLayer();
		new MatsimNetworkReader(net).readFile(network);
		
		CoordinateReferenceSystem crs = MGC.getCRS(TransformationFactory.WGS84_UTM47S);
		new RunCompare(eventsFile1, eventsFile2, crs, net).run();
	}

	
	private class TravelTimesFromEvents implements AgentDepartureEventHandler, AgentArrivalEventHandler, AgentStuckEventHandler {

		private final HashMap<String,AgentInfo> ttimes;
		private final QuadTree<AgentInfo> ttimesTree = new QuadTree<AgentInfo>(RunCompare.ENVELOPE.getMinX(),RunCompare.ENVELOPE.getMinY(),RunCompare.ENVELOPE.getMaxX(),RunCompare.ENVELOPE.getMaxY());

		public TravelTimesFromEvents() {
			this.ttimes = new HashMap<String,AgentInfo>();
		}
		
		public Collection<AgentInfo> getAgents(final Polygon p) {
			Collection<AgentInfo> ret = new ArrayList<AgentInfo>();
			Envelope e = p.getEnvelopeInternal();
			Rect bounds = new Rect(e.getMinX(),e.getMinY(),e.getMaxX(),e.getMaxY());
			this.ttimesTree.get(bounds, ret);
			return ret;
		}
		
		public void printAvgTT() {
			double ttSum = 0;
			for (AgentInfo ai : this.ttimes.values()) {
				ttSum += ai.time;
			}
			System.out.println("avg tt:" + ttSum/this.ttimes.size());
			
		}

		public void handleEvent(final AgentDepartureEvent event) {
			AgentInfo ai = new AgentInfo();
			ai.time = event.time;
			Link link = RunCompare.this.network.getLink(event.linkId);
			ai.c = new Coordinate(link.getCenter().getX(),link.getCenter().getY());
			this.ttimes.put(event.agentId, ai);
			this.ttimesTree.put(ai.c.x, ai.c.y, ai);
			
		}

		public void reset(final int iteration) {
			// TODO Auto-generated method stub
			
		}

		public void handleEvent(final AgentArrivalEvent event) {
			AgentInfo ai = this.ttimes.get(event.agentId);
			ai.time = event.time - ai.time;
			
		}

		public void handleEvent(final AgentStuckEvent event) {
			this.ttimes.remove(event.agentId);
			
		}
		
	}
	
	private static class AgentInfo {
		double time;
		Coordinate c;
	}
}

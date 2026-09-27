import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;


public class PassengerDistribution {
	private final Demand demand;
	private final PTN ptn;
	private final Distance distance;
	private final double od_network_acceleration;
	private final double waiting_time;
	private final double conversion_factor_length;
	private final double conversion_factor_coordinates;
	private final double ptn_speed;
	private final boolean use_network_distance;
	private final HashMap<DemandPoint, LinkedList<Passenger>> passengersByOrigin;


	public PassengerDistribution(Demand demand, PTN ptn, Distance distance, double od_network_acceleration,
	                             double waiting_time, double conversion_factor_length,
	                             double conversion_factor_coordinates, double ptn_speed,
	                             boolean use_network_distance) {
		this.demand = demand;
		this.ptn = ptn;
		this.distance = distance;
		this.od_network_acceleration = od_network_acceleration;
		this.waiting_time = waiting_time;
		this.conversion_factor_length = conversion_factor_length;
		this.conversion_factor_coordinates = conversion_factor_coordinates;
		this.ptn_speed = ptn_speed;
		this.use_network_distance = use_network_distance;
		passengersByOrigin = new HashMap<>();
		createPassengers();
	}

	public OD createOD() throws Exception {
		OD od_matrix = new OD();
		double intervalBound;

		//compute origin and destination stations for all passengers
		calcPassengerPaths();

		//Fill od_matrix
		for (Entry<DemandPoint, LinkedList<Passenger>> e : passengersByOrigin.entrySet()) {
			DemandPoint origin = e.getKey();
			LinkedList<Passenger> passengersFromOrigin = e.getValue();
			double[] random_numbers = new double[origin.getDemand()];
			// Create consecutive intervals for destinations with lengths proportional to demand/(traveling time)²
			intervalBound = 0;
			for (Passenger passenger : passengersFromOrigin) {
				intervalBound += (double) passenger.getDestinationDemandPoint().getDemand()
					/ Math.pow(passenger.getTravelingTime(), 2);
				passenger.setUpperBoundInterval(intervalBound);
			}
			// Draw random numbers in the union of the intervals of the destinations
			for (int index = 0; index < random_numbers.length; index++)
				random_numbers[index] = Math.random() * intervalBound;
			// For each commodity count how many random numbers lie in its interval
			Arrays.sort(random_numbers);
			int index = 0;
			for (Passenger passenger : passengersFromOrigin) {
				int weight = 0;
				// Uses that interval bounds are increasing in the order of passengersFromOrigin
				while(index < random_numbers.length && random_numbers[index] < passenger.getUpperBoundInterval()) {
					index++;
					weight++;
				}
				passenger.setWeight(weight);
				if (passenger.getOriginStop() != null) {
					od_matrix.addPassenger(passenger);
				}
			}
		}
		return od_matrix;
	}

	//private methods--------------------------------------------------------------------------------------------
	private void createPassengers() {
		for (DemandPoint origin : demand.getDemand_points()) {
			passengersByOrigin.put(origin, new LinkedList<>());
			for (DemandPoint destination : demand.getDemand_points()) {
				if (destination.getId() == origin.getId())
					continue;
				passengersByOrigin.get(origin).add(new Passenger(origin, destination));
			}
		}
	}

	private void calcPassengerPaths() throws Exception {

		//SP-Graph
		class SPNode {
			private final Stop stop;
			private final DemandPoint demand_point;

			SPNode(Stop stop) {
				this.stop = stop;
				demand_point = null;
			}

			SPNode(DemandPoint demand_point) {
				this.demand_point = demand_point;
				stop = null;
			}
		}

		class SPEdge {
			private final SPNode start;
			private final SPNode end;
			private final double weight;

			SPEdge(SPNode start, SPNode end, double weight) {
				this.start = start;
				this.end = end;
				this.weight = weight;
			}
		}

		HashMap<Stop, SPNode> stop_arrival = new HashMap<>();
		HashMap<Stop, SPNode> stop_departure = new HashMap<>();
		HashMap<DemandPoint, SPNode> demand_arrival = new HashMap<>();
		HashMap<DemandPoint, SPNode> demand_departure = new HashMap<>();
		LinkedList<SPEdge> edges = new LinkedList<>();
		SPNode arrival_sp;
		SPNode departure_sp;
		SPNode demand_point_arrival_sp;
		SPNode demand_point_departure_sp;
		double dist;
		double travelTime;
		TravelingTime tt_network = new TravelingTime(ptn_speed);
		TravelingTime tt_streets = new TravelingTime(ptn_speed / od_network_acceleration);
		
		//waiting_edges + split stops
		for (Stop stop : ptn.getStops()) {
			arrival_sp = new SPNode(stop);
			stop_arrival.put(stop, arrival_sp);
			departure_sp = new SPNode(stop);
			stop_departure.put(stop, departure_sp);
			edges.add(new SPEdge(arrival_sp, departure_sp, waiting_time));
		}

		//demand-points + connecting-edges
		for (DemandPoint demand_point : demand.getDemand_points()) {
			demand_point_arrival_sp = new SPNode(demand_point);
			demand_arrival.put(demand_point, demand_point_arrival_sp);
			demand_point_departure_sp = new SPNode(demand_point);
			demand_departure.put(demand_point, demand_point_departure_sp);
			//from demand-point to ptn
			for (SPNode stop : stop_departure.values()) {
				dist = distance.calcDist(stop.stop, demand_point) * conversion_factor_coordinates;
				travelTime = tt_streets.calcTimeInMinutes(dist);
				edges.add(new SPEdge(demand_point_departure_sp, stop, travelTime));
			}
			//from ptn to demand-point
			for (SPNode stop : stop_arrival.values()) {
				dist = distance.calcDist(stop.stop, demand_point) * conversion_factor_coordinates;
				travelTime = tt_streets.calcTimeInMinutes(dist);
				edges.add(new SPEdge(stop, demand_point_arrival_sp, travelTime));
			}
		}

		//edges connecting demand-points directly
		for (SPNode origin : demand_departure.values()) {
			for (SPNode destination : demand_arrival.values()) {
				if (origin.demand_point.getId() != destination.demand_point.getId()) {
					dist = distance.calcDist(origin.demand_point, destination.demand_point) *
							conversion_factor_coordinates;
					travelTime = tt_streets.calcTimeInMinutes(dist);
					edges.add(new SPEdge(origin, destination, travelTime));
				}
			}
		}

		//ptn-edges
		//length is calculated using TravelingTime
		for (Edge edge : ptn.getEdges()) {
			edges.add(new SPEdge(stop_departure.get(edge.getLeft_stop()),
					stop_arrival.get(edge.getRight_stop()),
					tt_network.calcTimeInMinutes(edge.getLength() * conversion_factor_length)));
			if (!ptn.isDirected()) {
				edges.add(new SPEdge(stop_departure.get(edge.getRight_stop()),
						stop_arrival.get(edge.getLeft_stop()),
						tt_network.calcTimeInMinutes(edge.getLength() * conversion_factor_length)));
			}
		}
		//construct shortest path graph
		ShortestPathsGraph<SPNode, SPEdge> spg = new ShortestPathsGraph<>();
		for (SPNode stop : stop_arrival.values())
			spg.addVertex(stop);
		for (SPNode stop : stop_departure.values())
			spg.addVertex(stop);
		for (SPNode demand_point : demand_arrival.values())
			spg.addVertex(demand_point);
		for (SPNode demand_point : demand_departure.values())
			spg.addVertex(demand_point);
		for (SPEdge edge : edges)
			spg.addEdge(edge, edge.start, edge.end, edge.weight);

		//compute shortest paths
		SPNode origin_sp;
		SPNode destination_sp;
		LinkedList<SPEdge> path;
		Stop origin_stop;
		Stop destination_stop;
		for (DemandPoint origin : passengersByOrigin.keySet()) {
			origin_sp = demand_departure.get(origin);
			spg.compute(origin_sp);

			for (Passenger passenger : passengersByOrigin.get(origin)) {
				destination_sp = demand_arrival.get(passenger.getDestinationDemandPoint());
				path = spg.trackPath(destination_sp);
				travelTime = spg.getDistance(destination_sp);
				origin_stop = path.getFirst().end.stop;
				destination_stop = path.getLast().start.stop;
				passenger.setOriginStop(origin_stop);
				passenger.setDestinaitonStop(destination_stop);
				if (!use_network_distance) {
					dist = distance.calcDist(origin, passenger.getDestinationDemandPoint());
					travelTime = tt_streets.calcTimeInMinutes(dist);
				}
				passenger.setTravelingTime(travelTime);
			}

		}
	}
}

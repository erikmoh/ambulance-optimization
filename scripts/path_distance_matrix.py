import json
import math
from os import getpid
import pandas as pd
import osmnx as ox
import networkx as nx
from pyrosm import OSM
from tqdm.auto import tqdm
from multiprocessing import Process, Pipe
from coordinate_converter import node_to_id, calc_dist


PLOT_GRAPH = False

PROCESSES = 16

FILENAME_OLD = None
FILENAME_NEW = "od_paths"

ox.settings.use_cache = True
UPDATE_GRAPH = False
UPDATE_CLOSEST_GRIDS = False

UPDATE_LOCATION_PERIOD = 5
STRICT_SIMPLIFYING = False
REMOVE_RINGS = True
HIGHWAY_SPEEDS = {
    'motorway': 80,
    'trunk': 70,
    'primary': 50,
    'secondary': 40,
    'tertiary': 40,
    'unclassified': 40,
    'residential': 30
    }


def init():
    if not UPDATE_GRAPH:
        return ox.load_graphml('data/osm/simplified.graphml', edge_dtypes={"oneway": str})

    # initialize the reader
    osm = OSM("data/osm/oslo_akershus_highways.osm.pbf")
    # read nodes and edges of the 'driving' network
    nodes, edges = osm.get_network(nodes=True, network_type="driving")
    # create NetworkX graph
    return osm.to_graph(nodes, edges, graph_type="networkx")


def assign_travel_time(G):
    # assign speeds to edges missing data. Impute if highway type is not in dict.
    G = ox.add_edge_speeds(G, HIGHWAY_SPEEDS)
    # calculate and add travel time to edges
    G = ox.add_edge_travel_times(G)
    return G


def simplify_graph(G):
    # remove geometry attribute from edges as simplify will create it
    for _, _, d in G.edges(data=True):
        d.pop('geometry', None)
    # remove unnecessary nodes
    G = ox.simplify_graph(G, STRICT_SIMPLIFYING, REMOVE_RINGS)
    return G


def load_coordinates():
    # get grid coordinates
    grids = pd.read_csv("data/grid_centroids.csv")
    grids = grids[["lat", "long"]]
    grids["type"] = "grid"
    # get base station coordinates
    base_stations = pd.read_csv("data/base_stations.csv")
    base_stations = base_stations[["latitude", "longitude"]]
    base_stations.columns = ("lat", "long")
    base_stations["type"] = "base_station"
    # get base station coordinates
    hospitals = pd.read_csv("data/hospitals.csv")
    hospitals = hospitals[["lat", "long"]]
    hospitals["type"] = "hospital"
    # combine coordinates
    combined = pd.concat([grids, base_stations, hospitals], ignore_index=True)
    ids = combined.index.values
    xs = combined["long"].values
    ys = combined["lat"].values
    return combined, ids, xs, ys, grids


def get_closest_grids(G, grids):
    if not UPDATE_CLOSEST_GRIDS:
        with open(f'data/nodes_closest_grids.json', 'r') as r:
            return json.load(r)

    closest_grids = {}
    for node in tqdm(G.nodes):
        closest = None
        minDist = math.inf
        # get node coordinates
        node_x = G.nodes[node]['x']
        node_y = G.nodes[node]['y']
        # loop all grids to find closest
        for coordinate in grids.itertuples():
            grid_id, grid_y, grid_x, _ = coordinate
            # calc distances
            dist = calc_dist(grid_x, grid_y, node_x, node_y)
            if dist < minDist:
                minDist = dist
                closest = grid_id
        # save grid id
        closest_grids[node] = closest

    # saves to file
    with open(f'data/nodes_closest_grids.json', 'w') as f:
        json.dump(closest_grids, f, indent=2)
    return closest_grids


def get_route_info(G, ssb_ids, route, update_period, closest_grids):
    travel_time = 0
    section_time = 0
    route_grids = []
    # iterate edges in route
    for u, v in zip(route[:-1], route[1:]):
        edge_time = G.edges[(u, v, 0)]['travel_time']
        travel_time += edge_time
        section_time += edge_time
        # save position every x min along route
        if section_time > update_period*60:
            section_time = 0
            closest = closest_grids[u]
            if closest:
                route_grids.append(ssb_ids[closest])
    
    travel_time = round(travel_time)
    return travel_time, route_grids


def shortest_paths(G, ids, from_ids, ssb_ids, nearest_nodes, closest_grids, conn):
    print(f"Finding shortest paths from id {from_ids[0]}-{from_ids[-1]} in process", getpid())
    od = {}
    for id1 in tqdm(from_ids):
        ssb_id1 = ssb_ids[id1]
        od[ssb_id1] = {}
        
        for id2 in ids:
            ssb_id2 = ssb_ids[id2]
            # set travel time to 0 if path is from and to the same node
            if id1 == id2:
                od[ssb_id1][ssb_id2] = {"travel_time": 0, "route": []}
                continue
            # if opposite direction has already been processed
            try:
                opposite = od[ssb_id2][ssb_id1]
                od[ssb_id1][ssb_id2] = {
                    "travel_time": opposite["travel_time"], 
                    "route": opposite["route"][::-1]
                    }
                continue
            except:
                pass

            # get the graph nodes
            source_node = nearest_nodes[id1]
            target_node = nearest_nodes[id2]

            # find shortest path (by travel time)
            route = nx.shortest_path(G, source_node, target_node, weight="travel_time")

            # get travel time and route grids
            travel_time, route_grids = get_route_info(G, ssb_ids, route, UPDATE_LOCATION_PERIOD, closest_grids)

            # remove destination from route if it was added
            if ssb_id2 in route_grids:
                route_grids.pop()

            od[ssb_id1][ssb_id2] = {"travel_time": travel_time, "route": route_grids}

    conn.send(od)


def main():
    print("Initializing reader and loading graph...")
    G = init()

    if UPDATE_GRAPH:
        print("Assigning speed and travel times for edges...")
        G = assign_travel_time(G)

        print("Simplifying graph...")
        G = simplify_graph(G)

        print("Saving graph...")
        ox.save_graphml(G, 'data/osm/simplified.graphml')

    if PLOT_GRAPH:
        print("Printing graph...")
        ox.plot_graph(G)

    print("Getting coordinates from csv...")
    combined, ids, xs, ys, grids = load_coordinates()

    print("Finding the coordinates' closest nodes...")
    nearest_nodes = ox.nearest_nodes(G, xs, ys, False)

    print("Finding the nodes' closest grids...")
    closest_grids = get_closest_grids(G, grids)

    print("Creating ssb ids from coordinates...")
    ssb_ids = [node_to_id(id, type, xs, ys) for id, _, _, type in combined.itertuples()]

    print(f"Creating {PROCESSES} processes...")
    processes = []
    connections = []
    split_size = math.ceil(len(ids)/PROCESSES)
    for split in range(PROCESSES):
        conn1, conn2 = Pipe()
        split_ids = ids[split_size*split:split_size*(split+1)]
        processes.append(Process(target=shortest_paths, args=(G, ids, split_ids, ssb_ids, nearest_nodes, closest_grids, conn2)))
        connections.append(conn1)

    print("Getting shortest paths from and to all coordinates...")
    for process in processes:
        process.start()

    od = {}
    if FILENAME_OLD:
        print("Loading od path matrix")
        with open(f'data/{FILENAME_OLD}', 'r') as r:
            od = json.load(r)

    print("Upserting new paths from processes to od matrix")
    for connection in connections:
        od1 = connection.recv()
        for k in od1.keys():
            item = od1[k]
            for k1 in item.keys():
                v = od1[k][k1]
                try:
                    od[k][k1] = v 
                except:
                    od[k] = item 
    
    print("Closing all processes...")
    for process in processes:
        process.join()
        
    print("Saving od to file...")
    with open(f'data/{FILENAME_NEW}.json', 'w') as f:
        json.dump(od, f, indent=2)
    print("Done.")


if __name__ == '__main__':
    main()

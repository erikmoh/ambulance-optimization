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


UPDATE_PERIOD = 5 # minutes

PLOT_GRAPH = False

PROCESSES = 1

FILENAME_OLD = "od_paths.json"
FILENAME_NEW = "od_paths_updated.json"

ox.settings.use_cache = True
UPDATE_GRAPH = False
UPDATE_CLOSEST_GRIDS = False

STRICT_SIMPLIFYING = False
REMOVE_RINGS = True
HIGHWAY_SPEEDS = {
    'motorway': 85,         # 93
    'motorway_link': 70,    # 75
    'trunk': 65,            # 67
    'trunk_link': 60,       # 66
    'primary': 55,          # 60
    'primary_link': 50,     # 65
    'secondary': 45,        # 57
    'secondary_link': 40,   # 60
    'tertiary': 40,         # 43
    'residential': 30,      # 33
    'unclassified': 30      # 40
    }


def init():
    if not UPDATE_GRAPH:
        G = ox.load_graphml('data/osm/simplified.graphml', edge_dtypes={"oneway": str}, node_dtypes={"osmid": float})
        return G

    # initialize the reader
    osm = OSM("data/osm/oslo_akershus_highways.osm.pbf")
    # read nodes and edges of the 'driving' network
    nodes, edges = osm.get_network(nodes=True, network_type="driving")
    # create NetworkX graph
    return osm.to_graph(nodes, edges, graph_type="networkx")


def assign_travel_time(G):
    # remove osm speed limits
    for _, _, d in G.edges(data=True):
        if d["highway"] in HIGHWAY_SPEEDS.keys() and "maxspeed" in d:
            d.pop("maxspeed")
    # assign speeds to edges missing data. Impute if highway type is not in dict.
    G = ox.add_edge_speeds(G, HIGHWAY_SPEEDS)
    # calculate and add travel time to edges
    G = ox.add_edge_travel_times(G)
    return G


def simplify_graph(G):
    print("Number of nodes and edges", len(G.nodes), len(G.edges))

    # remove geometry attribute from edges as simplify will create it
    for _, _, d in G.edges(data=True):
        d.pop('geometry', None)

    # remove unnecessary nodes
    G = ox.simplify_graph(G, STRICT_SIMPLIFYING, REMOVE_RINGS)
    print("Number of nodes and edges after simplifying", len(G.nodes), len(G.edges))

    # get a GeoSeries of consolidated intersections
    G_proj = ox.project_graph(G)
    # consolidate intersections and rebuild graph topology
    G2 = ox.consolidate_intersections(G_proj, rebuild_graph=True, tolerance=15, dead_ends=False)
    print("Number of nodes and edges after consolidating", len(G2.nodes), len(G2.edges))

    return ox.project_graph(G2, to_crs='epsg:4326')


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
        closest_grids[str(float(node))] = closest

    # saves to file
    with open(f'data/nodes_closest_grids.json', 'w') as f:
        json.dump(closest_grids, f, indent=2)
    return closest_grids


def get_route_info(G, ssb_ids, route, closest_grids):
    travel_time = 0
    section_time = 0
    route_grids = []
    # iterate edges in route
    for u, v in zip(route[:-1], route[1:]):
        edge = G.edges[(u, v, 0)]
        edge_time = edge["travel_time"]
        travel_time += edge_time
        section_time += edge_time
        # save position every x min along route
        while section_time >= UPDATE_PERIOD*60:
            closest_grid = ssb_ids[closest_grids[str(float(v))]]
            route_grids.append(closest_grid)
            section_time -= UPDATE_PERIOD*60
    
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

            #ox.plot_graph_route(G, route, route_linewidth=6, node_size=5, bgcolor='k')

            # get travel time and route grids
            travel_time, route_grids = get_route_info(G, ssb_ids, route, closest_grids)

            # remove destination from route if it was added
            if ssb_id2 in route_grids:
                route_grids.pop()

            od[ssb_id1][ssb_id2] = {"travel_time": travel_time, "route": route_grids}

    conn.send(od)


def main():
    print("Initializing reader and loading graph...")
    G = init()

    if UPDATE_GRAPH:
        print("Simplifying graph...")
        G = simplify_graph(G)

        print("Assigning speed and travel times for edges...")
        G = assign_travel_time(G)

        print("Saving graph...")
        ox.save_graphml(G, 'data/osm/simplified.graphml', gephi=False, encoding='utf-8')

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

    od = {"update_period_minutes": UPDATE_PERIOD}

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
    with open(f'data/{FILENAME_NEW}', 'w') as f:
        json.dump(od, f, indent=2)
    print("Done.")


if __name__ == '__main__':
    main()

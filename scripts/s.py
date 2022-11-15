import json

od1 = {
    "1": {
        "2": {
            "travel_time": 1
        }
    },
    "2": {
        "1": {
            "travel_time": 1
        }
    },
    "3": {
        "1": {
            "travel_time": 1
        },
        "2": {
            "travel_time": 1
        }
    },
    "4": {
        "1": {
        "travel_time": 1
        },
        "2": {
        "travel_time": 1
        }
    }
}

od2 = {
    "1": {
        "3": {
            "travel_time": 1
        },
        "4": {
            "travel_time": 1
        }
    },
    "2": {
        "3": {
            "travel_time": 1
        },
        "4": {
            "travel_time": 1
        }
    },
    "3": {
        "4": {
            "travel_time": 1
        }
    },
    "4": {
        "3": {
            "travel_time": 1
        }
    }
}

""" od1 = {
    "1": {
        "2": {
            "travel_time": 1
        },
        "3": {
            "travel_time": 1
        },
        "4": {
            "travel_time": 1
        }
    },
    "2": {
        "1": {
            "travel_time": 1
        },
        "3": {
            "travel_time": 1
        },
        "4": {
            "travel_time": 1
        }
    }
}

od2 = {-
    "3": {
        "1": {
            "travel_time": 1
        },
        "2": {
            "travel_time": 1
        },
        "4": {
            "travel_time": 1
        }
    },
    "4": {
        "1": {
            "travel_time": 1
        },
        "2": {
            "travel_time": 1
        },
        "3": {
            "travel_time": 1
        }
    }
} """

# appending the data
#od1.update(od2)

for k in od2.keys():
    item = od2[k]
    for k1 in item.keys():
        v = od2[k][k1]
        od1[k][k1] = v
 
# the result is a JSON string:
print(json.dumps(od1))
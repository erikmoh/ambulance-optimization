import numpy as np
import random
from deap import base, tools  # deap utilities


epsilon = 0.0001


def get_initial_weights_with_ga(base_model, train_x, train_y, population_size=10, generations=10, cxpb=0.65, mean=0.0, verbose=True, sigma=0.1, indpb=0.7, ga_data_size=500):
    # Get initial population and GA tools
    population = get_initial_population(base_model=base_model, size=population_size)
    toolbox = init_ga( mean=mean, sigma=sigma, indpb=indpb)

    # Create objects to contain population and their calculated fitness
    population_dict = {}
    fitness_dict = {}
    random_indices = []
    while (len(random_indices) < ga_data_size):
        number = random.randint(0,len(train_y))
        if number not in random_indices:
            random_indices.append(number)
    generation_x_values = []
    generation_y_values = []
    for i in random_indices:
        generation_x_values.append(train_x[i])
        generation_y_values.append(train_y[i])
    generation_x_values = np.array(generation_x_values)
    generation_y_values = np.array(generation_y_values)
    
     # Populate population and fitness objects
    for i in range(population_size):
        population_dict[i] = population[i]
        fitness_dict[i] = toolbox.evaluate((generation_x_values, generation_y_values, population[i], base_model))

    for generation in range(generations):

        # Find the chromosomes with the best fitness
        fitness_rank = sorted(fitness_dict.items(), key=lambda x: x[1])

        # Print values to get insight in how the algorithm is doing
        if verbose:
            print("Generation: {}".format(generation))
            top_ranks = 0
            for key, value in fitness_rank:
                print("Chromosome ID: {}, fitness {}". format(key, value))
                top_ranks += 1
                if top_ranks >3:
                    break

        # Create offspring
        offspring = toolbox.clone(population_dict)

        # Do crossover on offspring
        for i in range(len(fitness_rank)-1):
            if random.random() < cxpb:
                 tools.cxTwoPoint(offspring[fitness_rank[i][0]], offspring[fitness_rank[i+1][0]])
        
        # Mutate offspring, evaluate and select which to keep
        for i in range(population_size):
            toolbox.mutate(offspring[i])
            offspring_fitness = toolbox.evaluate((generation_x_values, generation_y_values, offspring[i], base_model))
            if offspring_fitness < fitness_dict[i]:
                population_dict[i] = offspring[i]
                fitness_dict[i] = offspring_fitness


    fitness_rank = sorted(fitness_dict.items(), key=lambda x: x[1])
    if verbose: 
        print("Best id and fitness: {}, {}".format(fitness_rank[0][0], fitness_rank[0][1]))

    # Sort population by fitness and return
    sorted_population = []
    for i in range(len(fitness_rank)):
        sorted_population.append(population_dict[fitness_rank[i][0]])
    return sorted_population

def evaluate_ind_mse(parameters):
    """
    Evaluate chromosome
    """

    number_of_time_slots_to_predict = 48

    train_x, train_y,individual, model = parameters

    new_weights = format_individual_to_model_weights(model, individual)
    model.set_weights(new_weights)
    predictions = model.predict(train_x)
    fitness_sum = np.sum(np.square(predictions.flatten() - np.array(train_y)))
    
    return fitness_sum

def evaluate_ind(parameters):
    """
    Evaluate chromosome
    """

    number_of_time_slots_to_predict = 48

    train_x, train_y,individual, model = parameters

    new_weights = format_individual_to_model_weights(model, individual)
    model.set_weights(new_weights)

    predictions = []
    fitness_sum = 0


    predictions = model.predict(train_x)
    
    for i in range(number_of_time_slots_to_predict):
        fitness_sum += predictions[i] - train_y[i]*(np.log(predictions[i]+epsilon))
    
    return fitness_sum

def format_individual_to_model_weights(model, individual):
    new_weights = []
    prev_range = 0
    for layer in model.layers:
        if len(layer.get_weights()) != 0:
            new_range = len(layer.get_weights()[0][0])*len(layer.get_weights()[0])
            new_weights.append(np.reshape(individual[prev_range:new_range+prev_range], ((len(layer.get_weights()[0])),len(layer.get_weights()[0][0]))))
            new_weights.append(layer.get_weights()[1])
            prev_range += new_range
    return new_weights


def init_ga(mean=0, sigma=0.1, indpb=0.8):

    #Create toolbox 
    toolbox = base.Toolbox()

    #Mutate using gaussian distribution
    toolbox.register("mutate", tools.mutGaussian,mu=mean, sigma=sigma, indpb=indpb)

    #Evaluation function
    toolbox.register("evaluate", evaluate_ind_mse)

    return toolbox

def get_initial_population(base_model, size = 20):
    """
    Create 20 random weights and return them in a list
    """
    population = []
    individual = []

    for layer in base_model.layers:
        if len(layer.get_weights()) != 0:
            individual.append(list(np.concatenate(layer.get_weights()[0]).flat))
    individual = np.concatenate(individual).flatten().tolist()

    for _ in range(size):
        population.append(individual)
        individual = np.random.permutation(individual)
    return population


def set_model_weights(population, model, n=0):
    new_weights = []
    prev_range = 0
    for layer in model.layers:
        if len(layer.get_weights()) != 0:
            new_range = len(layer.get_weights()[0][0])*len(layer.get_weights()[0])
            new_weights.append(np.reshape(population[n][prev_range:new_range+prev_range], ((len(layer.get_weights()[0])),len(layer.get_weights()[0][0]))))
            new_weights.append(layer.get_weights()[1])
            prev_range += new_range

    model.set_weights(new_weights)
    return model
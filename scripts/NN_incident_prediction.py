import numpy as np
import pandas as pd

from keras.models import Sequential
from keras.layers import Dense, Dropout
from keras.callbacks import EarlyStopping
from keras.optimizers import Adam
from keras.utils import to_categorical

from sklearn.metrics import accuracy_score
from sklearn.model_selection import KFold, train_test_split


DISTRIBUTION_FILE = "data/incidents_distribution_station_count.csv"
DISTRIBUTION_TEST_FILE = "data/incidents_distribution_station_count_test.csv"

CONFIGS = [
        {
            "layers": [128, 128, 64, 8],
            "dropout": 0.4,
            "lr": 0.001,
            "patience": 1,
            "epochs": 10,
            "seed": 42,
            "limit_features": False
        },
        {
            "layers": [128, 128, 64, 8],
            "dropout": 0.4,
            "lr": 0.001,
            "patience": 1,
            "epochs": 10,
            "seed": 42,
            "limit_features": True
        },
        {
            "layers": [128, 128, 64, 8],
            "dropout": 0.4,
            "lr": 0.001,
            "patience": 1,
            "epochs": 10,
            "seed": 32,
            "limit_features": False
        },
        {
            "layers": [128, 128, 64, 8],
            "dropout": 0.4,
            "lr": 0.001,
            "patience": 1,
            "epochs": 10,
            "seed": 32,
            "limit_features": True
        }
    ]



def cross_validation(x_train, y_train, nodes, epochs, dropout, patience):
    kf = KFold(n_splits=5)
    ce = []
    for train_index, validation_index in kf.split(x_train):
        model = get_model(nodes, dropout, len(x_train[0]))
        es = EarlyStopping(monitor='mean_squared_error', mode='min', verbose=1, patience=patience)
        history = model.fit(x_train[train_index], y_train[train_index], validation_data=(x_train[validation_index], y_train[validation_index]), epochs=epochs, verbose=1, callbacks=[es])
        ce.append(min(history.history['mean_squared_error']))
    avg_ce = sum(ce) / len(ce)
    return avg_ce



def get_model(nodes_in_hidden_layers, dropout, nr_of_inputs, lr):
    model = Sequential()
    for i in range(len(nodes_in_hidden_layers)):
        if i == 0:
            model.add(Dense(nodes_in_hidden_layers[i], activation='relu', input_shape=(nr_of_inputs,)))  # Input and hidden layer
        else:
            if dropout != 0:
                model.add(Dropout(dropout))
            model.add(Dense(nodes_in_hidden_layers[i], activation='relu'))  # Additional hidden layers
    model.add(Dense(4, activation="softmax"))  # Output layer
    model.compile(optimizer=Adam(learning_rate=lr), loss="categorical_crossentropy", metrics=("accuracy"))
    return model


def get_features(data, config):
    station = np.eye(19)[data["Base Station"]]
    year = np.eye(4)[data["Year"] - 2015]
    month = np.eye(12)[data["Month"] - 1]
    day = np.eye(31)[data["Day"] - 1]
    week = np.eye(53)[data["Week"] - 1]
    weekday = np.eye(7)[data["Weekday"] - 1]
    hour = np.eye(24)[data["Hour"]]
    daytime = np.zeros((len(data), 6))

    # Loop over the four-hour periods and sum the one hot encoded values of each hour in the period
    for i in range(6):
        daytime[:, i] = np.sum(hour[:, i*4:(i+1)*4], axis=1)

    x = np.concatenate([
        station,
        year,
        month,
        day,
        week,
        weekday,
        hour,
        daytime], axis=1)

    if config["limit_features"]:
        x = np.concatenate([
            station,
            year,
            month,
            day,
            week,
            weekday,
            #hour,
            daytime], axis=1)
    
    return x


def preprocess(data, config):
    #data = data[data["Base Station"] == 8]
    x = get_features(data, config)
    y = data["Incidents"].to_numpy()

    Y_class = np.zeros(y.shape)
    Y_class[y == 0] = 0  # Class 1
    Y_class[y == 1] = 1  # Class 2
    Y_class[(y > 1) & (y < 4)] = 2  # Class 3
    Y_class[y >= 4] = 3  # Class 4

    return x, Y_class


def main():
    data = pd.read_csv(DISTRIBUTION_FILE, encoding='utf-8', escapechar='\\', parse_dates=True)
    data_test = pd.read_csv(DISTRIBUTION_TEST_FILE, encoding='utf-8', escapechar='\\', parse_dates=True)

    for i, config in enumerate(CONFIGS):
        X_train, Y_train = preprocess(data, config)
        X_test, Y_test = preprocess(data_test, config)

        model = get_model(config["layers"], config["dropout"], len(X_train[0]), config["lr"])
        es = EarlyStopping(monitor='val_accuracy', mode='max', verbose=1, patience=config["patience"])

        x_train, x_test, y_train, y_test = train_test_split(X_train, Y_train, test_size=0.2, random_state=config["seed"])

        # Transform class labels to one-hot encoding
        y_train_one_hot = to_categorical(y_train)
        y_test_one_hot = to_categorical(y_test)

        model.fit(x_train, y_train_one_hot, epochs=config["epochs"], verbose=1, validation_data=(x_test, y_test_one_hot), callbacks=[es])

        predictions = model.predict(X_test)

        predictions_class = np.argmax(predictions, axis=1)

        score = accuracy_score(Y_test, predictions_class)
        print(i+1)
        print("Score: ", score)


main()

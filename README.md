# ambulance-optimization


### Structure
main:
- Experiments: For running custom simulation or optimization experiments 
- Optimization: Contains optimization classes like GA
- Simulation: Simulation classes
  - IO classes for loading incident and grid/route/distance data
  - Simulation: Main simulation class
  - SimulationApp: Runnable app with UI
  - SimulationController: JavaFx controller for app
- Parameters: All parameters that is used in simulation and optimization (all combinations are not possible)

scripts: 
- contains python scripts
  - processing datasets
  - predicting demand
  - graphs and figures
  - data analysis

output:
- simulation: saved simulation results (fitness, response time, allocation)
- visualization: graphs and figures


### Setup / Configurations
- mvn clean install
- pip install -r "Requirements.txt" (might require updated versions and conda might be useful)

Run configurations for SimulationApp: 
```txt
  --module-path C:\Users\erikm\javafx-sdk-19\lib --add-modules javafx.swing,javafx.graphics,javafx.fxml,javafx.media,javafx.web --add-reads javafx.graphics=ALL-UNNAMED --add-opens javafx.controls/com.sun.javafx.charts=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.iio=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.iio.common=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.css=ALL-UNNAMED --add-opens javafx.base/com.sun.javafx.runtime=ALL-UNNAMED -Xmx8192m
```

IntelliJ VM options 
- Xmx8192m is for increased memory (depends on your system)
- the rest is to fix problems with google-java-format
```txt
-Xmx8192m
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
```

### Notes
- Used IntelliJ for java, VSCode for scripts.
- "Old" folders contains work by previous master projects that is no longer used.


### Tips:

- Change breakpoint settings to only suspend thread and not all. That way you can use the javafx
application when stopped at a breakpoint during debugging.
- Recommend using a non-random initializer when debugging to get the same events in the simulation 
every time.


### Errors
- AllCityCenter currently causes an error in the Simulation. 
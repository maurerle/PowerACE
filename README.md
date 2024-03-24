# PowerACE

PowerACE is an agent-based electricity market model that encompasses various markets. The focus is on investment decisions in interconnected markets, while also simulating the annual and the day-ahead market at an hourly resolution. PowerACE enables scientists to examine the influence of various regulatory and legal frameworks on the European electricity market. PowerACE employs an exploratory approach by optimizing from the perspective of the actors, rather than from a system-wide perspective.

## Getting started

How to run PowerACE is mentioned in the [Wiki](https://gitlab.kit.edu/kit/iip/opensource/powerace/-/wikis/home) - [Getting started with PowerACE](https://gitlab.kit.edu/kit/iip/opensource/powerace/-/wikis/Getting-Started-with-PowerACE). 

## System Requirements

It is recommended to use PowerACE with [JDK 21.X](https://www.oracle.com/java/technologies/javase/jdk20-archive-downloads.html). Additionally, a current installation of [maven](https://maven.apache.org/download.cgi) is required. For solving optimization problems, a [Gurobi](https://www.gurobi.com/) installation is necessary. Detailed information can be found in the [Wiki](https://gitlab.kit.edu/kit/iip/opensource/powerace/-/wikis/home).

# Compilation

Having Maven installed, one can run `mvn compile assembly:single` to create the jar file.
This can then be run with `java -jar target/PowerACE-0.0.1-SNAPSHOT-jar-with-dependencies.jar`

## Using PowerACE

### Input Data
To effectively use PowerACE, various types of input data are necessary:
- powerplant date for non renewable powerplants 
- yearly timeseries for
    - renewable feed in
        - wind onshore
        - wind offshore
        - solar
        - hydro power
    - demand 
    - net transfer capacities usage for non-simulated but coupled market areas 
- net transfer capacities 

### Result data
PowerACE provides hourly market results for each simulated market area for every simulated year. These include detailed dispatch data for power plants and spot market prices. Additionally, annual investment decisions are logged, ensuring yearly information about the power plant fleet is available.


## Developers 

PowerACE was developed over several years at the [Institute for Industrial Production](https://www.iip.kit.edu/english/9.php), [Chair of Energy Economics](https://www.iip.kit.edu/english/Chair-of-Energy-Economics.php) at [Karlsruhe Institute of Technology](https://www.kit.edu/english/). During this time, various people have contributed to PowerACE. [Here is a detailed list of the developers](https://gitlab.kit.edu/kit/iip/opensource/powerace/-/blob/main/PowerACE-main/HallOfFame?ref_type=heads).

## Recommended citation
If you use PowerACE in your scientific work please cite:

Karlsruhe Institute of Technology (KIT), Institute of Industrial Production (IIP), Chair of Energy Economics (2023), PowerACE - An Agent-based Energy Market Model


## Acknowledgement

PowerACE has been developed over many years in various publicly funded research projects. The publication took place within the [VerSEAS-Project](https://www.iip.kit.edu/1064_5452.php). The developers thank the German Federal Ministry for Economic Affairs and Climate Protection for funding the VerSEAS project (founding number 03EI1018A). 

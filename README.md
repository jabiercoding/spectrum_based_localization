# spectrum_based_localization
* Import the plugins of this repository in the workspace. There are main methods to launch and get the results, but before that the next points are needed to satisfy all the needed dependencies.
* ArgoUMLSPL benchmark: Follow the setting up instructions from https://github.com/but4reuse/argouml-spl-benchmark . The ArgoUMLSPLBenchmark project must be in the workspace and the scenarios (or at least one) must be generated.
* BUT4Reuse: Follow the installation instructions and keep the plugins in the workspace: https://github.com/but4reuse/but4reuse/wiki/Installation
* ArgoUMLSPL benchmark BUT4Reuse helper: Clone and import the plugins in the workspace: https://github.com/but4reuse/argouml-spl-benchmark_but4reuse-helper 

Then, right click the Main methods, Run As... -> Java Application. Once finished press F5 in the project to refresh to see the output folder.

### Troubleshooting
* The "scenarios" folder in ArgoUMLSPL benchmark might have problems to refresh when all the scenarios are generated (too many files). You can filter this folder in the Project Explorer -> (icon with 3 vertical dots) -> Filters and Customization... -> User filters -> add "scenarios"

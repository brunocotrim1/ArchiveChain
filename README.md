ArchiveChain Project
Overview
ArchiveChain is a distributed storage system designed to run on a Linux subsystem. This README provides instructions for setting up and running the project, including dependencies and configuration.
Prerequisites

Linux subsystem (primary support)
Java Development Kit (JDK) 21 installed
Maven installed
Git installed
OpenSSL installed (for compiling the sloth library on non-Linux systems)
JAVA_HOME environment variable set to JDK 21

Setup Instructions
1. Clone the Repositories
Clone the main project and the utils repository:
git clone https://github.com/brunocotrim1/ArchiveChain
git clone https://github.com/brunocotrim1/ArchiveChainUtils

2. Build ArchiveChainUtils
Navigate to the ArchiveChainUtils directory and build the project:
cd ArchiveChainUtils
mvn clean install

3. Configure ArchiveChain
Navigate to the ArchiveChain project folder and set up the configuration file located at src/main/resources/application.yml. Update the following fields under the app section:

storagePath: Directory for node storage (e.g., ./nodes/${app.id}).
filesToPlotPath: Directory for files to plot (e.g., ./nodes/files).
farmerAddress: Your internet address (e.g., http://192.168.1.72).
seedNodes: List of nodes to connect to the network, including the local node and main seed node.
fccnPublicKey: The public key of the FCCN entity.
fccnNetworkAddress: The address of the FCCN entity.
dedicatedStorage: Amount of storage in bytes (e.g., 50,000,000 bytes).
initializeStorage: Set to true if plotting files for the first time, otherwise false.
initialVDFIterations: Number of initial VDF iterations (e.g., 1,000,000).

Example configuration (application.yml):
app:
  storagePath: ./nodes/${app.id}
  filesToPlotPath: ./nodes/files
  farmerAddress: "http://192.168.1.72"
  seedNodes:
    - "http://192.168.1.72:8080" # Local node
    - "http://192.168.1.72:8081" # Main seed node
  fccnPublicKey: "3059301306072a8648ce3d020106082a8648ce3d03010703420004e4a88d98f5ece99dae11bd4309307557e24876c2d389932c7b83f4c6012c41c803638b9aa38257f33d0a4137c9371fd59c13536ff807fa80261c8596d717d493"
  fccnNetworkAddress: "http://192.168.1.72:8085"
  dedicatedStorage: 50000000 # in bytes (~50MB)
  initializeStorage: true
  initialVDFIterations: 1000000

4. Run the Project
In the ArchiveChain project folder, run the Spring Boot application with the following command:
sudo mvn spring-boot:run -Dspring-boot.run.arguments="--app.id=0 --server.port=8080 --app.timelord=false"

Note: The app.timelord parameter is set to false as it is not necessary for every node to run a timelord.
Running on Non-Linux Systems
To run ArchiveChain on other operating systems (e.g., macOS), you need to compile the sloth library located at src/main/native. Use the following commands, adapted to your operating system:
Compile into Shared Library
g++ -std=c++17 -shared -o sloth.so -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/<os> sloth_wrapper.cpp sloth256_189.o -I/opt/homebrew/opt/openssl@3/include/ -L/opt/homebrew/opt/openssl@3/lib/ -lcrypto

Replace <os> with your operating system (e.g., darwin for macOS).
Compile Main
g++ -std=c++17 -o sloth_test sloth_wrapper.cpp -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/<os> sloth256_189.o -I/opt/homebrew/opt/openssl@3/include/ -L/opt/homebrew/opt/openssl@3/lib/ -lcrypto

Ensure OpenSSL is installed and adjust the include and library paths (-I and -L) according to your system’s OpenSSL installation.
Notes

The project requires Java 21. Ensure your JAVA_HOME points to JDK 21.
Ensure all dependencies are correctly installed and paths are properly configured.
The sloth library compilation is only required for non-Linux systems.
Verify the application.yml configuration, especially the seedNodes and farmerAddress, to ensure they are reachable.
The dedicatedStorage field is specified in bytes (e.g., 50,000,000 bytes is approximately 50MB).


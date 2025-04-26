In the Config file, seed nodes to connect to the network should be specified as well as FCCN publick key and address that will act as the entity we will distribute storage

When running the project Arguments should be specified, mainly the id and port to run and specify if it will run the timelord algoritmh:
Example: --app.id=1 --server.port=8081 --app.timelord=false
sudo mvn spring-boot:run -Dspring-boot.run.arguments="--app.id=1 --server.port=8081 --app.timelord=false"
sudo mvn spring-boot:run -Dspring-boot.run.arguments="--app.id=0 --server.port=8080 --app.timelord=true"